package com.flutcloud.flutlink.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A downloadable APK in a GitHub release. */
@Serializable
data class GithubAsset(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String
)

/** The `latest` GitHub release payload (only the fields we need). */
@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("assets") val assets: List<GithubAsset> = emptyList()
)

/** A newer FlutLink build that can be downloaded and installed. */
data class AppUpdate(val version: String, val apkUrl: String)

/**
 * Checks the FlutLink GitHub releases for a newer version (platform-agnostic;
 * the download+install step stays platform-specific — APK sideload on
 * Android, unsupported on iOS). Releases are published by CI.
 */
class UpdateChecker(
    private val client: HttpClient,
    private val repo: String = "OseMine/FlutLink"
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Query the latest GitHub release. Returns an [AppUpdate] when a newer
     * version with an APK asset exists, null when the app is up to date or
     * no APK is published for the latest release.
     */
    suspend fun checkForUpdate(currentVersion: String): AppUpdate? {
        try {
            val response = client.get("https://api.github.com/repos/$repo/releases/latest") {
                headers.append("Accept", "application/vnd.github+json")
            }
            if (!response.status.isSuccess()) return null
            val release = runCatching {
                json.decodeFromString<GithubRelease>(response.bodyAsText())
            }.getOrNull() ?: return null
            val tag = release.tagName.removePrefix("v")
            if (compareVersions(tag, baseVersion(currentVersion)) <= 0) return null
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return null
            return AppUpdate(tag, apk.browserDownloadUrl)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw NetworkException(e)
        }
    }

    companion object {
        /** Normalize "1.0.0-debug" -> "1.0.0". */
        fun baseVersion(version: String): String =
            version.substringBefore('-').substringBefore('+')

        /** Compare dotted versions; "1.0.0" > "0.1.0". */
        fun compareVersions(a: String, b: String): Int {
            val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
            val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val x = pa.getOrElse(i) { 0 }
                val y = pb.getOrElse(i) { 0 }
                if (x != y) return x.compareTo(y)
            }
            return 0
        }
    }
}