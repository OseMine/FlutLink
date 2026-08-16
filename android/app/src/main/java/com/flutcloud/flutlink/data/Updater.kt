package com.flutcloud.flutlink.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

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

/** A newer FlutLink Android build that can be downloaded and installed. */
data class AppUpdate(val version: String, val apkUrl: String)

/**
 * Self-updater for the Android client: checks the FlutLink GitHub releases
 * for a newer version, downloads the release APK and hands it to the system
 * package installer. Releases are published by the CI workflow
 * (`.github/workflows/android.yml`).
 */
class Updater(
    private val client: OkHttpClient,
    private val context: Context,
    private val repo: String = "OseMine/FlutLink"
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Query the latest GitHub release. Returns an [AppUpdate] when a newer
     * version with an APK asset exists, null when the app is up to date or
     * no APK is published for the latest release.
     */
    suspend fun checkForUpdate(currentVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code != 200) return@withContext null
                val release = runCatching {
                    json.decodeFromString<GithubRelease>(response.body?.string().orEmpty())
                }.getOrNull() ?: return@withContext null
                val tag = release.tagName.removePrefix("v")
                if (compareVersions(tag, baseVersion(currentVersion)) <= 0) return@withContext null
                val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: return@withContext null
                AppUpdate(tag, apk.browserDownloadUrl)
            }
        } catch (e: IOException) {
            throw NetworkException(e)
        }
    }

    /** Download the release APK into the app cache dir. */
    suspend fun download(url: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "flutlink-update.apk")
        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
                response.body?.byteStream()?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IOException("Empty update body")
            }
        } catch (e: IOException) {
            throw NetworkException(e)
        }
        target
    }

    /** Hand the downloaded APK to the system package installer. */
    fun install(apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
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
