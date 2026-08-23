package com.flutcloud.flutlink.data

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.okhttp.OkHttpConfig
import okio.FileSystem

/** OkHttp powers HTTP on Android (connection pooling, HTTP/2, TLS). */
internal actual fun createPlatformClient(
    configure: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(OkHttp) {
    configure(this)
}

internal actual fun systemFileSystem(): FileSystem = FileSystem.SYSTEM

internal actual fun flutLog(tag: String, message: String) {
    Log.i(tag, message)
}

internal actual fun flutLogError(tag: String, message: String, error: Throwable) {
    Log.w(tag, message, error)
}
