package com.flutcloud.flutlink.ui.settings

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
import androidx.compose.material.icons.filled.Logout
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.R
import com.flutcloud.flutlink.core.AccountMeta
import com.flutcloud.flutlink.ui.components.SectionHeader
import com.flutcloud.flutlink.ui.flutLinkViewModel
import com.flutcloud.flutlink.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onLoggedOut: () -> Unit) {
    val vm = flutLinkViewModel { SettingsViewModel(it) }
    val context = LocalContext.current
    val accounts by vm.accounts.collectAsState()
    val themePreference by vm.themePreference.collectAsState()
    val dynamicColor by vm.dynamicColor.collectAsState()
    val serverInfo by vm.serverInfo.collectAsState()
    val toast by vm.toast.collectAsState()
    val update by vm.update.collectAsState()
    val checkingUpdate by vm.checkingUpdate.collectAsState()
    val installingUpdate by vm.installingUpdate.collectAsState()

    val session by container.sessionManager.session.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.loadServerInfo() }
    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it.resolve(context))
            vm.consumeToast()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }) },
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
            SectionHeader(stringResource(R.string.account))
            ListItem(
                headlineContent = {
                    Text(session?.username ?: stringResource(R.string.not_signed_in), style = MaterialTheme.typography.titleMedium)
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
                    headlineContent = { Text(info.name ?: stringResource(R.string.flut_cloud_app)) },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                info.version,
                                info.user,
                                info.features?.size?.let { stringResource(R.string.feature_count, it) }
                            ).joinToString(" · ")
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Theme
            SectionHeader(stringResource(R.string.appearance))
            ListItem(
                headlineContent = { Text(stringResource(R.string.theme)) },
                supportingContent = { Text(stringResource(R.string.theme_hint)) },
                leadingContent = { Icon(Icons.Default.DarkMode, contentDescription = null) },
            )
            val themeOptions = listOf(
                "system" to R.string.theme_system,
                "light" to R.string.theme_light,
                "dark" to R.string.theme_dark
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
            ListItem(
                headlineContent = { Text(stringResource(R.string.dynamic_color)) },
                supportingContent = { Text(stringResource(R.string.dynamic_color_hint)) },
                leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                trailingContent = {
                    Switch(checked = dynamicColor, onCheckedChange = { vm.setDynamicColor(it) })
                }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Updates
            SectionHeader(stringResource(R.string.updates))
            ListItem(
                headlineContent = { Text(stringResource(R.string.flutlink_for_android)) },
                supportingContent = { Text(stringResource(R.string.version_format, vm.appVersion)) },
                leadingContent = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
                trailingContent = {
                    TextButton(onClick = { vm.checkForUpdate() }, enabled = !checkingUpdate && !installingUpdate) {
                        Text(
                            if (checkingUpdate) stringResource(R.string.checking)
                            else stringResource(R.string.check_for_updates)
                        )
                    }
                }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Accounts
            SectionHeader(stringResource(R.string.accounts))
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
                    stringResource(R.string.flutcloud_only_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))
            TextButton(
                onClick = {
                    vm.signOut()
                    onLoggedOut()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sign_out))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    update?.let { found ->
        AlertDialog(
            onDismissRequest = { vm.dismissUpdate() },
            title = { Text(stringResource(R.string.update_available)) },
            text = {
                Text(
                    if (installingUpdate) {
                        stringResource(R.string.update_downloading_name, found.version)
                    } else {
                        stringResource(R.string.update_available_text, found.version)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.installUpdate() },
                    enabled = !installingUpdate
                ) { Text(if (installingUpdate) stringResource(R.string.downloading) else stringResource(R.string.update)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { vm.dismissUpdate() },
                    enabled = !installingUpdate
                ) { Text(stringResource(R.string.cancel)) }
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
                if (isActive) stringResource(R.string.account_active, meta.username) else meta.username,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = { Text(meta.instanceUrl) },
        leadingContent = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
        trailingContent = {
            if (!isActive) {
                TextButton(onClick = onSwitch) { Text(stringResource(R.string.switch_account)) }
            } else {
                IconButton(onClick = { confirmRemove = true }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_account))
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
    if (confirmRemove) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.remove_account_confirm, meta.username)) },
            text = { Text(stringResource(R.string.remove_account_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}