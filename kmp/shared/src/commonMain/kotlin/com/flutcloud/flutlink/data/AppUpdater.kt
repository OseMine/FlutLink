package com.flutcloud.flutlink.data

import com.flutcloud.flutlink.core.Platform
import okio.Buffer
import okio.Path
import okio.buffer

/**
 * Shared self-update flow for platforms that opt in via [Platform.updatesEnabled]
 * (Android): streams the release APK into the platform's updates cache,
 * verifies its SHA-256 against the GitHub release digest (CP-F4, mirroring
 * the desktop install scripts) and hands it to the system package installer.
 */
suspend fun Platform.downloadAndInstall(update: AppUpdate) {
    val apk = downloadUpdate(update.apkUrl, cacheDir().resolve("updates", normalize = false))
    update.sha256?.let { expected ->
        val actual = sha256OfFile(apk)
        if (!actual.equals(expected, ignoreCase = true)) {
            // Never hand a tampered/truncated package to the installer.
            runCatching { systemFileSystem().delete(apk) }
            throw ApiException(
                "SHA-256 mismatch for the downloaded update.",
                "update_checksum_mismatch"
            )
        }
    }
    installUpdate(apk)
}

/** Stream a file through [Sha256.Digester] so size is never memory-bound. */
private fun sha256OfFile(path: Path): String {
    systemFileSystem().source(path).buffer().use { src ->
        val digester = Sha256.Digester()
        val chunk = Buffer()
        while (src.read(chunk, 256L * 1024L) > 0) {
            digester.update(chunk.readByteArray())
        }
        return digester.hex()
    }
}
