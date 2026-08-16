package com.flutcloud.flutlink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.flutcloud.flutlink.ui.navigation.AppNavigation
import com.flutcloud.flutlink.ui.theme.FlutLinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as FlutLinkApplication).container
        setContent {
            FlutLinkRoot(container)
        }
    }
}

@Composable
private fun FlutLinkRoot(container: AppContainer) {
    val themePreference by container.settingsStore.themePreference.collectAsState(initial = "system")
    val dynamicColor by container.settingsStore.dynamicColor.collectAsState(initial = true)
    val darkTheme = when (themePreference) {
        "light" -> false
        "dark" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    FlutLinkTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AppNavigation(container)
        }
    }
}