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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flutcloud.flutlink.AppContainer
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
    val users by vm.users.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val search by vm.search.collectAsState()
    val hasMore by vm.hasMore.collectAsState()

    var showCreate by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.loadUsers() }
    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("User administration") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add user") }
            )
        }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            TextField(
                value = search,
                onValueChange = { vm.search.value = it },
                placeholder = { Text("Search users…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            when {
                loading && users.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                users.isEmpty() -> EmptyState(
                    icon = Icons.Default.Person,
                    title = "No users found",
                    hint = "Create a user or clear the search."
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(users, key = { it.id }) { user ->
                        UserRow(
                            user = user,
                            onToggleEnabled = { vm.setEnabled(user, !user.enabled) },
                            onDelete = { vm.deleteUser(user) },
                            onQuota = { quotaBytes -> vm.setQuota(user, quotaBytes) }
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
                Text("Load more")
            }
        }
    }
}

@Composable
private fun UserRow(
    user: ManagedUser,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    onQuota: (Long?) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
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
                        "disabled",
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
                            total == null || total <= 0 -> "unlimited"
                            else -> formatBytes(total)
                        }
                    } ?: "quota unknown",
                    user.email?.takeIf { it.isNotBlank() } ?: "no email"
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
                        Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Quota: unlimited") },
                            leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onQuota(null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Quota: 1 GB") },
                            leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onQuota(GB)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Quota: 5 GB") },
                            leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onQuota(5 * GB)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Quota: 10 GB") },
                            leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onQuota(10 * GB)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete user") },
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
        title = { Text("Create user") },
        text = {
            Column {
                TextField(value = userId, onValueChange = { userId = it }, singleLine = true, label = { Text("User ID") })
                Spacer(Modifier.height(8.dp))
                TextField(value = password, onValueChange = { password = it }, singleLine = true, label = { Text("Password") })
                Spacer(Modifier.height(8.dp))
                TextField(value = displayName, onValueChange = { displayName = it }, singleLine = true, label = { Text("Display name (optional)") })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(userId.trim(), password, displayName.trim().ifBlank { null }) },
                enabled = userId.isNotBlank() && password.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}