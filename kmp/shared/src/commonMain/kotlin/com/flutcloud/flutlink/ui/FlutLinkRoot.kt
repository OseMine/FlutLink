package com.flutcloud.flutlink.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.ui.navigation.AppNavigation
import com.flutcloud.flutlink.ui.theme.FlutLinkTheme

/**
 * Platform-independent app root used by both the Android activity and the iOS
 * ComposeUIViewController: wires the container into the composition, applies
 * the persisted theme and hosts the navigation graph.
 */
@Composable
fun FlutLinkRoot(container: AppContainer) {
    val themePreference by container.settingsStore.themePreference.collectAsState(initial = "system")
    val dynamicColor by container.settingsStore.dynamicColor.collectAsState(initial = true)
    val accentHue by container.settingsStore.accentHue.collectAsState(initial = null)

    FlutLinkTheme(
        themePreference = themePreference,
        // Only honoured where dynamic color exists (Android 12+).
        dynamicColor = if (container.supportsDynamicColor) dynamicColor else false,
        accentHue = accentHue
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            CompositionLocalProvider(LocalAppContainer provides container) {
                AppNavigation(container)
            }
        }
    }
}
