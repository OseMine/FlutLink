package com.flutcloud.flutlink.ui.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.core.AccountMeta
import com.flutcloud.flutlink.ui.components.SectionHeader
import com.flutcloud.flutlink.ui.flutLinkViewModel
import com.flutcloud.flutlink.ui.theme.defaultAccentHue
import com.flutcloud.flutlink.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt
import com.flutcloud.flutlink.resources.Res
import com.flutcloud.flutlink.resources.accent_color
import com.flutcloud.flutlink.resources.accent_color_hint
import com.flutcloud.flutlink.resources.accent_reset
import com.flutcloud.flutlink.resources.account
import com.flutcloud.flutlink.resources.account_active
import com.flutcloud.flutlink.resources.accounts
import com.flutcloud.flutlink.resources.appearance
import com.flutcloud.flutlink.resources.cancel
import com.flutcloud.flutlink.resources.check_for_updates
import com.flutcloud.flutlink.resources.checking
import com.flutcloud.flutlink.resources.downloading
import com.flutcloud.flutlink.resources.dynamic_color
import com.flutcloud.flutlink.resources.dynamic_color_hint
import com.flutcloud.flutlink.resources.feature_count
import com.flutcloud.flutlink.resources.flut_cloud_app
import com.flutcloud.flutlink.resources.flutcloud_only_note
import com.flutcloud.flutlink.resources.flutlink_mobile
import com.flutcloud.flutlink.resources.not_signed_in
import com.flutcloud.flutlink.resources.remove
import com.flutcloud.flutlink.resources.remove_account
import com.flutcloud.flutlink.resources.remove_account_confirm
import com.flutcloud.flutlink.resources.remove_account_confirm_text
import com.flutcloud.flutlink.resources.settings
import com.flutcloud.flutlink.resources.sign_out
import com.flutcloud.flutlink.resources.switch_account
import com.flutcloud.flutlink.resources.theme
import com.flutcloud.flutlink.resources.theme_hint
import com.flutcloud.flutlink.resources.theme_midnight
import com.flutcloud.flutlink.resources.theme_operationflut
import com.flutcloud.flutlink.resources.theme_system
import com.flutcloud.flutlink.resources.theme_system_note
import com.flutcloud.flutlink.resources.update
import com.flutcloud.flutlink.resources.update_available
import com.flutcloud.flutlink.resources.update_available_text
import com.flutcloud.flutlink.resources.update_downloading_name
import com.flutcloud.flutlink.resources.updates
import com.flutcloud.flutlink.resources.version_format


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onLoggedOut: () -> Unit) {
    val vm = flutLinkViewModel { SettingsViewModel(it) }
        val accounts by vm.accounts.collectAsState()
    val themePreference by vm.themePreference.collectAsState()
    val dynamicColor by vm.dynamicColor.collectAsState()
    val accentHue by vm.accentHue.collectAsState()
    val serverInfo by vm.serverInfo.collectAsState()
    val toast by vm.toast.collectAsState()
    val update by vm.update.collectAsState()
    val checkingUpdate by vm.checkingUpdate.collectAsState()
    val installingUpdate by vm.installingUpdate.collectAsState()

    val session by container.sessionManager.session.collectAsState()
    val sessionKey = session?.let { "${it.baseUrl}|${it.username}" }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.loadServerInfo() }
    LaunchedEffect(sessionKey) { vm.loadServerInfo() }
    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it.resolveSuspend())
            vm.consumeToast()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(Res.string.settings)) }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Account
            SectionHeader(stringResource(Res.string.account))
            ListItem(
                headlineContent = {
                    Text(session?.username ?: stringResource(Res.string.not_signed_in), style = MaterialTheme.typography.titleMedium)
                },
                supportingContent = {
                    Text(session?.normalizedBaseUrl ?: "")
                },
                leadingContent = {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(36.dp))
                }
            )
            serverInfo?.let { info ->
                ListItem(
                    headlineContent = { Text(info.name ?: stringResource(Res.string.flut_cloud_app)) },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                info.version,
                                info.user,
                                info.features?.size?.let { stringResource(Res.string.feature_count, it) }
                            ).joinToString(" · ")
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Theme
            SectionHeader(stringResource(Res.string.appearance))
            ListItem(
                headlineContent = { Text(stringResource(Res.string.theme)) },
                supportingContent = { Text(stringResource(Res.string.theme_hint)) },
                leadingContent = { Icon(Icons.Default.DarkMode, contentDescription = null) },
            )
            val themeOptions = listOf(
                "operationflut" to Res.string.theme_operationflut,
                "midnight" to Res.string.theme_midnight,
                "system" to Res.string.theme_system
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                themeOptions.forEachIndexed { index, (value, labelRes) ->
                    SegmentedButton(
                        selected = themePreference == value,
                        onClick = { vm.setThemePreference(value) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size)
                    ) {
                        Text(stringResource(labelRes))
                    }
                }
            }
            Text(
                stringResource(Res.string.theme_system_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            val accentDefault = defaultAccentHue(themePreference, isSystemInDarkTheme())
            val accentValue = accentHue ?: accentDefault
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(Res.string.accent_color),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { vm.setAccentHue(null) }) {
                        Text(stringResource(Res.string.accent_reset))
                    }
                }
                Slider(
                    value = accentValue.toFloat(),
                    onValueChange = { vm.setAccentHue(it.roundToInt()) },
                    valueRange = 0f..360f
                )
                Text(
                    stringResource(Res.string.accent_color_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (container.supportsDynamicColor) {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.dynamic_color)) },
                    supportingContent = { Text(stringResource(Res.string.dynamic_color_hint)) },
                    leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                    trailingContent = {
                        Switch(checked = dynamicColor, onCheckedChange = { vm.setDynamicColor(it) })
                    }
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Updates / version
            SectionHeader(stringResource(Res.string.updates))
            ListItem(
                headlineContent = { Text(stringResource(Res.string.flutlink_mobile)) },
                supportingContent = { Text(stringResource(Res.string.version_format, vm.appVersion)) },
                leadingContent = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
                trailingContent = {
                    if (container.updatesSupported) {
                        TextButton(onClick = { vm.checkForUpdate() }, enabled = !checkingUpdate && !installingUpdate) {
                            Text(
                                if (checkingUpdate) stringResource(Res.string.checking)
                                else stringResource(Res.string.check_for_updates)
                            )
                        }
                    }
                }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Accounts
            SectionHeader(stringResource(Res.string.accounts))
            accounts.forEach { meta ->
                AccountRow(
                    meta = meta,
                    isActive = meta.isActive,
                    onSwitch = { vm.switchAccount(meta) },
                    onRemove = { vm.removeAccount(meta) }
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.flutcloud_only_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))
            TextButton(
                onClick = { vm.signOut() },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.sign_out))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    update?.let { found ->
        AlertDialog(
            onDismissRequest = { vm.dismissUpdate() },
            title = { Text(stringResource(Res.string.update_available)) },
            text = {
                Text(
                    if (installingUpdate) {
                        stringResource(Res.string.update_downloading_name, found.version)
                    } else {
                        stringResource(Res.string.update_available_text, found.version)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.installUpdate() },
                    enabled = !installingUpdate
                ) { Text(if (installingUpdate) stringResource(Res.string.downloading) else stringResource(Res.string.update)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { vm.dismissUpdate() },
                    enabled = !installingUpdate
                ) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }
}

@Composable
private fun AccountRow(
    meta: AccountMeta,
    isActive: Boolean,
    onSwitch: () -> Unit,
    onRemove: () -> Unit
) {
    var confirmRemove by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = {
            Text(
                if (isActive) stringResource(Res.string.account_active, meta.username) else meta.username,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = { Text(meta.instanceUrl) },
        leadingContent = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
        trailingContent = {
            if (!isActive) {
                TextButton(onClick = onSwitch) { Text(stringResource(Res.string.switch_account)) }
            } else {
                IconButton(onClick = { confirmRemove = true }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.remove_account))
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
    if (confirmRemove) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(Res.string.remove_account_confirm, meta.username)) },
            text = { Text(stringResource(Res.string.remove_account_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) { Text(stringResource(Res.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }
}
