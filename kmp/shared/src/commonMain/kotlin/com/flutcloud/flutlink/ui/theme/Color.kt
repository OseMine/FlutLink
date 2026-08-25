package com.flutcloud.flutlink.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// FlutLink brand palettes (Deep Midnight / Bright Daylight). The role
// lightness/chroma values mirror the desktop `src/style.css` `[data-theme]`
// blocks: a single accent seed hue derives the whole primary/secondary/
// tertiary palette ("Material You" style), while the surfaces stay fixed per
// theme. Changing the accent hue in Settings re-tints the entire UI, exactly
// like the desktop.

internal const val LIGHT_DEFAULT_HUE = 266
internal const val MIDNIGHT_DEFAULT_HUE = 220

/** Convert an OKLCH color (CSS Color 4) to an sRGB [Color]. */
internal fun oklch(l: Double, c: Double, hue: Double): Color {
    val h = (((hue % 360.0) + 360.0) % 360.0) * PI / 180.0
    val a = c * cos(h)
    val b = c * sin(h)

    val lPrime = l + 0.3963377774 * a + 0.2158037573 * b
    val mPrime = l - 0.1055613458 * a - 0.0638541728 * b
    val sPrime = l - 0.0894841775 * a - 1.2914855480 * b

    val l3 = lPrime.pow(3)
    val m3 = mPrime.pow(3)
    val s3 = sPrime.pow(3)

    val r = 4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3
    val g = -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3
    val bv = -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3

    fun gamma(x: Double): Float {
        val v = x.coerceIn(0.0, 1.0)
        return (if (v > 0.0031308) 1.055 * v.pow(1.0 / 2.4) - 0.055 else 12.92 * v).toFloat()
    }
    return Color(red = gamma(r), green = gamma(g), blue = gamma(bv), alpha = 1f)
}

private data class BrandSurfaces(
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceDim: Color,
    val surfaceBright: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val outline: Color,
    val outlineVariant: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color
)

private val LightSurfaces = BrandSurfaces(
    background = Color(0xFFF4F4F7),
    onBackground = Color(0xFF09090B),
    surface = Color(0xFFF4F4F7),
    onSurface = Color(0xFF09090B),
    surfaceDim = Color(0xFFD8D8DF),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF52525B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFECECF1),
    surfaceContainerHighest = Color(0xFFD7D7E0),
    outline = Color(0xFFB9B9C6),
    outlineVariant = Color(0xFFECECF1),
    inverseSurface = Color(0xFF2E2E36),
    inverseOnSurface = Color(0xFFF4F4F7)
)

private val MidnightSurfaces = BrandSurfaces(
    background = Color(0xFF05070F),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF05070F),
    onSurface = Color(0xFFF8FAFC),
    surfaceDim = Color(0xFF05070F),
    surfaceBright = Color(0xFF16224B),
    surfaceVariant = Color(0xFF0A0F22),
    onSurfaceVariant = Color(0xFFA3B1D6),
    surfaceContainerLowest = Color(0xFF02040C),
    surfaceContainerLow = Color(0xFF081021),
    surfaceContainer = Color(0xFF0A0F22),
    surfaceContainerHigh = Color(0xFF111A3A),
    surfaceContainerHighest = Color(0xFF1B2A57),
    outline = Color(0xFF2C4079),
    outlineVariant = Color(0xFF0E1529),
    inverseSurface = Color(0xFFEEF1FA),
    inverseOnSurface = Color(0xFF111A3A)
)

/** Bright Daylight brand palette (`[data-theme="light"]`, light mode). */
internal fun lightScheme(accentHue: Int): ColorScheme {
    val h = accentHue.toDouble()
    val s = LightSurfaces
    return lightColorScheme(
        primary = oklch(0.47, 0.22, h),
        onPrimary = oklch(0.99, 0.003, h),
        primaryContainer = oklch(0.93, 0.07, h),
        onPrimaryContainer = oklch(0.29, 0.12, h),
        secondary = oklch(0.52, 0.08, h + 16),
        onSecondary = oklch(0.99, 0.003, h + 16),
        secondaryContainer = oklch(0.92, 0.05, h + 16),
        onSecondaryContainer = oklch(0.28, 0.07, h + 16),
        tertiary = oklch(0.5, 0.07, h - 90),
        onTertiary = oklch(0.99, 0.003, h - 90),
        tertiaryContainer = oklch(0.92, 0.05, h - 90),
        onTertiaryContainer = oklch(0.28, 0.07, h - 90),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = s.background,
        onBackground = s.onBackground,
        surface = s.surface,
        onSurface = s.onSurface,
        surfaceDim = s.surfaceDim,
        surfaceBright = s.surfaceBright,
        surfaceVariant = s.surfaceVariant,
        onSurfaceVariant = s.onSurfaceVariant,
        surfaceContainerLowest = s.surfaceContainerLowest,
        surfaceContainerLow = s.surfaceContainerLow,
        surfaceContainer = s.surfaceContainer,
        surfaceContainerHigh = s.surfaceContainerHigh,
        surfaceContainerHighest = s.surfaceContainerHighest,
        outline = s.outline,
        outlineVariant = s.outlineVariant,
        inverseSurface = s.inverseSurface,
        inverseOnSurface = s.inverseOnSurface,
        inversePrimary = oklch(0.82, 0.12, h)
    )
}

/** Deep navy brand palette (`[data-theme="midnight"]`, dark mode). */
internal fun midnightScheme(accentHue: Int): ColorScheme {
    val h = accentHue.toDouble()
    val s = MidnightSurfaces
    return darkColorScheme(
        primary = oklch(0.6, 0.18, h),
        onPrimary = oklch(0.99, 0.003, h),
        primaryContainer = oklch(0.33, 0.12, h),
        onPrimaryContainer = oklch(0.9, 0.08, h),
        secondary = oklch(0.7, 0.08, h + 20),
        onSecondary = oklch(0.16, 0.04, h + 20),
        secondaryContainer = oklch(0.3, 0.06, h + 20),
        onSecondaryContainer = oklch(0.9, 0.05, h + 20),
        tertiary = oklch(0.72, 0.1, h - 70),
        onTertiary = oklch(0.16, 0.04, h - 70),
        tertiaryContainer = oklch(0.32, 0.07, h - 70),
        onTertiaryContainer = oklch(0.9, 0.05, h - 70),
        error = oklch(0.72, 0.15, 25.0),
        onError = oklch(0.18, 0.03, 25.0),
        errorContainer = oklch(0.35, 0.12, 25.0),
        onErrorContainer = oklch(0.92, 0.07, 25.0),
        background = s.background,
        onBackground = s.onBackground,
        surface = s.surface,
        onSurface = s.onSurface,
        surfaceDim = s.surfaceDim,
        surfaceBright = s.surfaceBright,
        surfaceVariant = s.surfaceVariant,
        onSurfaceVariant = s.onSurfaceVariant,
        surfaceContainerLowest = s.surfaceContainerLowest,
        surfaceContainerLow = s.surfaceContainerLow,
        surfaceContainer = s.surfaceContainer,
        surfaceContainerHigh = s.surfaceContainerHigh,
        surfaceContainerHighest = s.surfaceContainerHighest,
        outline = s.outline,
        outlineVariant = s.outlineVariant,
        inverseSurface = s.inverseSurface,
        inverseOnSurface = s.inverseOnSurface,
        inversePrimary = oklch(0.85, 0.1, h)
    )
}