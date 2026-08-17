package com.flutcloud.flutlink.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/**
 * Opens a local file with an external app via `ACTION_VIEW` + FileProvider or
 * MediaStore content URI. Returns false when the file is missing or no app
 * can handle it (callers then fall back to showing the path).
 */
object FileOpener {

    fun open(context: Context, path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false

        val uri = if (isInDownloadsFolder(file)) {
            queryMediaStoreUri(context, file.name)
                ?: FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(Intent.createChooser(intent, "Open with"))
            true
        } catch (e: Exception) {
            Log.w(TAG, "No app can open $path", e)
            false
        }
    }

    private fun isInDownloadsFolder(file: File): Boolean =
        file.absolutePath.contains(Environment.DIRECTORY_DOWNLOADS)

    private fun queryMediaStoreUri(context: Context, fileName: String): android.net.Uri? {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(fileName),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                return ContentUris.withAppendedId(collection, id)
            }
        }
        return null
    }

    private const val TAG = "FlutLinkOpen"
}