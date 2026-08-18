package com.flutcloud.flutlink.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// FlutCloud brand color system, mirroring src/style.css.
//
// operationflut (hue 266) and midnight (hue 220) are the dark brand themes,
// "light" is the adaptive light theme. The accent hue is a seed from which
// the primary/secondary/tertiary palette is derived at runtime
// ("Material You" style) — the same mechanism as the Desktop accent slider.

private fun oklchToColor(L: Double, C: Double, h: Double): Color {
    val hr = Math.toRadians(h)
    val a = C * cos(hr)
    val b = C * sin(hr)
    val l_ = L + 0.3963377774 * a + 0.2158037573 * b
    val m_ = L - 0.1055613458 * a - 0.0638541728 * b
    val s_ = L - 0.0894841775 * a - 1.2914855480 * b
    val l = l_ * l_ * l_
    val m = m_ * m_ * m_
    val s = s_ * s_ * s_
    val r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    val g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    val bCh = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    fun gamma(x: Double): Float {
        val v = if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1.0 / 2.4) - 0.055
        return v.toFloat().coerceIn(0f, 1f)
    }
    return Color(gamma(r), gamma(g), gamma(bCh))
}

// --- Surfaces (fixed per theme, from style.css) ---

private val OperationFlutBackground = Color(0xFF090821)
private val OperationFlutOnBackground = Color(0xFFFAFAFA)
private val OperationFlutSurface = Color(0xFF090821)
private val OperationFlutOnSurface = Color(0xFFFAFAFA)
private val OperationFlutSurfaceDim = Color(0xFF090821)
private val OperationFlutSurfaceBright = Color(0xFF1A1838)
private val OperationFlutSurfaceContainerLowest = Color(0xFF060514)
private val OperationFlutSurfaceContainerLow = Color(0xFF0C0B26)
private val OperationFlutSurfaceContainer = Color(0xFF0D0C2B)
private val OperationFlutSurfaceContainerHigh = Color(0xFF161434)
private val OperationFlutSurfaceContainerHighest = Color(0xFF232152)
private val OperationFlutOnSurfaceVariant = Color(0xFF9A99B3)
private val OperationFlutOutline = Color(0xFF3C3A6E)
private val OperationFlutOutlineVariant = Color(0xFF171530)
private val OperationFlutInverseSurface = Color(0xFFF4F4F7)
private val OperationFlutInverseOnSurface = Color(0xFF161434)

private val MidnightBackground = Color(0xFF05070F)
private val MidnightOnBackground = Color(0xFFF8FAFC)
private val MidnightSurface = Color(0xFF05070F)
private val MidnightOnSurface = Color(0xFFF8FAFC)
private val MidnightSurfaceDim = Color(0xFF05070F)
private val MidnightSurfaceBright = Color(0xFF16224B)
private val MidnightSurfaceContainerLowest = Color(0xFF02040C)
private val MidnightSurfaceContainerLow = Color(0xFF081021)
private val MidnightSurfaceContainer = Color(0xFF0A0F22)
private val MidnightSurfaceContainerHigh = Color(0xFF111A3A)
private val MidnightSurfaceContainerHighest = Color(0xFF1B2A57)
private val MidnightOnSurfaceVariant = Color(0xFFA3B1D6)
private val MidnightOutline = Color(0xFF2C4079)
private val MidnightOutlineVariant = Color(0xFF0E1529)
private val MidnightInverseSurface = Color(0xFFEEF1FA)
private val MidnightInverseOnSurface = Color(0xFF111A3A)

private val LightBackground = Color(0xFFF4F4F7)
private val LightOnBackground = Color(0xFF09090B)
private val LightSurface = Color(0xFFF4F4F7)
private val LightOnSurface = Color(0xFF09090B)
private val LightSurfaceDim = Color(0xFFD8D8DF)
private val LightSurfaceBright = Color(0xFFFFFFFF)
private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFFAFAFC)
private val LightSurfaceContainer = Color(0xFFFFFFFF)
private val LightSurfaceContainerHigh = Color(0xFFECECF1)
private val LightSurfaceContainerHighest = Color(0xFFD7D7E0)
private val LightOnSurfaceVariant = Color(0xFF52525B)
private val LightOutline = Color(0xFFB9B9C6)
private val LightOutlineVariant = Color(0xFFECECF1)
private val LightInverseSurface = Color(0xFF2E2E36)
private val LightInverseOnSurface = Color(0xFFF4F4F7)

private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)

/**
 * Builds the FlutCloud brand [ColorScheme].
 *
 * @param dark     dark (operationflut/midnight) vs. light theme
 * @param midnight deep-navy variant of the OperationFlut palette
 * @param hue      accent seed hue (0..360) used for primary/secondary/tertiary
 */
