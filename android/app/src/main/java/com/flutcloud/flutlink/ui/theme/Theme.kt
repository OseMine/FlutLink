package com.flutcloud.flutlink.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * A resolved FlutCloud brand palette. [Light] is only reached through
 * "system" + light OS mode; [OperationFlut]/[Midnight] are dark-first brand
 * themes. Mirrors the desktop `[data-theme]` resolution in `src/style.css`.
 */
internal enum class FlutResolvedTheme(val isDark: Boolean) {
    Light(false),
    OperationFlut(true),
    Midnight(true)
}

/**
 * Map the stored theme preference to a concrete brand palette. Legacy
 * "light"/"dark" values keep working; anything else falls back to "system".
 */
internal fun resolveFlutTheme(themePreference: String, darkTheme: Boolean): FlutResolvedTheme =
    when (themePreference) {
        "operationflut" -> FlutResolvedTheme.OperationFlut
        "midnight" -> FlutResolvedTheme.Midnight
        "light" -> FlutResolvedTheme.Light
        "dark" -> FlutResolvedTheme.OperationFlut
        else -> if (darkTheme) FlutResolvedTheme.Midnight else FlutResolvedTheme.Light
    }

/** Default accent hue of the currently resolved brand theme (CSS defaults). */
fun defaultAccentHue(themePreference: String, darkTheme: Boolean): Int =
    when (resolveFlutTheme(themePreference, darkTheme)) {
        FlutResolvedTheme.Midnight -> MIDNIGHT_DEFAULT_HUE
        FlutResolvedTheme.OperationFlut -> OPERATIONFLUT_DEFAULT_HUE
        FlutResolvedTheme.Light -> LIGHT_DEFAULT_HUE
    }

@Composable
fun FlutLinkTheme(
    themePreference: String = "system",
    // Dynamic color follows Android 12+ wallpaper palettes ("Material You");
    // when off, the FlutCloud brand palettes (tinted by the accent hue) apply.
    dynamicColor: Boolean = true,
    // Material You accent seed; null keeps the theme's default hue.
    accentHue: Int? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val resolved = resolveFlutTheme(themePreference, darkTheme)
    val hue = accentHue ?: defaultAccentHue(themePreference, darkTheme)

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (resolved.isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> when (resolved) {
            FlutResolvedTheme.Light -> lightScheme(hue)
            FlutResolvedTheme.OperationFlut -> operationflutScheme(hue)
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
