package com.flutcloud.flutlink.ui.admin

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.R
import com.flutcloud.flutlink.data.dto.ManagedUser
import com.flutcloud.flutlink.ui.components.EmptyState
import com.flutcloud.flutlink.ui.flutLinkViewModel
import com.flutcloud.flutlink.ui.format.formatBytes
import com.flutcloud.flutlink.ui.viewmodel.AdminViewModel

private const val GB = 1024L * 1024 * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(container: AppContainer) {
    val vm = flutLinkViewModel { AdminViewModel(it) }
    val context = LocalContext.current
    val users by vm.users.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val search by vm.search.collectAsState()
    val hasMore by vm.hasMore.collectAsState()

    var showCreate by remember { mutableStateOf(false) }
    var groupTarget by remember { mutableStateOf<ManagedUser?>(null) }
    var deleteTarget by remember { mutableStateOf<ManagedUser?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.loadUsers() }
    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it.resolve(context))
            vm.clearError()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.user_administration)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_user)) }
            )
        }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            TextField(
                value = search,
                onValueChange = { vm.search.value = it },
                placeholder = { Text(stringResource(R.string.search_users)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            when {
                loading && users.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                users.isEmpty() -> EmptyState(
                    icon = Icons.Default.Person,
                    title = stringResource(R.string.no_users_found),
                    hint = stringResource(R.string.no_users_hint)
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(users, key = { it.id }) { user ->
                        UserRow(
                            user = user,
                            onToggleEnabled = { vm.setEnabled(user, !user.enabled) },
                            onDelete = { deleteTarget = user },
                            onQuota = { quotaBytes -> vm.setQuota(user, quotaBytes) },
                            onManageGroups = { groupTarget = user }
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
            Button(onClick = onClick) {
                Text(stringResource(R.string.admin_load_more))
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
    onManageGroups: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val resources = LocalContext.current.resources
    ListItem(
        headlineContent = {
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
                    Text(
                        stringResource(R.string.disabled),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        supportingContent = {
            Text(
                listOf(
                    "@${user.id}",
                    user.quota?.let { q ->
                        val used = q.used
                        val total = q.total
                        when {
                            used != null && total != null && total > 0 ->
                                "${formatBytes(used)} / ${formatBytes(total)}"
                            total == null || total <= 0 -> resources.getString(R.string.unlimited)
                            else -> formatBytes(total)
                        }
                    } ?: resources.getString(R.string.quota_unknown),
                    user.email?.takeIf { it.isNotBlank() } ?: resources.getString(R.string.no_email)
                ).joinToString(" · ")
            )
        },
        leadingContent = {
            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = user.enabled, onCheckedChange = { onToggleEnabled() })
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.actions))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.quota_unlimited)) },
                            leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onQuota(null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.quota_1gb)) },
                            leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onQuota(GB)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.quota_5gb)) },
                            leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onQuota(5 * GB)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.quota_10gb)) },
                            leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onQuota(10 * GB)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.admin_manage_groups)) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onManageGroups()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_user)) },
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
    )
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
        title = { Text(stringResource(R.string.create_user)) },
        text = {
            Column {
                TextField(value = userId, onValueChange = { userId = it }, singleLine = true, label = { Text(stringResource(R.string.user_id)) })
                Spacer(Modifier.height(8.dp))
                TextField(value = password, onValueChange = { password = it }, singleLine = true, label = { Text(stringResource(R.string.password)) })
                Spacer(Modifier.height(8.dp))
                TextField(value = displayName, onValueChange = { displayName = it }, singleLine = true, label = { Text(stringResource(R.string.display_name_optional)) })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(userId.trim(), password, displayName.trim().ifBlank { null }) },
                enabled = userId.isNotBlank() && password.isNotBlank()
            ) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
        title = { Text(stringResource(R.string.admin_groups_title, user.displayName ?: user.id)) },
        text = {
            Column {
                Text("@${user.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                if (user.groups.isEmpty()) {
                    Text(
                        stringResource(R.string.admin_no_groups),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    user.groups.forEach { group ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    group,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            TextButton(onClick = { onRemoveFromGroup(group) }) { Text(stringResource(R.string.remove)) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = groupInput,
                    onValueChange = { groupInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.admin_group_name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.admin_groups_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = { onCreateGroup(groupInput.trim()) },
                    enabled = groupInput.isNotBlank()
                ) { Text(stringResource(R.string.admin_create_group)) }
                TextButton(
                    onClick = {
                        onAddToGroup(groupInput.trim())
                        groupInput = ""
                    },
                    enabled = groupInput.isNotBlank()
                ) { Text(stringResource(R.string.admin_add_to_group)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
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
        title = { Text(stringResource(R.string.delete_user)) },
        text = {
            Text(stringResource(R.string.delete_user_confirm, user.displayName ?: user.id))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}