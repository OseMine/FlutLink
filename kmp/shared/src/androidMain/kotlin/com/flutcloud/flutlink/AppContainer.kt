package com.flutcloud.flutlink

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.MediaStore
import com.flutcloud.flutlink.core.AccountStore
import com.flutcloud.flutlink.core.AppConfig
import com.flutcloud.flutlink.core.EncryptedKeyValueStorage
import com.flutcloud.flutlink.core.SessionManager
import com.flutcloud.flutlink.core.SettingsStore
import com.flutcloud.flutlink.core.SharedPreferencesKeyValueStorage
import com.flutcloud.flutlink.data.FlutCloudApi
import com.flutcloud.flutlink.data.HttpClientFactory
import com.flutcloud.flutlink.data.ListCache
import com.flutcloud.flutlink.data.Updater
import com.flutcloud.flutlink.data.WebDavApi
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Implemented by the platform Application class so shared UI code can reach
 * the [container] without depending on the concrete Application type
 * (which lives in the :android-app module since the AGP 9 split).
 */
interface ContainerHost {
    val container: AppContainer
}

/** Simple service locator for the single-activity app. */
class AppContainer(
    context: Context,
    userAgent: String,
    val config: AppConfig
) {

    private val httpClient = HttpClientFactory.create(userAgent)

    private val appContext = context.applicationContext

    val settingsStore = SettingsStore(context)
    val accountStore = AccountStore(
        prefs = SharedPreferencesKeyValueStorage(
            appContext.getSharedPreferences(EncryptedKeyValueStorage.PREFS_NAME, Context.MODE_PRIVATE)
        ),
        securePrefs = EncryptedKeyValueStorage(appContext)
    )
    val sessionManager = SessionManager(accountStore)

    val ocsApi = FlutCloudApi(httpClient)
    val webDavApi = WebDavApi(httpClient)
    val listCache = ListCache(appContext)
    val updater = Updater(httpClient, appContext)

    /** App-specific files dir for downloads (survives app updates). */
    fun appFilesDir(): java.io.File = appContext.filesDir

    /** Query the size of a SAF content [Uri], null when the provider reports none. */
    fun contentSize(uri: Uri): Long? =
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) cursor.getLong(idx) else null
        }

    /** Open an InputStream for a SAF content [Uri] (streamed, never read fully). */
    fun openContentStream(uri: Uri): InputStream? =
        appContext.contentResolver.openInputStream(uri)

    /** Fallback for providers without a reported size: stream into a temp file. */
    fun streamToTempFile(uri: Uri, suffix: String = ".tmp"): java.io.File {
        val file = java.io.File.createTempFile("upload_", suffix, appContext.cacheDir)
        openContentStream(uri)?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output, bufferSize = 65536)
            }
        } ?: throw IOException("Cannot open $uri")
        return file
    }

    /**
     * Download a file into the public Downloads folder.
     * On Android 10+ uses MediaStore; on older versions writes to
     * `Environment.DIRECTORY_DOWNLOADS` directly.
     *
     * @param fileName   The desired file name (e.g. "photo.jpg").
     * @param download   A lambda that receives the destination [File] and
     *                   streams the content into it (e.g. via [WebDavApi.downloadToFile]).
     * @return The destination [File] that was written.
     */
    suspend fun downloadToDownloads(fileName: String, download: suspend (File) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: throw java.io.IOException("Failed to create MediaStore entry")
            try {
                // Download to a temp file first, then copy into the MediaStore output
                val tmp = File(appContext.cacheDir, "dl_$fileName")
                try {
                    download(tmp)
                    resolver.openOutputStream(uri)?.use { out ->
                        tmp.inputStream().use { input -> input.copyTo(out) }
                    } ?: throw java.io.IOException("Failed to open output stream")
                } finally {
                    tmp.delete()
                }
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val file = File(dir, fileName)
            download(file)
        }
    }
}
