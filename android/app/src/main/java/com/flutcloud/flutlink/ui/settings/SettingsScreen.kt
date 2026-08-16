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
import androidx.compose.ui.unit.dp
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.core.AccountMeta
import com.flutcloud.flutlink.ui.components.SectionHeader
import com.flutcloud.flutlink.ui.flutLinkViewModel
import com.flutcloud.flutlink.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onLoggedOut: () -> Unit) {
    val vm = flutLinkViewModel { SettingsViewModel(it) }
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
            snackbar.showSnackbar(it)
            vm.consumeToast()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
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
            SectionHeader("Account")
            ListItem(
                headlineContent = {
                    Text(session?.username ?: "Not signed in", style = MaterialTheme.typography.titleMedium)
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
                    headlineContent = { Text(info.name ?: "FlutCloud app") },
                    supportingContent = {
                        Text(
                            listOfNotNull(info.version, info.user, info.features?.size?.let { "$it features" }).joinToString(" · ")
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Theme
            SectionHeader("Appearance")
            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = { Text("Pick light, dark or follow the system.") },
                leadingContent = { Icon(Icons.Default.DarkMode, contentDescription = null) },
                trailingContent = {
                    SingleChoiceSegmentedButtonRow {
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = themePreference == value,
                                onClick = { vm.setThemePreference(value) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            )
            ListItem(
                headlineContent = { Text("Dynamic color") },
                supportingContent = { Text("Use the Android 12+ Material You palette from your wallpaper.") },
                leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                trailingContent = {
                    Switch(checked = dynamicColor, onCheckedChange = { vm.setDynamicColor(it) })
                }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Updates
            SectionHeader("Updates")
            ListItem(
                headlineContent = { Text("FlutLink for Android") },
                supportingContent = { Text("Version ${vm.appVersion}") },
                leadingContent = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
                trailingContent = {
                    TextButton(onClick = { vm.checkForUpdate() }, enabled = !checkingUpdate && !installingUpdate) {
                        Text(if (checkingUpdate) "Checking…" else "Check for updates")
                    }
                }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Accounts
            SectionHeader("Accounts")
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
                    "FlutCloud-only: servers without the FlutCloud app are rejected.",
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
                Text("Sign out")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    update?.let { found ->
        AlertDialog(
            onDismissRequest = { vm.dismissUpdate() },
            title = { Text("Update available") },
            text = {
                Text(
                    if (installingUpdate) {
                        "Downloading FlutLink ${found.version}…"
                    } else {
                        "FlutLink ${found.version} is available. Download and install it now?"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.installUpdate() },
                    enabled = !installingUpdate
                ) { Text(if (installingUpdate) "Downloading…" else "Update") }
            },
            dismissButton = {
                TextButton(
                    onClick = { vm.dismissUpdate() },
                    enabled = !installingUpdate
                ) { Text("Cancel") }
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
                if (isActive) "${meta.username} (active)" else meta.username,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = { Text(meta.instanceUrl) },
        leadingContent = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
        trailingContent = {
            if (!isActive) {
                TextButton(onClick = onSwitch) { Text("Switch") }
            } else {
                IconButton(onClick = { confirmRemove = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove account")
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
    if (confirmRemove) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${meta.username}?") },
            text = { Text("The stored token for this account will be deleted from the secure store.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
            }
        )
    }
}