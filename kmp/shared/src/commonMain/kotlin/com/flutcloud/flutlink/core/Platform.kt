package com.flutcloud.flutlink.core

import com.flutcloud.flutlink.data.PickedFile
import okio.Path

/**
 * Platform-backed services consumed by shared UI code. Each target provides
 * its own implementation and hands it to the [AppContainer] constructor —
 * no service-locator magic, plain dependency injection.
 */
interface Platform {

    /** Plain key/value storage for non-secret data (accounts, settings). */
    fun plainStorage(): KeyValueStorage

    /** Secure storage for account tokens (Keystore/Keychain backed). */
    fun secureStorage(): KeyValueStorage

    /** App-private directory for cached downloads (survives app updates). */
    fun appFilesDir(): Path

    /** Scratch directory for temp files (may be purged by the OS). */
    fun cacheDir(): Path

    /**
     * Persist a download into the user-visible downloads location
     * (MediaStore Downloads on Android 10+, public dir below, Files-app
     * Documents on iOS). Streams via the [write] callback so large files
     * never sit fully in memory.
     *
     * @return a human-readable path/location string for confirmations.
     */
    suspend fun saveToDownloads(fileName: String, write: suspend (Path) -> Unit): String

    /** Open a local file with an external viewer; false when unsupported. */
    fun openFile(path: Path): Boolean

    /** Share a local file via the system share sheet; false when unsupported. */
    fun shareFile(path: Path): Boolean

    /** Whether the self-updater row is shown in Settings (Android only). */
    val updatesEnabled: Boolean get() = false

    /**
     * Download an update package for installation. Only called when
     * [updatesEnabled] is true.
     */
    suspend fun downloadUpdate(url: String, destDir: Path): Path {
        throw UnsupportedOperationException("Updates are not supported on this platform")
    }

    /** Hand a downloaded update package to the system installer. */
    fun installUpdate(apk: Path) {
        throw UnsupportedOperationException("Updates are not supported on this platform")
    }

    /** Whether Material You dynamic color is available (Android 12+). */
    val supportsDynamicColor: Boolean get() = false

    /**
     * Whether the app must keep the device theme: the theme picker is hidden
     * in Settings and the OS dark/light setting always decides (Android).
     */
    val keepsDeviceTheme: Boolean get() = false

    /**
     * Prepare an uploaded file chosen by the platform picker for streaming
     * reads. Called once per upload attempt; implementations that copy the
     * picked content into cache should do it here and return a stable handle.
     * The default wraps the already-materialized [PickedFile] unchanged.
     */
    suspend fun materialize(picked: PickedFile): PickedFile = picked

    /** Human-readable platform name ("Android"/"iOS") for UI texts. */
    val name: String
}