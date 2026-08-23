package com.flutcloud.flutlink.ui.format

import androidx.compose.runtime.Composable
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.stringResource
import com.flutcloud.flutlink.resources.Res
import com.flutcloud.flutlink.resources.quota_of
import com.flutcloud.flutlink.resources.quota_unknown
import com.flutcloud.flutlink.resources.quota_used


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
    return "${formatOneDecimal(value)} ${units[unit]}"
}

/** "1.2 GB of 5.0 GB (24%)" style quota label. */
@Composable
fun formatQuota(used: Long?, total: Long?): String {
    if (used == null) return stringResource(Res.string.quota_unknown)
    val usedText = formatBytes(used)
    return if (total == null || total <= 0) {
        stringResource(Res.string.quota_used, usedText)
    } else {
        val percent = (used.toDouble() / total.toDouble() * 100.0).toInt()
        stringResource(Res.string.quota_of, usedText, formatBytes(total), percent)
    }
}


/** "12.5" style one-decimal formatting without Locale-dependent APIs. */
internal fun formatOneDecimal(value: Double): String {
    val rounded = (value * 10).roundToLong()
    val whole = rounded / 10
    val frac = rounded % 10
    return if (frac == 0L) "$whole" else "$whole.$frac"
}
private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/** Short date from RFC 1123 ("Thu, 13 Aug 2026 12:00:00 GMT"). */
fun formatMtime(mtime: String?): String {
    if (mtime.isNullOrBlank()) return ""
    // "MMM d, yyyy" from the RFC 1123 fields; timezone is irrelevant for display.
    val parts = mtime.trim().split(Regex("\\s+"))
    return runCatching {
        val day = parts[1].toInt()
        val month = MONTHS.indexOfFirst { parts[2].startsWith(it) } + 1
        require(month > 0)
        "${MONTHS[month - 1]} $day, ${parts[3]}"
    }.getOrDefault(mtime)
}