package com.flutcloud.flutlink.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flutcloud.flutlink.data.dto.Quota
import com.flutcloud.flutlink.data.dto.WebDavEntry
import com.flutcloud.flutlink.ui.format.formatBytes
import com.flutcloud.flutlink.ui.format.formatMtime
import com.flutcloud.flutlink.ui.format.formatQuota

/** Error banner shown inline for failed operations. */
@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Empty state with an optional hint. */
@Composable
fun EmptyState(icon: ImageVector, title: String, hint: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(48.dp)
        )
        Text(title, style = MaterialTheme.typography.titleMedium)
        hint?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Storage quota bar (M3 expressive: rounded, soft). */
@Composable
fun QuotaBar(quota: Quota?, modifier: Modifier = Modifier) {
    val used = quota?.used
    val total = quota?.total
    val fraction = if (used != null && total != null && total > 0) {
        (used.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        null
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                formatQuota(used, total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = if (fraction > 0.9f) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** Icon + tint for a WebDAV entry. */
@Composable
fun fileIcon(entry: WebDavEntry): Pair<ImageVector, Color> {
    if (entry.isDir) return Icons.Default.Folder to Color(0xFFE8C26A)
    return when {
        entry.name.endsWith(".pdf", ignoreCase = true) ->
            Icons.Default.PictureAsPdf to MaterialTheme.colorScheme.error
        entry.name.isImage() ->
            Icons.Default.Image to MaterialTheme.colorScheme.tertiary
        entry.name.isVideo() ->
            Icons.Default.Movie to MaterialTheme.colorScheme.tertiary
        entry.name.isAudio() ->
            Icons.Default.MusicNote to Color(0xFF9FE39F)
        entry.name.isArchive() ->
            Icons.Default.Archive to Color(0xFFE8C26A)
        entry.name.isCode() ->
            Icons.Default.Code to MaterialTheme.colorScheme.secondary
        else ->
            Icons.Default.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun String.isImage() = endsWith(".png", ignoreCase = true) ||
    endsWith(".jpg", ignoreCase = true) || endsWith(".jpeg", ignoreCase = true) ||
    endsWith(".gif", ignoreCase = true) || endsWith(".webp", ignoreCase = true) ||
    endsWith(".svg", ignoreCase = true)

private fun String.isVideo() = endsWith(".mp4", ignoreCase = true) ||
    endsWith(".mkv", ignoreCase = true) || endsWith(".webm", ignoreCase = true) ||
    endsWith(".mov", ignoreCase = true)

private fun String.isAudio() = endsWith(".mp3", ignoreCase = true) ||
    endsWith(".flac", ignoreCase = true) || endsWith(".wav", ignoreCase = true) ||
    endsWith(".ogg", ignoreCase = true) || endsWith(".m4a", ignoreCase = true)

private fun String.isArchive() = endsWith(".zip", ignoreCase = true) ||
    endsWith(".tar", ignoreCase = true) || endsWith(".gz", ignoreCase = true) ||
    endsWith(".7z", ignoreCase = true) || endsWith(".rar", ignoreCase = true)

private fun String.isCode() = endsWith(".txt", ignoreCase = true) ||
    endsWith(".md", ignoreCase = true) || endsWith(".json", ignoreCase = true) ||
    endsWith(".xml", ignoreCase = true) || endsWith(".kt", ignoreCase = true) ||
    endsWith(".rs", ignoreCase = true) || endsWith(".ts", ignoreCase = true) ||
    endsWith(".vue", ignoreCase = true) || endsWith(".html", ignoreCase = true) ||
    endsWith(".css", ignoreCase = true)

/** Small file size + mtime line used in list rows. */
@Composable
fun FileMetaLine(entry: WebDavEntry, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (!entry.isDir) {
            Text(
                formatBytes(entry.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val mtime = entry.mtime
        if (mtime != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                formatMtime(mtime),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Scrollable wrapper used by the login screen on small screens. */
@Composable
fun ScrollableColumn(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = horizontalAlignment
    ) {
        content()
    }
}

/** Section header for settings/admin screens. */
@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.1.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

// ---------------------------------------------------------------------------
// Desktop-style components matching src/style.css `.btn`, `.pill`, `.badge`,
// `.segment` classes. Cleaned up in KMP-F15: `FlutCard` (`.card`) and
// `FlutIconButton` (`.icon-btn`) were unused and removed; on the M3-Expressive
// revert (KMP-F14) the remaining ones here are replaced by M3 primitives
// (see kmp/README.md "Theme-Entscheidung").
// ---------------------------------------------------------------------------

/** Neutral pill badge with an optional colored status dot. Mirrors `.badge`. */
@Composable
fun FlutBadge(
    text: String,
    modifier: Modifier = Modifier,
    dotColor: Color? = null
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Micro-toggle pill for filter/view toggles. Mirrors `.pill` / `.pill-active`. */
@Composable
fun FlutPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.outline
    }
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(9999.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(9999.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = textColor
            )
        }
    }
}

/** Segmented control (e.g. list/grid toggle). Mirrors `.segment`. */
@Composable
fun FlutSegmentedControl(
    selectedIndex: Int,
    items: List<Pair<String, ImageVector>>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(28.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, (label, icon) ->
                val isSelected = selectedIndex == index
                val bgColor = if (isSelected) {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                } else {
                    Color.Transparent
                }
                val textColor = if (isSelected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(bgColor)
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = textColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (index < items.lastIndex) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
        }
    }
}

/** Ghost button matching desktop `.btn-ghost`. */
@Composable
fun FlutGhostButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

/** Outline button matching desktop `.btn-outline`. */
@Composable
fun FlutOutlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

/** Primary filled button matching desktop `.btn-primary`. */
@Composable
fun FlutPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

/** Breadcrumb segment for file navigation. */
@Composable
fun Breadcrumb(
    segments: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        segments.forEachIndexed { index, (label, onClick) ->
            val isLast = index == segments.lastIndex
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isLast) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isLast) FontWeight(600) else FontWeight.Normal,
                modifier = Modifier.clickable(enabled = !isLast, onClick = onClick)
            )
            if (!isLast) {
                Text(
                    text = "/",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Section label matching desktop label style (11px uppercase). */
@Composable
fun SectionLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}