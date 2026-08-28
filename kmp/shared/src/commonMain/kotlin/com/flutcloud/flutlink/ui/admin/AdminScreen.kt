package com.flutcloud.flutlink.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.data.dto.ManagedUser
import com.flutcloud.flutlink.ui.components.EmptyState
import com.flutcloud.flutlink.ui.flutLinkViewModel
import com.flutcloud.flutlink.ui.format.formatBytes
import com.flutcloud.flutlink.ui.viewmodel.AdminViewModel
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import com.flutcloud.flutlink.resources.Res
import com.flutcloud.flutlink.resources.actions
import com.flutcloud.flutlink.resources.add_user
import com.flutcloud.flutlink.resources.admin_add_to_group
import com.flutcloud.flutlink.resources.admin_create_group
import com.flutcloud.flutlink.resources.admin_group_name_label
import com.flutcloud.flutlink.resources.admin_groups_hint
import com.flutcloud.flutlink.resources.admin_groups_title
import com.flutcloud.flutlink.resources.admin_load_more
import com.flutcloud.flutlink.resources.admin_manage_groups
import com.flutcloud.flutlink.resources.admin_no_groups
import com.flutcloud.flutlink.resources.cancel
import com.flutcloud.flutlink.resources.close
import com.flutcloud.flutlink.resources.create
import com.flutcloud.flutlink.resources.create_user
import com.flutcloud.flutlink.resources.delete
import com.flutcloud.flutlink.resources.delete_user
import com.flutcloud.flutlink.resources.delete_user_confirm
import com.flutcloud.flutlink.resources.disabled
import com.flutcloud.flutlink.resources.display_name_optional
import com.flutcloud.flutlink.resources.no_email
import com.flutcloud.flutlink.resources.no_users_found
import com.flutcloud.flutlink.resources.no_users_hint
import com.flutcloud.flutlink.resources.password
import com.flutcloud.flutlink.resources.quota_10gb
import com.flutcloud.flutlink.resources.quota_1gb
import com.flutcloud.flutlink.resources.quota_5gb
import com.flutcloud.flutlink.resources.quota_custom
import com.flutcloud.flutlink.resources.quota_custom_title
import com.flutcloud.flutlink.resources.quota_custom_value
import com.flutcloud.flutlink.resources.quota_set
import com.flutcloud.flutlink.resources.quota_unit_gb
import com.flutcloud.flutlink.resources.quota_unit_mb
import com.flutcloud.flutlink.resources.quota_unknown
import com.flutcloud.flutlink.resources.quota_unlimited
import com.flutcloud.flutlink.resources.remove
import com.flutcloud.flutlink.resources.search_users
import com.flutcloud.flutlink.resources.search_users_required
import com.flutcloud.flutlink.resources.unlimited
import com.flutcloud.flutlink.resources.user_administration
import com.flutcloud.flutlink.resources.user_id
import com.flutcloud.flutlink.resources.view_files


