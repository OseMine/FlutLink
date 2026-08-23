package com.flutcloud.flutlink.data

import com.flutcloud.flutlink.data.dto.WebDavEntry
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Source
import okio.buffer
import okio.use

/** Reports `(transferred, total)` bytes during a streaming transfer. */
fun interface ProgressCallback {
    fun onProgress(transferred: Long, total: Long)
}

/**
 * WebDAV client for the FlutCloud file API (PROPFIND, SEARCH, PUT, GET,
 * MKCOL, DELETE, MOVE). Mirrors the desktop `nextcloud/webdav.rs`. Runs on
 * every KMP target: HTTP via Ktor, streaming file IO via okio, multistatus
 * parsing via [MiniXmlParser].
 */
class WebDavApi(private val client: HttpClient) {

    private fun logI(msg: String) = flutLog(TAG, msg)

    /** The user whose files namespace a request addresses (admin impersonation). */
    private fun effectiveUser(session: AuthSession, targetUser: String?): String =
        targetUser ?: session.username

    private fun davRoot(session: AuthSession, targetUser: String? = null): String =
        "${session.normalizedBaseUrl}/remote.php/dav/files/${encodeSegment(effectiveUser(session, targetUser))}"

    private fun davUrl(session: AuthSession, path: String, targetUser: String? = null): String {
        val root = davRoot(session, targetUser)
        return if (path.isEmpty() || path == "/") root else "$root/${encodePathSegments(path)}"
    }

    private fun HttpRequestBuilder.auth(session: AuthSession) {
        header(HttpHeaders.Authorization, basicAuth(session.username, session.token))
    }

    /**
     * Attach the `Impersonate-User` header so the server resolves the request
     * in another user's namespace. Requires admin credentials; only set when
     * [targetUser] differs from the signed-in user (mirrors the desktop
     * `webdav::list`/`request_as`).
     */
    private fun HttpRequestBuilder.impersonate(session: AuthSession, targetUser: String?) {
        if (targetUser != null && targetUser != session.username) {
            header("Impersonate-User", targetUser)
        }
    }

    /** Nextcloud requires this marker on OCS and DAV endpoints to avoid 401/997. */
    private fun HttpRequestBuilder.ocsMarker(url: String) {
        if (url.contains("/ocs/") || url.contains("/remote.php/dav/")) {
            header("OCS-APIRequest", "true")
        }
    }

