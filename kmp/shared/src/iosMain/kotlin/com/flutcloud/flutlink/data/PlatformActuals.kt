package com.flutcloud.flutlink.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import okio.FileSystem

/** Darwin (NSURLSession) powers HTTP on iOS. */
internal actual fun createPlatformClient(
    configure: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(Darwin) {
    configure(this)
}

internal actual fun systemFileSystem(): FileSystem = FileSystem.SYSTEM

internal actual fun flutLog(tag: String, message: String) {
    println("INFO [$tag] $message")
}

internal actual fun flutLogError(tag: String, message: String, error: Throwable) {
    println("WARN [$tag] $message (${error.message})")
}
