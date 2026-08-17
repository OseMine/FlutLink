package com.flutcloud.flutlink.data

import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/**
 * Opens the Android share sheet (`ACTION_SEND`) for a local file served via
 * FileProvider. Returns false when the file is missing or no app can handle
 * the share (callers then fall back to showing the path).
 */
object ShareSheet {

    fun share(context: Context, path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false

        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Log.w(TAG, "No FileProvider path for $path", e)
            return false
        }
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(Intent.createChooser(intent, null))
            true
        } catch (e: Exception) {
            Log.w(TAG, "No app can share $path", e)
            false
        }
    }

    private const val TAG = "FlutLinkShare"
}
