package com.flutcloud.flutlink.data

import com.flutcloud.flutlink.core.Platform

/**
 * Shared self-update flow for platforms that opt in via [Platform.updatesEnabled]
 * (Android): streams the release APK into the platform's updates cache and
 * hands it to the system package installer.
 */
suspend fun Platform.downloadAndInstall(update: AppUpdate) {
    val apk = downloadUpdate(update.apkUrl, cacheDir().resolve("updates", normalize = false))
    installUpdate(apk)
}
