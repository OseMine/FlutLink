package com.flutcloud.flutlink.data

import okio.Source

/**
 * A file picked through the platform document picker (SAF on Android,
 * UIDocumentPicker on iOS). The content can be read streaming via [open];
 * implementations may materialize it into cache first (iOS security-scoped
 * resources) — see [com.flutcloud.flutlink.core.Platform.materialize].
 */
class PickedFile(
    val displayName: String,
    val contentType: String,
    /** Reported size in bytes, null when the provider does not tell. */
    val size: Long?,
    private val openStream: () -> Source
) {
    fun open(): Source = openStream()
}
