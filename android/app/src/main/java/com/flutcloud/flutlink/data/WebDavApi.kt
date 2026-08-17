package com.flutcloud.flutlink.data

import android.util.Log
import com.flutcloud.flutlink.data.dto.WebDavEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.net.URLDecoder

/** Reports `(transferred, total)` bytes during a streaming transfer. */
fun interface ProgressCallback {
    fun onProgress(transferred: Long, total: Long)
}

/**
 * WebDAV client for the FlutCloud file API (PROPFIND, SEARCH, PUT, GET,
 * MKCOL, DELETE, MOVE). Mirrors the desktop `nextcloud/webdav.rs`.
 */
class WebDavApi(private val client: OkHttpClient) {

    private fun logI(msg: String) = Log.i(TAG, msg)

    private fun davRoot(session: AuthSession): String =
        "${session.normalizedBaseUrl}/remote.php/dav/files/${encodeSegment(session.username)}"

    private fun davUrl(session: AuthSession, path: String): String {
        val root = davRoot(session)
        return if (path.isEmpty() || path == "/") root else "$root/${encodePathSegments(path)}"
    }

    private fun auth(request: Request.Builder, session: AuthSession): Request.Builder =
        request.header("Authorization", Credentials.basic(session.username, session.token))

    private fun executeWebDav(request: Request, basePath: String): List<WebDavEntry> {
        val started = System.currentTimeMillis()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code >= 400 && response.code != 207) {
                    throw ApiException(
                        "Server answered ${response.code}: $body".trim(),
                        "http_${response.code}",
                        response.code
                    )
                }
                val entries = parseMultistatus(body, basePath)
                Log.i(TAG, "${request.method} ${request.url} -> ${response.code} in ${System.currentTimeMillis() - started}ms body=${body.length} entries=${entries.size}")
                return entries
            }
        } catch (e: ApiException) {
            Log.w(TAG, "${request.method} ${request.url} -> ApiException ${e.message}", e)
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "${request.method} ${request.url} -> IOException ${e.message}", e)
            throw NetworkException(e)
        }
    }

    /** List a folder (PROPFIND, Depth 1). */
    suspend fun list(session: AuthSession, path: String): List<WebDavEntry> = withContext(Dispatchers.IO) {
        val request = auth(
            Request.Builder().url(davUrl(session, path)).method("PROPFIND", null),
            session
        ).header("Depth", "1").build()
        executeWebDav(request, "/remote.php/dav/files/${encodeSegment(session.username)}")
            .filterNot { it.path == listingCurrentPath(path) }
    }

    /** WebDAV-SEARCH across the whole files tree. */
    suspend fun search(session: AuthSession, query: String): List<WebDavEntry> = withContext(Dispatchers.IO) {
        val body = searchRequestBody(session.username, query)
        val request = auth(
            Request.Builder()
                .url("${session.normalizedBaseUrl}/remote.php/dav/")
                .method("SEARCH", body.toRequestBody("application/xml".toMediaType())),
            session
        ).header("Depth", "0").build()
        executeWebDav(request, "/remote.php/dav/files/${encodeSegment(session.username)}")
    }

    /** PROPFIND (Depth 0): does the remote resource exist? */
    suspend fun exists(session: AuthSession, path: String): Boolean = withContext(Dispatchers.IO) {
        val request = auth(
            Request.Builder().url(davUrl(session, path)).method("PROPFIND", null),
            session
        ).header("Depth", "0").build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code in 200..299 || response.code == 207 -> true
                    response.code == 404 -> false
                    else -> throw ApiException("Server answered ${response.code}.", "http_${response.code}", response.code)
                }
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw NetworkException(e)
        }
    }

    /** Upload bytes via PUT. Sends X-OC-MTime so the stored mtime stays stable. */
    suspend fun upload(
        session: AuthSession,
        path: String,
        bytes: ByteArray,
        contentType: String = "application/octet-stream",
        mtimeEpochSeconds: Long? = null
    ) = withContext(Dispatchers.IO) {
        val builder = auth(
            Request.Builder().url(davUrl(session, path)),
            session
        ).put(bytes.toRequestBody(contentType.toMediaType()))
        mtimeEpochSeconds?.let { builder.header("X-OC-MTime", it.toString()) }
        statusCheck(builder.build())
    }

    /**
     * Download a remote file streaming it directly into `dest` (OkHttp
     * `byteStream()` -> `FileOutputStream`), so large files never sit in the
     * heap as a `ByteArray`.
     */
    suspend fun downloadToFile(
        session: AuthSession,
        path: String,
        dest: File,
        onProgress: ProgressCallback? = null
    ): File = withContext(Dispatchers.IO) {
        val request = auth(Request.Builder().url(davUrl(session, path)).get(), session).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ApiException("Download failed: HTTP ${response.code}", "http_${response.code}", response.code)
                }
                val body = response.body ?: throw ApiException("Empty download")
                val total = body.contentLength().coerceAtLeast(0L)
                dest.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(STREAM_BUFFER_BYTES)
                        var transferred = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            transferred += read
                            onProgress?.onProgress(transferred, total)
                        }
                    }
                }
                dest
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw NetworkException(e)
        }
    }

    /**
     * Upload a file by streaming `openStream` (e.g. a SAF content URI) into the
     * request body, so the file never has to be read into the heap as a whole.
     * `contentLength` must be known (query the provider); for providers without
     * a size use [upload] instead.
     */
    suspend fun uploadStream(
        session: AuthSession,
        path: String,
        openStream: () -> InputStream,
        contentLength: Long,
        contentType: String = "application/octet-stream",
        mtimeEpochSeconds: Long? = null,
        onProgress: ProgressCallback? = null
    ) = withContext(Dispatchers.IO) {
        val body = object : RequestBody() {
            override fun contentType() = contentType.toMediaType()
            override fun contentLength(): Long = contentLength
            override fun writeTo(sink: BufferedSink) {
                val input = openStream() ?: throw IOException("Cannot open upload stream")
                input.use {
                    val buffer = ByteArray(STREAM_BUFFER_BYTES)
                    var transferred = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                        transferred += read
                        onProgress?.onProgress(transferred, contentLength)
                    }
                }
            }
        }
        val builder = auth(
            Request.Builder().url(davUrl(session, path)),
            session
        ).put(body)
        mtimeEpochSeconds?.let { builder.header("X-OC-MTime", it.toString()) }
        statusCheck(builder.build())
    }

    /** Create a folder (MKCOL). 405 = already exists, treated as success. */
    suspend fun mkdir(session: AuthSession, path: String) = withContext(Dispatchers.IO) {
        val request = auth(
            Request.Builder().url(davUrl(session, path)).method("MKCOL", null),
            session
        ).build()
        statusCheck(request, ignoreStatus = 405)
    }

    /** Delete a file/folder. 404 = already gone, treated as success. */
    suspend fun delete(session: AuthSession, path: String) = withContext(Dispatchers.IO) {
        val request = auth(
            Request.Builder().url(davUrl(session, path)).method("DELETE", null),
            session
        ).build()
        statusCheck(request, ignoreStatus = 404)
    }

    /**
     * Rename/move a resource (MOVE with Overwrite: F). Throws a
     * `target_exists` ApiException when the destination already exists.
     */
    suspend fun rename(session: AuthSession, path: String, newPath: String) = withContext(Dispatchers.IO) {
        val request = auth(
            Request.Builder()
                .url(davUrl(session, path))
                .method("MOVE", null)
                .header("Destination", davUrl(session, newPath))
                .header("Overwrite", "F"),
            session
        ).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 412) {
                    throw ApiException("Destination already exists: $newPath", "target_exists", 412)
                }
                if (!response.isSuccessful && response.code != 404) {
                    throw ApiException("Rename failed: HTTP ${response.code}", "http_${response.code}", response.code)
                }
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw NetworkException(e)
        }
    }

    /** Fetch a preview thumbnail; null when the server has none. */
    suspend fun preview(session: AuthSession, path: String, size: Int = 256): ByteArray? = withContext(Dispatchers.IO) {
        val url = "${session.normalizedBaseUrl}/index.php/core/preview.png?file=${encodeSegment(path)}&x=$size&y=$size"
        val request = auth(Request.Builder().url(url).get(), session).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 404 || response.code == 400) return@withContext null
                if (!response.isSuccessful) {
                    throw ApiException("Preview failed: HTTP ${response.code}", "http_${response.code}", response.code)
                }
                response.body?.bytes()
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw NetworkException(e)
        }
    }

    private fun statusCheck(request: Request, ignoreStatus: Int? = null) {
        try {
            client.newCall(request).execute().use { response ->
                if (response.code == ignoreStatus) return
                if (!response.isSuccessful) {
                    throw ApiException(
                        "Server answered ${response.code}: ${response.body?.string().orEmpty()}".trim(),
                        "http_${response.code}",
                        response.code
                    )
                }
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
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

        /** Percent-decode a segment, keeping literal `+` intact. */
        private fun urlDecode(segment: String): String =
            try {
                URLDecoder.decode(segment.replace("+", "%2B"), "UTF-8")
            } catch (_: IllegalArgumentException) {
                segment
            }

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
            return "/" + trimmed.split('/').joinToString("/") { urlDecode(it) }
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
            val name = urlDecode(rel.substringAfterLast('/'))
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
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(StringReader(body))
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

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
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
                    XmlPullParser.TEXT -> if (field != null) text.append(parser.text)
                    XmlPullParser.END_TAG -> {
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
    }
}