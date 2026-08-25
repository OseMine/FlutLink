package com.flutcloud.flutlink.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.flutcloud.flutlink.data.FileOpener
import com.flutcloud.flutlink.data.NetworkException
import com.flutcloud.flutlink.data.ShareSheet
import com.flutcloud.flutlink.data.createPlatformClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toOkioPath

/**
 * Android implementation of [Platform]: SharedPreferences/Encrypted-
 * SharedPreferences storage, MediaStore Downloads, FileProvider open/share
 * and APK self-updates.
 */
class AndroidPlatform(context: Context) : Platform {

    private val appContext = context.applicationContext

    private val updateClient = createPlatformClient { }

    override val name: String get() = "Android"

    override fun plainStorage(): KeyValueStorage =
        SharedPreferencesKeyValueStorage(
            appContext.getSharedPreferences(EncryptedKeyValueStorage.PREFS_NAME, Context.MODE_PRIVATE)
        )

    override fun secureStorage(): KeyValueStorage = EncryptedKeyValueStorage(appContext)

    override fun appFilesDir(): Path {
        appContext.filesDir.mkdirs()
        return appContext.filesDir.toOkioPath()
    }

    override fun cacheDir(): Path {
        appContext.cacheDir.mkdirs()
        return appContext.cacheDir.toOkioPath()
    }

    /**
     * Download into the public Downloads folder. On Android 10+ the content is
     * written to a cache temp file first and then copied into a MediaStore
     * entry; on older versions it is written to the public downloads dir
     * directly (the WRITE_EXTERNAL_STORAGE gate lives in the UI layer).
     *
     * @return a human-readable location for confirmations.
     */
    override suspend fun saveToDownloads(fileName: String, write: suspend (Path) -> Unit): String =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = appContext.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(collection, values)
                    ?: throw IOException("Failed to create MediaStore entry")
                try {
                    // Download to a temp file first, then copy into the MediaStore output.
                    val tmp = File(appContext.cacheDir, "dl_$fileName")
                    try {
                        write(tmp.toOkioPath())
                        resolver.openOutputStream(uri)?.use { out ->
                            tmp.inputStream().use { input -> input.copyTo(out) }
                        } ?: throw IOException("Failed to open output stream")
                    } finally {
                        tmp.delete()
                    }
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
                "${Environment.DIRECTORY_DOWNLOADS}/$fileName"
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val file = File(dir, fileName)
                write(file.toOkioPath())
                file.absolutePath
            }
        }

    /** Open a local file with an external app (ACTION_VIEW). */
    override fun openFile(path: Path): Boolean =
        FileOpener.open(appContext, path.toString())

    /** Share a local file via ACTION_SEND + FileProvider. */
    override fun shareFile(path: Path): Boolean =
        ShareSheet.share(appContext, path.toString())

    override val updatesEnabled: Boolean get() = true

    override val supportsDynamicColor: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Stream the update APK into the given directory. */
    override suspend fun downloadUpdate(url: String, destDir: Path): Path {
        return try {
            withContext(Dispatchers.IO) {
                val target = destDir.resolve("flutlink-update.apk")
                val response = updateClient.get(url)
                if (!response.status.isSuccess()) {
                    throw IOException("Download failed: HTTP ${response.status.value}")
                }
                // Create the destination directory right before writing: a
                // missing parent (or a stale non-directory "updates" entry)
                // used to surface only as a bare ENOENT from the output stream.
                val dir = File(target.toString()).parentFile
                if (dir == null || !(dir.isDirectory || dir.mkdirs())) {
                    throw IOException("Update cache directory unavailable: $dir")
                }
                val out = File(target.toString())
                try {
                    out.outputStream().use { output ->
                        val channel = response.bodyAsChannel()
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = channel.readAvailable(buffer, 0, buffer.size)
                            if (read == -1) break
                            if (read > 0) output.write(buffer, 0, read)
                        }
                    }
                } catch (e: Exception) {
                    // Never leave a truncated package behind for the installer.
                    out.delete()
                    throw e
                }
                target
            }
        } catch (e: IOException) {
            throw NetworkException(e)
        }
    }

    /** Hand the downloaded APK to the system package installer. */
    override fun installUpdate(apk: Path) {
        val file = File(apk.toString())
        if (!file.isFile) {
            throw IOException("Downloaded update package is missing: $file")
        }
        val uri: Uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }
}

