package com.flutcloud.flutlink.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * FlutCloud brand theming.
 *
 * [brandTheme] mirrors the Desktop theme selector:
 * - "operationflut" — default dark brand theme (accent hue 266)
 * - "midnight" — deep-navy brand theme (accent hue 220)
 * - "system" — follow the OS (dark → midnight, light → light theme)
 *
 * [accentHue] is a "Material You"-style seed (0..360) that derives the whole
 * primary/secondary/tertiary palette; -1 keeps the theme default. Android 12+
 * wallpaper palettes ([dynamicColor]) only apply in "system" mode without a
 * custom accent hue, matching the Desktop behaviour.
 */
@Composable
fun FlutLinkTheme(
    brandTheme: String = "system",
    accentHue: Float = -1f,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val isDark = brandTheme == "operationflut" || brandTheme == "midnight" || systemDark
    val midnight = brandTheme == "midnight" || (brandTheme == "system" && systemDark)
    val defaultHue = if (midnight) 220f else 266f
    val hue = if (accentHue in 0f..360f) accentHue else defaultHue
    val customHue = accentHue in 0f..360f

    val colorScheme = when {
        dynamicColor && brandTheme == "system" && !customHue &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> buildFlutColorScheme(dark = isDark, midnight = midnight, hue = hue)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FlutTypography,
        shapes = FlutShapes,
        content = content
    )
}
