package com.flutcloud.flutlink.ui

import androidx.compose.runtime.Composable
import com.flutcloud.flutlink.data.PickedFile

/**
 * Launcher for the platform document picker. Invoking the returned lambda
 * opens the picker; the [onPicked] callback receives the picked file or null
 * when the user cancelled.
 */
@Composable
expect fun rememberFilePickLauncher(onPicked: (PickedFile?) -> Unit): () -> Unit

/**
 * Permission gate for saving into the public Downloads folder (only relevant
 * for Android < 10). Returns null when no runtime permission is needed and
 * downloads may proceed directly; otherwise returns a lambda that triggers
 * the system dialog and reports the outcome via [onResult].
 */
@Composable
expect fun rememberDownloadsPermissionRequester(
    onResult: (granted: Boolean) -> Unit
): (() -> Unit)?