package com.flutcloud.flutlink.ui.format

import java.util.Locale

fun formatBytes(bytes: Long?): String {
    if (bytes == null) return ""
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024.0
        unit++
    }
    return String.format(Locale.ROOT, "%.1f %s", value, units[unit])
}

/** "1.2 GB of 5.0 GB (24%)" style quota label. */
fun formatQuota(used: Long?, total: Long?): String {
    if (used == null) return "Quota unknown"
    val usedText = formatBytes(used)
    return if (total == null || total <= 0) {
        "$usedText used"
    } else {
        val percent = (used.toDouble() / total.toDouble() * 100.0).toInt()
        "$usedText of ${formatBytes(total)} ($percent%)"
    }
}

/** Short date from RFC 1123 ("Thu, 13 Aug 2026 12:00:00 GMT"). */
fun formatMtime(mtime: String?): String {
    if (mtime.isNullOrBlank()) return ""
    return try {
        val parsed = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.parse(mtime)
        java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy").format(parsed)
    } catch (_: Exception) {
        mtime
    }
}