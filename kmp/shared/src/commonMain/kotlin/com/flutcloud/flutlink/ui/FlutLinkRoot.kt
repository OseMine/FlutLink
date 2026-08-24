package com.flutcloud.flutlink.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.data.AppUpdate
import com.flutcloud.flutlink.data.NetworkException
import com.flutcloud.flutlink.data.downloadAndInstall
import com.flutcloud.flutlink.resources.Res
import com.flutcloud.flutlink.resources.cancel
import com.flutcloud.flutlink.resources.downloading
import com.flutcloud.flutlink.resources.update
import com.flutcloud.flutlink.resources.update_available
import com.flutcloud.flutlink.resources.update_available_text
import com.flutcloud.flutlink.resources.update_download_failed
import com.flutcloud.flutlink.resources.update_download_failed_detail
import com.flutcloud.flutlink.resources.update_downloading_name
import com.flutcloud.flutlink.ui.navigation.AppNavigation
import com.flutcloud.flutlink.ui.theme.FlutLinkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

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
                AutoUpdatePrompt(container)
            }
        }
    }
}

/**
 * Silent update check at app start, only on platforms with a self-updater
 * (Android). When a newer release is published, offers download + install;
 * failures are shown inside the dialog so no global toast plumbing is needed.
 */
@Composable
private fun AutoUpdatePrompt(container: AppContainer) {
    if (!container.updatesSupported) return

    var pending by remember { mutableStateOf<AppUpdate?>(null) }
    var installing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<UiMessage?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // Silent: up-to-date or unreachable must not disturb the startup flow.
        pending = runCatching {
            container.updateChecker.checkForUpdate(container.config.appVersion)
        }.getOrNull()
    }

    val found = pending ?: return
    val failureMessage = failure
    AlertDialog(
        onDismissRequest = {
            if (!installing) {
                pending = null
                failure = null
            }
        },
        title = { Text(stringResource(Res.string.update_available)) },
        text = {
            Text(
                when {
                    installing -> stringResource(Res.string.update_downloading_name, found.version)
                    failureMessage != null -> failureMessage.resolve()
                    else -> stringResource(Res.string.update_available_text, found.version)
                }
            )
        },
        confirmButton = {
            TextButton(
                enabled = !installing,
                onClick = {
                    failure = null
                    installing = true
                    scope.launch {
                        try {
                            container.platform.downloadAndInstall(found)
                            pending = null
                        } catch (e: NetworkException) {
                            val detail = e.cause?.message
                            failure = if (detail.isNullOrBlank()) {
                                UiMessage(Res.string.update_download_failed)
                            } else {
                                UiMessage(Res.string.update_download_failed_detail, detail)
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            failure = unexpectedUiMessage(e.message)
                        } finally {
                            installing = false
                        }
                    }
                }
            ) {
                Text(
                    if (installing) stringResource(Res.string.downloading)
                    else stringResource(Res.string.update)
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !installing,
                onClick = {
                    pending = null
                    failure = null
                }
            ) { Text(stringResource(Res.string.cancel)) }
        }
    )
}
