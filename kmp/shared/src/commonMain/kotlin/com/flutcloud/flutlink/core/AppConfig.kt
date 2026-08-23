package com.flutcloud.flutlink.core

/** Compile-time app configuration injected by the platform entry point. */
data class AppConfig(
    /** FlutCloud server URL baked in at build time (empty = user chooses). */
    val defaultServerUrl: String,
    /** Human-readable app version for the settings screen and user agent. */
    val appVersion: String
)