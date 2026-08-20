package com.flutcloud.flutlink.data

/** Active FlutCloud connection: base URL + basic-auth credentials. */
data class AuthSession(
    val baseUrl: String,
    val username: String,
    val token: String
) {
    /** Normalized base URL without trailing slash. */
    val normalizedBaseUrl: String get() = baseUrl.trimEnd('/')
}