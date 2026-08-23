package com.flutcloud.flutlink.ui

import androidx.compose.runtime.Composable
import com.flutcloud.flutlink.core.IosPresenter
import com.flutcloud.flutlink.data.PickedFile
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject

private val fileManager = NSFileManager.defaultManager

/**
 * UIDocumentPickerViewController presented from the Compose host controller.
 * The `Import` mode hands us a copy of the file inside our own container, so
 * uploads can stream from the returned path without security-scope handling.
 */
@Composable
actual fun rememberFilePickLauncher(onPicked: (PickedFile?) -> Unit): () -> Unit {
    return {
        val top = IosPresenter.topViewController()
        if (top == null) {
            onPicked(null)
        } else {
            val picker = UIDocumentPickerViewController(
                documentTypes = listOf("public.data", "public.content"),
                mode = UIDocumentPickerMode.Import
            )
            picker.delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                override fun documentPicker(
                    controller: UIDocumentPickerViewController,
                    didPickDocumentsAtURLs: List<*>
                ) {
                    val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                    onPicked(url?.toPickedFile())
                }

                override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                    onPicked(null)
                }
            }
            top.presentViewController(picker, animated = true, completion = null)
        }
    }
}

/** iOS has no runtime permission prompt for saving into the app's Documents dir. */
@Composable
actual fun rememberDownloadsPermissionRequester(
    onResult: (granted: Boolean) -> Unit
): (() -> Unit)? = null

@kotlinx.cinterop.ExperimentalForeignApi
private fun NSURL.toPickedFile(): PickedFile {
    val localPath = path ?: "/"
    var size: Long? = null
    val attrs = fileManager.attributesOfItemAtPath(localPath, error = null)
    (attrs?.get(NSFileSize))?.let { value ->
        size = when (value) {
            is NSNumber -> value.longValue
            else -> value.toString().toLongOrNull()
        }
    }
    val contentType = pathExtension?.lowercase()?.let { mimeForExtension(it) }
        ?: "application/octet-stream"
    return PickedFile(
        displayName = lastPathComponent ?: "upload.bin",
        contentType = contentType,
        size = size,
        openStream = { FileSystem.SYSTEM.source(localPath.toPath()) }
    )
}

/** Minimal extension→MIME map; unknown types fall back to octet-stream upstream. */
private fun mimeForExtension(ext: String): String? = when (ext) {
    "jpg", "jpeg" -> "image/jpeg"
    "png", "gif", "webp" -> "image/$ext"
    "svg" -> "image/svg+xml"
    "pdf" -> "application/pdf"
    "txt", "md", "json", "xml", "kt", "rs", "ts", "vue", "html", "css" -> "text/plain"
    "zip" -> "application/zip"
    "7z" -> "application/x-7z-compressed"
    "rar" -> "application/vnd.rar"
    "gz" -> "application/gzip"
    "tar" -> "application/x-tar"
    "mp4" -> "video/mp4"
    "mov" -> "video/quicktime"
    "webm", "mkv" -> "video/webm"
    "mp3" -> "audio/mpeg"
    "wav" -> "audio/wav"
    "m4a" -> "audio/mp4"
    "ogg" -> "audio/ogg"
    "flac" -> "audio/flac"
    else -> null
}
