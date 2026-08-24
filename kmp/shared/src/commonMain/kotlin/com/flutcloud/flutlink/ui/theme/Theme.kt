package com.flutcloud.flutlink.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * A resolved FlutCloud brand palette. [Light] ("Bright Daylight") and
 * [Midnight] ("Deep Midnight") are selectable on iOS/desktop; Android always
 * follows the device setting. Mirrors the desktop `[data-theme]` resolution
 * in `src/style.css`.
 */
enum class FlutResolvedTheme(val isDark: Boolean) {
    Light(false),
    Midnight(true)
}

/**
 * Map the stored theme preference to a concrete brand palette. Legacy
 * "light"/"dark"/"operationflut" values keep working; anything else falls
 * back to "system".
 */
fun resolveFlutTheme(themePreference: String, darkTheme: Boolean): FlutResolvedTheme =
    when (themePreference) {
        "midnight", "dark", "operationflut" -> FlutResolvedTheme.Midnight
        "light" -> FlutResolvedTheme.Light
        else -> if (darkTheme) FlutResolvedTheme.Midnight else FlutResolvedTheme.Light
    }

/** Default accent hue of the currently resolved brand theme (CSS defaults). */
fun defaultAccentHue(themePreference: String, darkTheme: Boolean): Int =
    when (resolveFlutTheme(themePreference, darkTheme)) {
        FlutResolvedTheme.Midnight -> MIDNIGHT_DEFAULT_HUE
        FlutResolvedTheme.Light -> LIGHT_DEFAULT_HUE
    }

/**
 * Platform Material-You dynamic color scheme; null when the platform does not
 * support it (iOS) or the OS is too old (Android < 12).
 */
@Composable
internal expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?

@Composable
fun FlutLinkTheme(
    themePreference: String = "system",
    // Dynamic color follows Android 12+ wallpaper palettes ("Material You");
    // when off, the FlutCloud brand palettes (tinted by the accent hue) apply.
    dynamicColor: Boolean = true,
    // Accent seed; null keeps the theme's default hue.
    accentHue: Int? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val resolved = resolveFlutTheme(themePreference, darkTheme)
    val hue = accentHue ?: defaultAccentHue(themePreference, darkTheme)

    val colorScheme = if (
        dynamicColor && accentHue == null && themePreference == "system"
    ) {
        platformDynamicColorScheme(darkTheme)
            ?: when (resolved) {
                FlutResolvedTheme.Light -> lightScheme(hue)
                FlutResolvedTheme.Midnight -> midnightScheme(hue)
            }
    } else {
        when (resolved) {
            FlutResolvedTheme.Light -> lightScheme(hue)
            FlutResolvedTheme.Midnight -> midnightScheme(hue)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FlutTypography,
        shapes = FlutShapes,
        content = content
    )
}