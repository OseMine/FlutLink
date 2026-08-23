package com.flutcloud.flutlink.data

/**
 * Percent-encoding helpers for WebDAV/OCS URLs (RFC 3986). Replaces the
 * former java.net.URLEncoder usage so the API layer stays platform-free.
 */

/** Unreserved characters that survive a path segment unescaped. */
private fun isUnreserved(c: Char): Boolean =
    c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == '~'

/** Hex digits, upper case like java.net.URLEncoder output. */
private fun Char.hex(): String = "0123456789ABCDEF"[(code shr 4) and 0xF].toString() +
    "0123456789ABCDEF"[code and 0xF]

/** URL-encode a single path segment (space becomes %20, not +). */
fun encodeSegment(segment: String): String = buildString {
    for (c in segment) {
        if (isUnreserved(c)) append(c) else append('%').append(c.hex())
    }
}

/** Encode every segment of a logical path, e.g. `/My Folder/x` → `/My%20Folder/x`. */
fun encodePathSegments(path: String): String =
    path.split('/')
        .filter { it.isNotEmpty() }
        .joinToString("/") { encodeSegment(it) }

/** Percent-decode a string; malformed sequences are left intact. Literal `+` stays a plus. */
fun percentDecode(value: String): String {
    if ('%' !in value) return value
    val bytes = ByteArray(value.length)
    var out = 0
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '%' && i + 2 < value.length) {
            val hi = hexValue(value[i + 1])
            val lo = hexValue(value[i + 2])
            if (hi == null || lo == null) {
                bytes[out++] = c.code.toByte(); i++
            } else {
                bytes[out++] = ((hi shl 4) or lo).toByte(); i += 3
            }
        } else {
            bytes[out++] = c.code.toByte(); i++
        }
    }
    return bytes.decodeToString(0, out)
}

private fun hexValue(c: Char): Int? = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> null
}
