package com.flutcloud.flutlink.data

import okhttp3.OkHttpClient
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Shared OkHttp client: no total timeout, bounded connect/read like the desktop. */
object HttpClientFactory {
    fun create(userAgent: String): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
}

/** URL-encode a single path segment, keeping separators intact. */
fun encodeSegment(segment: String): String =
    URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

/** Encode every path segment of a logical path, e.g. `/My Folder/x` → `/My%20Folder/x`. */
fun encodePathSegments(path: String): String =
    path.split('/')
        .filter { it.isNotEmpty() }
        .joinToString("/") { encodeSegment(it) }