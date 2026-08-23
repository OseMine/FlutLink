package com.flutcloud.flutlink.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

/** OkHttp powers HTTP on the desktop JVM target. */
internal actual fun createPlatformClient(
    configure: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(OkHttp) {
    configure(this)
}

internal actual fun flutLog(tag: String, message: String) {
    println("INFO [$tag] $message")
}

internal actual fun flutLogError(tag: String, message: String, error: Throwable) {
    System.err.println("WARN [$tag] $message (${error.message})")
}
