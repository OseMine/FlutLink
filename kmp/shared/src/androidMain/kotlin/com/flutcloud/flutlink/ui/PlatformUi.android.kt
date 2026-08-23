package com.flutcloud.flutlink.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.flutcloud.flutlink.data.PickedFile
import okio.source

/** SAF document picker (OpenDocument) streaming into a [PickedFile]. */
@Composable
actual fun rememberFilePickLauncher(onPicked: (PickedFile?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        onPicked(uri?.let { context.toPickedFile(it) })
    }
    return { launcher.launch(arrayOf("*/*")) }
}

private fun Context.toPickedFile(uri: Uri): PickedFile {
    var name: String? = null
    var size: Long? = null
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0) name = cursor.getString(nameIdx)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
        }
    }
    val mime = contentResolver.getType(uri) ?: "application/octet-stream"
    return PickedFile(
        displayName = name ?: "upload.bin",
        contentType = mime,
        size = size,
        openStream = {
            contentResolver.openInputStream(uri)?.source()
                ?: throw IllegalStateException("Cannot open $uri")
        }
    )
}

/**
 * WRITE_EXTERNAL_STORAGE is only required for public Downloads writes below
 * Android 10; everywhere else downloads proceed without a prompt.
 */
@Composable
actual fun rememberDownloadsPermissionRequester(
    onResult: (granted: Boolean) -> Unit
): (() -> Unit)? {
    val context = LocalContext.current
    val granted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    if (granted) return null

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onResult(it) }
    return { launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
}
