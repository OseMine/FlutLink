package com.flutcloud.flutlink.ui

import androidx.compose.runtime.Composable
import com.flutcloud.flutlink.data.PickedFile

/**
 * Headless Desktop-JVM stubs: the shared UI is only rendered on Android and
 * iOS; the JVM target compiles commonMain for validation and the CLI.
 */
@Composable
actual fun rememberFilePickLauncher(onPicked: (PickedFile?) -> Unit): () -> Unit {
    return { throw UnsupportedOperationException("File picking requires Android or iOS") }
}

@Composable
actual fun rememberDownloadsPermissionRequester(
    onResult: (granted: Boolean) -> Unit
): (() -> Unit)? = null
