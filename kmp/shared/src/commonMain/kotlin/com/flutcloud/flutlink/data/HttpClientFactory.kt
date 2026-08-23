package com.flutcloud.flutlink.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.UserAgent

/**
 * Shared Ktor client: no total timeout, bounded connect/socket timeouts and a
 * stable User-Agent — mirrors the desktop client's reqwest configuration. The
 * HTTP engine is platform-provided (OkHttp on Android/JVM, Darwin on iOS).
 */
object HttpClientFactory {
    fun create(userAgent: String): HttpClient =
        createPlatformClient {
            expectSuccess = false
            followRedirects = true
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                socketTimeoutMillis = SOCKET_TIMEOUT_MS
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            }
            install(UserAgent) {
                agent = userAgent
            }
        }

    private const val CONNECT_TIMEOUT_MS = 30_000L
    private const val SOCKET_TIMEOUT_MS = 120_000L
}

/**
 * Builds the platform [HttpClient] with the given common configuration.
 * Engine selection stays platform-specific (OkHttp on Android/JVM, Darwin
 * on iOS); keeping this an expect/actual over the whole client avoids
 * leaking engine-specific types into common code.
 */
internal expect fun createPlatformClient(
    configure: HttpClientConfig<*>.() -> Unit
): HttpClient

/** Basic-auth header value for the given credentials (RFC 7617). */
fun basicAuth(username: String, token: String): String =
    "Basic ${Base64Wide.encode("$username:$token".encodeToByteArray())}"

/** Minimal RFC 4648 base64 (kept dependency-free for all platforms). */
private object Base64Wide {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder(((bytes.size + 2) / 3) * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
            out.append(ALPHABET[b0 shr 2])
            out.append(ALPHABET[(b0 and 0x3) shl 4 or (b1 shr 4)])
            out.append(if (i + 1 < bytes.size) ALPHABET[(b1 and 0xF) shl 2 or (b2 shr 6)] else '=')
            out.append(if (i + 2 < bytes.size) ALPHABET[b2 and 0x3F] else '=')
            i += 3
        }
        return out.toString()
    }
}