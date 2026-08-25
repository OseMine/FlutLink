package com.flutcloud.flutlink.data

import com.flutcloud.flutlink.data.dto.GuestEntry
import com.flutcloud.flutlink.data.dto.GuestShare
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Path
import okio.buffer

/**
 * Guest access to completely public shares as extensions on [FlutCloudApi].
 * All calls are anonymous (no account) against the fixed FlutCloud server;
 * the endpoints are strictly read-only on the server side. Mirrors the
 * desktop `src-tauri/src/guest.rs` surface.
 */

/** Feature announced by the FlutCloud app when guest access is available. */
internal const val GUEST_FEATURE = "complete-public-shares"

@Serializable
private data class GuestShareDto(
    val token: String? = null,
    val name: String? = null,
    val owner: String? = null,
    val ownerDisplay: String? = null,
    val category: String? = null,
    val url: String? = null,
    val downloadBase: String? = null,
    val mtime: Long? = null
)

@Serializable
private data class GuestEntryDto(
    val name: String? = null,
    val path: String? = null,
    val isDir: Boolean? = null,
    val size: Long? = null,
    val mtime: Long? = null,
    val contentType: String? = null
)

@Serializable
private data class GuestListingDto(
    val token: String? = null,
    val name: String? = null,
    val path: String? = null,
    val entries: List<GuestEntryDto> = emptyList()
)

private fun guestBaseUrl(baseUrl: String): String = baseUrl.trimEnd('/')

/**
 * Verify that the server supports guest access: anonymous ping probe plus a
 * check for the `complete-public-shares` feature. Keeps the FlutCloud-only
 * policy intact for guests.
 */
suspend fun FlutCloudApi.verifyGuestServer(baseUrl: String) {
    val url = "${guestBaseUrl(baseUrl)}/ocs/v2.php/apps/flutcloud/api/v1/ping"
    val data = executeAnonymous(url) ?: throw FlutCloudAppMissing()
    val features = data.jsonObject["features"]?.jsonArray
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .orEmpty()
    if (GUEST_FEATURE !in features) {
        // The app is installed but too old for guest access.
        throw FlutCloudAppMissing()
    }
}

/** Every completely public share in one bundled list. */
suspend fun FlutCloudApi.listGuestShares(baseUrl: String): List<GuestShare> {
    val data = executeAnonymous(
        "${guestBaseUrl(baseUrl)}/ocs/v2.php/apps/flutcloud/api/v1/public?format=json"
    ) ?: return emptyList()
    return data.jsonObject["shares"]?.jsonArray
        ?.mapNotNull { dto -> runCatching { json.decodeFromString<GuestShareDto>(dto.toString()) }.getOrNull() }
        ?.filter { it.token != null && it.name != null }
        ?.map { dto ->
            GuestShare(
                token = dto.token!!,
                name = dto.name!!,
                owner = dto.owner ?: "",
                ownerDisplay = dto.ownerDisplay ?: dto.owner,
                category = dto.category,
                url = dto.url ?: "",
                downloadBase = dto.downloadBase ?: "",
                mtime = dto.mtime
            )
        }
        .orEmpty()
}

/** Browse into a public share folder (`path` relative to the share root). */
suspend fun FlutCloudApi.listGuestEntries(
    baseUrl: String,
    token: String,
    path: String = "/"
): List<GuestEntry> {
    require(token.isNotBlank() && '/' !in token) { "Invalid share token" }
    require(".." !in path.split('/')) { "Path must not contain '..'" }
    val url = "${guestBaseUrl(baseUrl)}/ocs/v2.php/apps/flutcloud/api/v1/public/" +
        encodeSegment(token) + "?format=json&path=" + encodePathSegments(path)
    val data = executeAnonymous(url) ?: return emptyList()
    val listing = json.decodeFromString<GuestListingDto>(data.toString())
    return listing.entries
        .filter { it.name != null && it.path != null }
        .map { dto ->
            GuestEntry(
                name = dto.name!!,
                path = dto.path!!,
                isDir = dto.isDir ?: false,
                size = dto.size,
                mtime = dto.mtime,
                contentType = dto.contentType
            )
        }
}

/**
 * Stream a file from the share's anonymous WebDAV endpoint into `dest`
 * (basic auth with the token as username, mirroring the desktop
 * `guest::download_file`). Returns the written destination.
 */
suspend fun FlutCloudApi.downloadGuestFile(
    baseUrl: String,
    token: String,
    remotePath: String,
    dest: Path
): Path {
    require(token.isNotBlank() && '/' !in token) { "Invalid share token" }
    require(".." !in remotePath.split('/')) { "Path must not contain '..'" }
    val url = guestBaseUrl(baseUrl) + "/public.php/webdav/" +
        encodeSegment(token) + "/" + encodePathSegments(remotePath)
    return try {
        val response = client.request(url) {
            method = HttpMethod.Get
            header(io.ktor.http.HttpHeaders.Authorization, basicAuth(token, ""))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(
                "Download failed: HTTP ${response.status.value}",
                "http_${response.status.value}",
                response.status.value
            )
        }
        val fs = systemFileSystem()
        dest.parent?.let { parent -> fs.createDirectories(parent) }
        // Same streaming helper as the authenticated downloads (no progress:
        // guest transfers are short browse-and-save interactions).
        WebDavApi.copyWithProgress(response.bodyAsChannel(), fs.sink(dest).buffer(), 0L, null)
        dest
    } catch (e: ApiException) {
        throw e
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        throw NetworkException(e)
    }
}
