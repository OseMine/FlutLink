package com.flutcloud.flutlink.data

import java.io.File
import java.io.InputStream
import okio.Path.Companion.toOkioPath
import okio.source

/**
 * Android bridges for the common [WebDavApi]: keep the historical
 * `java.io.File` / `InputStream` surface for the Android UI while the core
 * operates on okio types.
 */

/** Download into a [File]; streaming, never read fully into the heap. */
suspend fun WebDavApi.downloadToFile(
    session: AuthSession,
    path: String,
    dest: File,
    onProgress: ProgressCallback? = null,
    targetUser: String? = null
): File {
    downloadToFile(session, path, dest.toOkioPath(), onProgress = onProgress, targetUser = targetUser)
    return dest
}

/** Upload from an [InputStream] (e.g. a SAF content URI) without buffering the whole file. */
suspend fun WebDavApi.uploadStream(
    session: AuthSession,
    path: String,
    openStream: () -> InputStream,
    contentLength: Long,
    contentType: String = "application/octet-stream",
    mtimeEpochSeconds: Long? = null,
    onProgress: ProgressCallback? = null,
    targetUser: String? = null
) = uploadStream(
    session, path,
    openStream = { openStream().source() },
    contentLength = contentLength,
    contentTypeValue = contentType,
    mtimeEpochSeconds = mtimeEpochSeconds,
    onProgress = onProgress,
    targetUser = targetUser
)