fun buildFlutColorScheme(dark: Boolean, midnight: Boolean, hue: Float): ColorScheme {
    val h = hue.toDouble()
    if (dark) {
        if (midnight) {
            return darkColorScheme(
                primary = oklchToColor(0.60, 0.18, h),
                onPrimary = oklchToColor(0.99, 0.003, h),
                primaryContainer = oklchToColor(0.33, 0.12, h),
                onPrimaryContainer = oklchToColor(0.90, 0.08, h),
                secondary = oklchToColor(0.70, 0.08, h + 20),
                onSecondary = oklchToColor(0.16, 0.04, h + 20),
                secondaryContainer = oklchToColor(0.30, 0.06, h + 20),
                onSecondaryContainer = oklchToColor(0.90, 0.05, h + 20),
                tertiary = oklchToColor(0.72, 0.10, h - 70),
                onTertiary = oklchToColor(0.16, 0.04, h - 70),
                tertiaryContainer = oklchToColor(0.32, 0.07, h - 70),
                onTertiaryContainer = oklchToColor(0.90, 0.05, h - 70),
                error = oklchToColor(0.72, 0.15, 25.0),
                onError = oklchToColor(0.18, 0.03, 25.0),
                errorContainer = oklchToColor(0.35, 0.12, 25.0),
                onErrorContainer = oklchToColor(0.92, 0.07, 25.0),
                inversePrimary = oklchToColor(0.85, 0.10, h),
                background = MidnightBackground,
                onBackground = MidnightOnBackground,
                surface = MidnightSurface,
                onSurface = MidnightOnSurface,
                surfaceDim = MidnightSurfaceDim,
                surfaceBright = MidnightSurfaceBright,
                surfaceContainerLowest = MidnightSurfaceContainerLowest,
                surfaceContainerLow = MidnightSurfaceContainerLow,
                surfaceContainer = MidnightSurfaceContainer,
                surfaceContainerHigh = MidnightSurfaceContainerHigh,
                surfaceContainerHighest = MidnightSurfaceContainerHighest,
                surfaceVariant = MidnightSurfaceContainerHighest,
                onSurfaceVariant = MidnightOnSurfaceVariant,
                outline = MidnightOutline,
                outlineVariant = MidnightOutlineVariant,
                inverseSurface = MidnightInverseSurface,
                inverseOnSurface = MidnightInverseOnSurface
            )
        }
        return darkColorScheme(
            primary = oklchToColor(0.52, 0.22, h),
            onPrimary = oklchToColor(0.99, 0.003, h),
            primaryContainer = oklchToColor(0.30, 0.13, h),
            onPrimaryContainer = oklchToColor(0.88, 0.08, h),
            secondary = oklchToColor(0.66, 0.10, h + 16),
            onSecondary = oklchToColor(0.16, 0.05, h + 16),
            secondaryContainer = oklchToColor(0.28, 0.07, h + 16),
            onSecondaryContainer = oklchToColor(0.90, 0.06, h + 16),
            tertiary = oklchToColor(0.68, 0.11, h - 90),
            onTertiary = oklchToColor(0.16, 0.04, h - 90),
            tertiaryContainer = oklchToColor(0.29, 0.07, h - 90),
            onTertiaryContainer = oklchToColor(0.90, 0.05, h - 90),
            error = oklchToColor(0.70, 0.17, 25.0),
            onError = oklchToColor(0.18, 0.03, 25.0),
            errorContainer = oklchToColor(0.33, 0.13, 25.0),
            onErrorContainer = oklchToColor(0.92, 0.07, 25.0),
            inversePrimary = oklchToColor(0.82, 0.12, h),
            background = OperationFlutBackground,
            onBackground = OperationFlutOnBackground,
            surface = OperationFlutSurface,
            onSurface = OperationFlutOnSurface,
            surfaceDim = OperationFlutSurfaceDim,
            surfaceBright = OperationFlutSurfaceBright,
            surfaceContainerLowest = OperationFlutSurfaceContainerLowest,
            surfaceContainerLow = OperationFlutSurfaceContainerLow,
            surfaceContainer = OperationFlutSurfaceContainer,
            surfaceContainerHigh = OperationFlutSurfaceContainerHigh,
            surfaceContainerHighest = OperationFlutSurfaceContainerHighest,
            surfaceVariant = OperationFlutSurfaceContainerHighest,
            onSurfaceVariant = OperationFlutOnSurfaceVariant,
            outline = OperationFlutOutline,
            outlineVariant = OperationFlutOutlineVariant,
            inverseSurface = OperationFlutInverseSurface,
            inverseOnSurface = OperationFlutInverseOnSurface
        )
    }
    return lightColorScheme(
        primary = oklchToColor(0.47, 0.22, h),
        onPrimary = oklchToColor(0.99, 0.003, h),
        primaryContainer = oklchToColor(0.93, 0.07, h),
        onPrimaryContainer = oklchToColor(0.29, 0.12, h),
        secondary = oklchToColor(0.52, 0.08, h + 16),
        onSecondary = oklchToColor(0.99, 0.003, h + 16),
        secondaryContainer = oklchToColor(0.92, 0.05, h + 16),
        onSecondaryContainer = oklchToColor(0.28, 0.07, h + 16),
        tertiary = oklchToColor(0.50, 0.07, h - 90),
        onTertiary = oklchToColor(0.99, 0.003, h - 90),
        tertiaryContainer = oklchToColor(0.92, 0.05, h - 90),
        onTertiaryContainer = oklchToColor(0.28, 0.07, h - 90),
        error = LightError,
        onError = LightOnError,
        errorContainer = LightErrorContainer,
        onErrorContainer = LightOnErrorContainer,
        inversePrimary = oklchToColor(0.82, 0.12, h),
        background = LightBackground,
        onBackground = LightOnBackground,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceDim = LightSurfaceDim,
        surfaceBright = LightSurfaceBright,
        surfaceContainerLowest = LightSurfaceContainerLowest,
        surfaceContainerLow = LightSurfaceContainerLow,
        surfaceContainer = LightSurfaceContainer,
        surfaceContainerHigh = LightSurfaceContainerHigh,
        surfaceContainerHighest = LightSurfaceContainerHighest,
        surfaceVariant = LightSurfaceContainerHighest,
        onSurfaceVariant = LightOnSurfaceVariant,
        outline = LightOutline,
        outlineVariant = LightOutlineVariant,
        inverseSurface = LightInverseSurface,
        inverseOnSurface = LightInverseOnSurface
    )
}
