package com.flutcloud.flutlink

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import platform.UIKit.UIViewController

/**
 * iOS entry point used by the `iosApp/` Xcode shell: instantiates a
 * [ComposeUIViewController] hosting the shared Compose UI.
 *
 * The feature-complete client UI lives in `androidMain` (it is coupled to
 * Android-only APIs: EncryptedSharedPreferences, Storage Access Framework,
 * Android string resources). Porting it to `commonMain` is tracked as
 * follow-up work — until then this hosts a branded placeholder so the
 * framework, Xcode shell and CI pipeline are exercised end to end.
 */
fun MainViewController(): UIViewController = androidx.compose.ui.window.ComposeUIViewController {
    IosPlaceholderApp()
}

@Composable
private fun IosPlaceholderApp() {
    MaterialTheme {
        Scaffold { padding ->
            Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "FlutLink",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2F80ED)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "iOS (Kotlin Multiplatform)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "The shared KMP module is building. Feature parity",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "with the Android client is follow-up work.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