private const val GB = 1024L * 1024 * 1024
private const val MB = 1024L * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(container: AppContainer, onViewFiles: (ManagedUser) -> Unit) {
    val vm = flutLinkViewModel { AdminViewModel(it) }
        val users by vm.users.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val search by vm.search.collectAsState()
    val hasMore by vm.hasMore.collectAsState()

    var showCreate by remember { mutableStateOf(false) }
    var groupTarget by remember { mutableStateOf<ManagedUser?>(null) }
    var quotaTarget by remember { mutableStateOf<ManagedUser?>(null) }
    var deleteTarget by remember { mutableStateOf<ManagedUser?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(search) {
        if (search.isBlank()) {
            vm.clearSearch()
            return@LaunchedEffect
        }
        delay(300)
        vm.loadUsers()
    }
    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it.resolveSuspend())
            vm.clearError()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(Res.string.user_administration)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(Res.string.add_user)) }
            )
        }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            // Search field
            TextField(
                value = search,
                onValueChange = { vm.search.value = it },
                placeholder = { Text(stringResource(Res.string.search_users)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when {
                loading && users.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                users.isEmpty() -> EmptyState(
                    icon = Icons.Default.Person,
                    title = stringResource(if (search.isBlank()) Res.string.search_users_required else Res.string.no_users_found),
                    hint = if (search.isBlank()) null else stringResource(Res.string.no_users_hint)
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(users, key = { it.id }) { user ->
                        UserRow(
                            user = user,
                            onToggleEnabled = { vm.setEnabled(user, !user.enabled) },
                            onDelete = { deleteTarget = user },
                            onQuota = { quotaBytes -> vm.setQuota(user, quotaBytes) },
                            onQuotaCustom = { quotaTarget = user },
                            onManageGroups = { groupTarget = user },
                            onViewFiles = { onViewFiles(user) }
                        )
                    }
                    if (hasMore) {
                        item {
                            LoadMoreButton(
                                loading = loading,
                                onClick = { vm.loadMore() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateUserDialog(
            onDismiss = { showCreate = false },
            onCreate = { id, password, displayName ->
                showCreate = false
                vm.createUser(id, password, displayName)
            }
        )
    }

    groupTarget?.let { target ->
        val current = users.firstOrNull { it.id == target.id } ?: target
        GroupsDialog(
            user = current,
            onDismiss = { groupTarget = null },
            onAddToGroup = { group -> vm.addToGroup(current, group) },
            onRemoveFromGroup = { group -> vm.removeFromGroup(current, group) },
            onCreateGroup = { group -> vm.createGroup(group) }
        )
    }

    quotaTarget?.let { target ->
        val current = users.firstOrNull { it.id == target.id } ?: target
        CustomQuotaDialog(
            user = current,
            onDismiss = { quotaTarget = null },
            onConfirm = { bytes ->
                quotaTarget = null
                vm.setQuota(current, bytes)
            }
        )
    }

    deleteTarget?.let { target ->
        val current = users.firstOrNull { it.id == target.id } ?: target
        DeleteUserDialog(
            user = current,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                vm.deleteUser(current)
            }
        )
    }
}

@Composable
private fun LoadMoreButton(loading: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(24.dp))
        } else {
            OutlinedButton(onClick = onClick) {
                Text(stringResource(Res.string.admin_load_more))
            }
        }
    }
}

@Composable
private fun UserRow(
    user: ManagedUser,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    onQuota: (Long?) -> Unit,
    onQuotaCustom: () -> Unit,
    onManageGroups: () -> Unit,
    onViewFiles: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { },
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading icon
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(12.dp))

            // Content
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.displayName ?: user.id,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!user.enabled) {
                        Spacer(Modifier.width(8.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text(stringResource(Res.string.disabled)) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                val quotaText = user.quota?.let { q ->
                    val used = q.used
                    val total = q.total
                    when {
                        used != null && total != null && total > 0 ->
                            "${formatBytes(used)} / ${formatBytes(total)}"
                        total == null || total <= 0 -> stringResource(Res.string.unlimited)
                        else -> formatBytes(total)
                    }
                } ?: stringResource(Res.string.quota_unknown)
                Text(
                    listOf(
                        "@${user.id}",
                        quotaText,
                        user.email?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.no_email)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Trailing: switch + menu
            Switch(
                checked = user.enabled,
                onCheckedChange = { onToggleEnabled() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.actions))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.quota_unlimited)) },
                        leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onQuota(null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.quota_1gb)) },
                        leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onQuota(GB)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.quota_5gb)) },
                        leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onQuota(5 * GB)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.quota_10gb)) },
                        leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onQuota(10 * GB)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.quota_custom)) },
                        leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onQuotaCustom()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.admin_manage_groups)) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onManageGroups()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.view_files)) },
                        leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onViewFiles()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.delete_user)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun CreateUserDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String?) -> Unit
) {
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.create_user)) },
        text = {
            Column {
                TextField(value = userId, onValueChange = { userId = it }, singleLine = true, label = { Text(stringResource(Res.string.user_id)) })
                Spacer(Modifier.height(8.dp))
                TextField(value = password, onValueChange = { password = it }, singleLine = true, label = { Text(stringResource(Res.string.password)) })
                Spacer(Modifier.height(8.dp))
                TextField(value = displayName, onValueChange = { displayName = it }, singleLine = true, label = { Text(stringResource(Res.string.display_name_optional)) })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(userId.trim(), password, displayName.trim().ifBlank { null }) },
                enabled = userId.isNotBlank() && password.isNotBlank()
            ) { Text(stringResource(Res.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}

@Composable
private fun GroupsDialog(
    user: ManagedUser,
    onDismiss: () -> Unit,
    onAddToGroup: (String) -> Unit,
    onRemoveFromGroup: (String) -> Unit,
    onCreateGroup: (String) -> Unit
) {
    var groupInput by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.admin_groups_title, user.displayName ?: user.id)) },
        text = {
            Column {
                Text("@${user.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                if (user.groups.isEmpty()) {
                    Text(
                        stringResource(Res.string.admin_no_groups),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    user.groups.forEach { group ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = {},
                                label = { Text(group) },
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { onRemoveFromGroup(group) }) {
                                Text(stringResource(Res.string.remove))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = groupInput,
                    onValueChange = { groupInput = it },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.admin_group_name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(Res.string.admin_groups_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = {
            Row {
                Button(
                    onClick = { onCreateGroup(groupInput.trim()) },
                    enabled = groupInput.isNotBlank()
                ) { Text(stringResource(Res.string.admin_create_group)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onAddToGroup(groupInput.trim())
                        groupInput = ""
                    },
                    enabled = groupInput.isNotBlank()
                ) { Text(stringResource(Res.string.admin_add_to_group)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
        }
    )
}

@Composable
private fun DeleteUserDialog(
    user: ManagedUser,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.delete_user)) },
        text = {
            Text(stringResource(Res.string.delete_user_confirm, user.displayName ?: user.id))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}


/** "12.5" style one-decimal formatting without java.text/Locale. */
private fun formatOneDecimal(value: Double): String {
    val rounded = (value * 10).roundToLong()
    val whole = rounded / 10
    val frac = rounded % 10
    return if (frac == 0L) "$whole" else "$whole.$frac"
}
private enum class QuotaUnit(val factor: Long) {
    GB(1024L * 1024 * 1024),
    MB(1024L * 1024)
}

/** Prefill for the custom quota dialog from the user's current quota. */
private fun quotaPrefill(total: Long?): Pair<String, QuotaUnit>? {
    if (total == null || total <= 0) return null
    return if (total % GB == 0L) {
        (total / GB).toString() to QuotaUnit.GB
    } else if (total % MB == 0L) {
        (total / MB).toString() to QuotaUnit.MB
    } else {
        val mb = total.toDouble() / MB
        val text = if (mb % 1.0 == 0.0) mb.toLong().toString()
        else formatOneDecimal(mb)
        text to QuotaUnit.MB
    }
}

@Composable
private fun CustomQuotaDialog(
    user: ManagedUser,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val prefill = remember(user) { quotaPrefill(user.quota?.total) }
    var value by remember { mutableStateOf(prefill?.first ?: "") }
    var unit by remember { mutableStateOf(prefill?.second ?: QuotaUnit.GB) }
    var unitMenuOpen by remember { mutableStateOf(false) }
    val valueNum = value.toDoubleOrNull()
    val valid = valueNum != null && valueNum > 0

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.quota_custom_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { new ->
                        if (new.matches(Regex("[0-9]*\\.?[0-9]*"))) value = new
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text(stringResource(Res.string.quota_custom_value)) },
                    isError = value.isNotBlank() && !valid,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Box {
                    OutlinedButton(onClick = { unitMenuOpen = true }) {
                        Text(
                            if (unit == QuotaUnit.GB) stringResource(Res.string.quota_unit_gb)
                            else stringResource(Res.string.quota_unit_mb)
                        )
                    }
                    DropdownMenu(expanded = unitMenuOpen, onDismissRequest = { unitMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.quota_unit_gb)) },
                            onClick = {
                                unit = QuotaUnit.GB
                                unitMenuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.quota_unit_mb)) },
                            onClick = {
                                unit = QuotaUnit.MB
                                unitMenuOpen = false
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val num = valueNum
                    if (num != null && num > 0) onConfirm((num * unit.factor).roundToLong())
                },
                enabled = valid
            ) { Text(stringResource(Res.string.quota_set)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}
