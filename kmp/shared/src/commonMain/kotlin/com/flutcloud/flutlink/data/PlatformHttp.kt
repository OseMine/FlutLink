package com.flutcloud.flutlink.data

/**
 * Platform logging sink for the network layer (Logcat on Android, stdout
 * elsewhere). Kept minimal so the API layer stays platform-free.
 */
internal expect fun flutLog(tag: String, message: String)

internal expect fun flutLogError(tag: String, message: String, error: Throwable)