    private suspend fun executeWebDav(
        url: String,
        basePath: String,
        block: HttpRequestBuilder.() -> Unit
    ): List<WebDavEntry> {
        val started = TimeSource.Monotonic.markNow()
        try {
            val response = client.request(url) {
                block()
                ocsMarker(url)
            }
            val body = response.bodyAsText()
            if (response.status.value >= 400 && response.status.value != 207) {
                throw ApiException(
                    "Server answered ${response.status.value}: $body".trim(),
                    "http_${response.status.value}",
                    response.status.value
                )
            }
            val entries = parseMultistatus(body, basePath)
            logI("$url -> ${response.status.value} in ${started.elapsedNow().inWholeMilliseconds}ms body=${body.length} entries=${entries.size}")
            return entries
        } catch (e: ApiException) {
            flutLogError(TAG, "$url -> ApiException ${e.message}", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            flutLogError(TAG, "$url -> IOException ${e.message}", e)
            throw NetworkException(e)
        }
    }

    /** List a folder (PROPFIND, Depth 1). */
    suspend fun list(session: AuthSession, path: String, targetUser: String? = null): List<WebDavEntry> {
        val effective = effectiveUser(session, targetUser)
        val entries = executeWebDav(davUrl(session, path, targetUser), "/remote.php/dav/files/${encodeSegment(effective)}") {
            method = HttpMethod("PROPFIND")
            auth(session)
            impersonate(session, targetUser)
            header("Depth", "1")
        }
        return entries.filterNot { it.path == listingCurrentPath(path) }
    }

    /** WebDAV-SEARCH across the whole files tree. */
    suspend fun search(session: AuthSession, query: String, targetUser: String? = null): List<WebDavEntry> {
        val effective = effectiveUser(session, targetUser)
        val body = searchRequestBody(effective, query)
        return executeWebDav("${session.normalizedBaseUrl}/remote.php/dav/", "/remote.php/dav/files/${encodeSegment(effective)}") {
            method = HttpMethod("SEARCH")
            auth(session)
            impersonate(session, targetUser)
            header("Depth", "0")
            contentType(ContentType.Application.Xml)
            setBody(body)
        }
    }

    /** PROPFIND (Depth 0): does the remote resource exist? */
    suspend fun exists(session: AuthSession, path: String, targetUser: String? = null): Boolean =
        try {
            val response = client.request(davUrl(session, path, targetUser)) {
                method = HttpMethod("PROPFIND")
                auth(session)
                impersonate(session, targetUser)
                header("Depth", "0")
                ocsMarker(davUrl(session, path, targetUser))
            }
            when (response.status.value) {
                in 200..299 -> true
                207 -> true
                404 -> false
                else -> throw ApiException(
                    "Server answered ${response.status.value}.",
                    "http_${response.status.value}",
                    response.status.value
                )
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException(e)
        }

    /** Upload bytes via PUT. Sends X-OC-MTime so the stored mtime stays stable. */
    suspend fun upload(
        session: AuthSession,
        path: String,
        bytes: ByteArray,
        contentType: String = "application/octet-stream",
        mtimeEpochSeconds: Long? = null,
        targetUser: String? = null
    ) = statusCheck(davUrl(session, path, targetUser), session, targetUser) {
        method = HttpMethod.Put
        contentType(ContentType.parse(contentType))
        setBody(bytes)
        mtimeEpochSeconds?.let { header("X-OC-MTime", it.toString()) }
    }

    /**
     * Download a remote file streaming it directly into `dest` on [fs], so
     * large files never sit in memory as a byte array.
     */
    suspend fun downloadToFile(
        session: AuthSession,
        path: String,
        dest: Path,
        fs: FileSystem = systemFileSystem(),
        onProgress: ProgressCallback? = null,
        targetUser: String? = null
    ): Path {
        try {
            val response = client.request(davUrl(session, path, targetUser)) {
                method = HttpMethod.Get
                auth(session)
                impersonate(session, targetUser)
                ocsMarker(davUrl(session, path, targetUser))
            }
            if (!response.status.isSuccess()) {
                throw ApiException(
                    "Download failed: HTTP ${response.status.value}",
                    "http_${response.status.value}",
                    response.status.value
                )
            }
            val total = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            dest.parent?.let { parent -> fs.createDirectories(parent) }
            copyWithProgress(response.bodyAsChannel(), fs.sink(dest).buffer(), total, onProgress)
            return dest
        } catch (e: ApiException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException(e)
        }
    }

    /**
     * Upload a file by streaming `openStream` into the request body, so the
     * source never has to be read into memory as a whole. `contentLength`
     * must be known; for unknown sizes use [upload] instead.
     */
    suspend fun uploadStream(
        session: AuthSession,
        path: String,
        openStream: () -> Source,
        contentLength: Long,
        contentTypeValue: String = "application/octet-stream",
        mtimeEpochSeconds: Long? = null,
        onProgress: ProgressCallback? = null,
        targetUser: String? = null
    ) = statusCheck(davUrl(session, path, targetUser), session, targetUser) {
        method = HttpMethod.Put
        contentType(ContentType.parse(contentTypeValue))
        setBody(streamingContent(openStream, contentLength, contentTypeValue, onProgress))
        mtimeEpochSeconds?.let { header("X-OC-MTime", it.toString()) }
    }

    /** Create a folder (MKCOL). 405 = already exists, treated as success. */
    suspend fun mkdir(session: AuthSession, path: String, targetUser: String? = null) =
        statusCheck(davUrl(session, path, targetUser), session, targetUser, ignoreStatus = 405) {
            method = HttpMethod("MKCOL")
        }

    /** Delete a file/folder. 404 = already gone, treated as success. */
    suspend fun delete(session: AuthSession, path: String, targetUser: String? = null) =
        statusCheck(davUrl(session, path, targetUser), session, targetUser, ignoreStatus = 404) {
            method = HttpMethod.Delete
        }

    /**
     * Rename/move a resource (MOVE with Overwrite: F). Throws a
     * `target_exists` ApiException when the destination already exists.
     */
    suspend fun rename(
        session: AuthSession,
        path: String,
        newPath: String,
        targetUser: String? = null
    ) {
        try {
            val response = client.request(davUrl(session, path, targetUser)) {
                method = HttpMethod("MOVE")
                auth(session)
                impersonate(session, targetUser)
                header("Destination", davUrl(session, newPath, targetUser))
                header("Overwrite", "F")
                ocsMarker(davUrl(session, path, targetUser))
            }
            if (response.status.value == 412) {
                throw ApiException("Destination already exists: $newPath", "target_exists", 412)
            }
            if (!response.status.isSuccess() && response.status.value != 404) {
                throw ApiException(
                    "Rename failed: HTTP ${response.status.value}",
                    "http_${response.status.value}",
                    response.status.value
                )
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException(e)
        }
    }

    private suspend fun statusCheck(
        url: String,
        session: AuthSession,
        targetUser: String?,
        ignoreStatus: Int? = null,
        block: HttpRequestBuilder.() -> Unit
    ) {
        try {
            val response = client.request(url) {
                block()
                auth(session)
                impersonate(session, targetUser)
                ocsMarker(url)
            }
            if (response.status.value == ignoreStatus) return
            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                throw ApiException(
                    "Server answered ${response.status.value}: $body".trim(),
                    "http_${response.status.value}",
                    response.status.value
                )
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException(e)
        }
    }

    companion object {
        private const val TAG = "FlutLinkDav"

        /** Chunk size for streaming transfers (download + upload). */
        private const val STREAM_BUFFER_BYTES = 64 * 1024

        private fun searchRequestBody(user: String, query: String): String =
            """<?xml version="1.0" encoding="UTF-8"?>
<d:searchrequest xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
  <d:basicsearch>
    <d:select>
      <d:prop>
        <d:displayname/>
        <d:getcontentlength/>
        <d:getlastmodified/>
        <d:getetag/>
        <d:getcontenttype/>
      </d:prop>
    </d:select>
    <d:from>
      <d:scope>
        <d:href>/files/${escapeXml(user)}</d:href>
        <d:depth>infinity</d:depth>
      </d:scope>
    </d:from>
    <d:where>
      <d:eq>
        <d:prop><d:displayname/></d:prop>
        <d:literal>${escapeXml(query)}</d:literal>
      </d:eq>
    </d:where>
    <d:orderby/>
  </d:basicsearch>
</d:searchrequest>
"""

        private fun escapeXml(text: String): String =
            text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        /**
         * Normalize a client-supplied folder path into the logical relative
         * path used in listings ("" → "/", "/Photos" stays as is, trailing
         * slashes stripped). The Depth-1 response of a folder always contains
         * the folder itself, which must not appear as an entry inside its own
         * listing — otherwise clicking it is a no-op.
         */
        private fun listingCurrentPath(path: String): String {
            val trimmed = path.trim('/')
            return if (trimmed.isEmpty()) "/" else "/$trimmed"
        }

        /** Strip scheme + host from an absolute href. */
        private fun hrefPath(href: String): String {
            val idx = href.indexOf("://")
            if (idx < 0) return href
            val after = href.substring(idx + 3)
            val slash = after.indexOf('/')
            return if (slash >= 0) after.substring(slash) else "/"
        }

        /** Index right after the first `basePath` occurrence on a path boundary. */
        private fun findBasePath(path: String, basePath: String): Int? {
            var start = 0
            while (true) {
                val idx = path.indexOf(basePath, start)
                if (idx < 0) return null
                val end = idx + basePath.length
                if (end >= path.length || path[end] == '/') return end
                start = idx + 1
            }
        }

        private fun relativePath(href: String, basePath: String): String {
            val path = hrefPath(href)
            val after = findBasePath(path, basePath)?.let { path.substring(it) } ?: path
            val trimmed = after.trim('/')
            if (trimmed.isEmpty()) return "/"
            return "/" + trimmed.split('/').joinToString("/") { percentDecode(it) }
        }

        /** Flag entries under `resources` (read-only) or `parts` (write-enabled). */
        private fun classify(rel: String): Pair<Boolean, Boolean> {
            var isResource = false
            var isPart = false
            for (segment in rel.split('/')) {
                when (segment.lowercase()) {
                    "resources" -> isResource = true
                    "parts" -> isPart = true
                }
            }
            return isResource to isPart
        }

        /** Resolve a virtual link to its counterpart (`resources/<n>` ↔ `parts/<n>`). */
        private fun resolveLinkTarget(rel: String): String? {
            val segments = rel.trim('/').split('/')
            if (segments.size < 2) return null
            val target = when (segments[0].lowercase()) {
                "resources" -> "parts"
                "parts" -> "resources"
                else -> return null
            }
            return "/" + (listOf(target) + segments.drop(1)).joinToString("/")
        }

        /** Counterpart path in the paired namespace. */
        private fun pairedPath(rel: String): String? {
            val segments = rel.split('/')
            for (i in segments.indices) {
                val paired = when (segments[i].lowercase()) {
                    "resources" -> "parts"
                    "parts" -> "resources"
                    else -> continue
                }
                val copy = segments.toMutableList()
                copy[i] = paired
                return copy.joinToString("/")
            }
            return null
        }

        private fun toEntry(
            href: String,
            basePath: String,
            isDir: Boolean,
            size: Long?,
            mtime: String?,
            etag: String?,
            contentType: String?
        ): WebDavEntry? {
            val rel = relativePath(href, basePath)
            if (rel == "/" || rel.isEmpty()) return null
            val name = percentDecode(rel.substringAfterLast('/'))
            if (name.isEmpty()) return null
            val (isResource, isPart) = classify(rel)
            return WebDavEntry(
                name = name,
                path = rel,
                isDir = isDir,
                size = size,
                mtime = mtime,
                etag = etag,
                contentType = contentType,
                isResource = isResource,
                isPart = isPart,
                linkTarget = resolveLinkTarget(rel),
                pairedPath = pairedPath(rel)
            )
        }

        /** Parse a WebDAV multistatus document into structured entries. */
        fun parseMultistatus(body: String, basePath: String): List<WebDavEntry> {
            val parser = MiniXmlParser(body)
            val entries = mutableListOf<WebDavEntry>()

            var href: String? = null
            var isDir = false
            var inResourceType = false
            var field: String? = null
            val text = StringBuilder()
            var size: Long? = null
            var mtime: String? = null
            var etag: String? = null
            var contentType: String? = null

            var event = parser.next()
            while (event != MiniXmlParser.END_DOCUMENT) {
                when (event) {
                    MiniXmlParser.START_TAG -> {
                        when (parser.name) {
                            "response" -> {
                                href = null; isDir = false; size = null
                                mtime = null; etag = null; contentType = null
                            }
                            "href" -> field = "href"
                            "resourcetype" -> inResourceType = true
                            "collection" -> if (inResourceType) isDir = true
                            "getcontentlength" -> field = "size"
                            "getlastmodified" -> field = "mtime"
                            "getetag" -> field = "etag"
                            "getcontenttype" -> field = "contenttype"
                        }
                        text.setLength(0)
                    }
                    MiniXmlParser.TEXT -> if (field != null) text.append(parser.text)
                    MiniXmlParser.END_TAG -> {
                        if (field != null) {
                            val value = text.toString().trim()
                            when (field) {
                                "href" -> href = value
                                "size" -> size = value.toLongOrNull()
                                "mtime" -> mtime = value
                                "etag" -> etag = value
                                "contenttype" -> contentType = value
                            }
                            field = null
                        }
                        when (parser.name) {
                            "resourcetype" -> inResourceType = false
                            "response" -> {
                                href?.let {
                                    toEntry(it, basePath, isDir, size, mtime, etag, contentType)
                                        ?.let { e -> entries.add(e) }
                                }
                            }
                        }
                    }
                }
                event = parser.next()
            }
            return entries
        }

        /** Stream a [ByteReadChannel] into an okio sink, reporting progress. */
        internal suspend fun copyWithProgress(
            channel: ByteReadChannel,
            sink: okio.BufferedSink,
            total: Long,
            onProgress: ProgressCallback?
        ) {
            sink.use { out ->
                val buffer = ByteArray(STREAM_BUFFER_BYTES)
                var transferred = 0L
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read == -1) break
                    if (read > 0) {
                        out.write(buffer, 0, read)
                        transferred += read
                        onProgress?.onProgress(transferred, total)
                    }
                }
            }
        }

        internal fun streamingContent(
            openStream: () -> Source,
            contentLength: Long,
            contentTypeValue: String,
            onProgress: ProgressCallback?
        ) = object : io.ktor.http.content.OutgoingContent.WriteChannelContent() {
            override val contentType: ContentType = ContentType.parse(contentTypeValue)
            override val contentLength: Long = contentLength

            override suspend fun writeTo(channel: io.ktor.utils.io.ByteWriteChannel) {
                openStream().use { src ->
                    val input = src.buffer()
                    val buffer = ByteArray(STREAM_BUFFER_BYTES)
                    var transferred = 0L
                    while (true) {
                        val read = input.read(buffer, 0, buffer.size)
                        if (read == -1) break
                        channel.writeFully(buffer, 0, read)
                        transferred += read
                        onProgress?.onProgress(transferred, contentLength)
                    }
                }
            }
        }
    }
}