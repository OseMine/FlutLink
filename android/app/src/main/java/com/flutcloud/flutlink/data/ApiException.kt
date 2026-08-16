package com.flutcloud.flutlink.data

/**
 * Error surfaced by the FlutCloud/WebDAV API layer. Mirrors the desktop
 * client's serialized `AppError { code, message }`.
 */
open class ApiException(
    override val message: String,
    val code: String = "api_error",
    val statusCode: Int = 0
) : Exception(message)

/** The server does not announce the `flutcloud` capability. */
class FlutCloudAppMissing : ApiException(
    "This server does not run the FlutCloud app.", "flutcloud_app_missing"
)

/** Network / HTTP-level failure (DNS, TLS, connection refused, ...). */
class NetworkException(cause: Throwable) : Exception(cause)