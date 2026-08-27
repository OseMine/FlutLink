package com.flutcloud.flutlink.ui.guest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.ui.components.ErrorBanner
import com.flutcloud.flutlink.ui.components.FlutBadge
import com.flutcloud.flutlink.ui.components.FlutGhostButton
import com.flutcloud.flutlink.ui.components.FlutOutlineButton
import com.flutcloud.flutlink.ui.components.FlutPill
import com.flutcloud.flutlink.ui.components.FlutPrimaryButton
import com.flutcloud.flutlink.ui.flutLinkViewModel
import com.flutcloud.flutlink.ui.format.formatBytes
import com.flutcloud.flutlink.ui.viewmodel.GuestViewModel
import org.jetbrains.compose.resources.stringResource
import com.flutcloud.flutlink.resources.Res
import com.flutcloud.flutlink.resources.cancel
import com.flutcloud.flutlink.resources.create
import com.flutcloud.flutlink.resources.delete
import com.flutcloud.flutlink.resources.guest_admin_assign_category
import com.flutcloud.flutlink.resources.guest_admin_categories
import com.flutcloud.flutlink.resources.guest_admin_category_name
import com.flutcloud.flutlink.resources.guest_admin_delete_category
import com.flutcloud.flutlink.resources.guest_admin_delete_category_confirm
import com.flutcloud.flutlink.resources.guest_admin_lock
import com.flutcloud.flutlink.resources.guest_admin_new_category
import com.flutcloud.flutlink.resources.guest_admin_prefixless
import com.flutcloud.flutlink.resources.guest_admin_prefixless_hint
import com.flutcloud.flutlink.resources.guest_admin_remove_category
import com.flutcloud.flutlink.resources.guest_admin_unlock
import com.flutcloud.flutlink.resources.guest_all
import com.flutcloud.flutlink.resources.guest_empty
import com.flutcloud.flutlink.resources.guest_read_only_hint
import com.flutcloud.flutlink.resources.guest_title
import com.flutcloud.flutlink.resources.retry
import com.flutcloud.flutlink.resources.sign_in

/**
 * Guest mode: bundled, strictly read-only view of every completely public
 * shared folder — no login required (desktop `GuestBrowser.vue` parity).
 * When signed in as admin, exposes category and lock management.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestScreen(
    container: AppContainer,
    onExit: () -> Unit,
    onSignIn: () -> Unit
) {
    val vm = flutLinkViewModel { GuestViewModel(it) }
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val shares by vm.shares.collectAsState()
    val activeShare by vm.activeShare.collectAsState()
    val path by vm.path.collectAsState()
    val entries by vm.entries.collectAsState()
    val toast by vm.toast.collectAsState()
    val isAdmin by vm.isAdmin.collectAsState()
    val allCategories by vm.allCategories.collectAsState()

    var categoryFilter by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    // Admin dialogs
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var showDeleteCategoryDialog by remember { mutableStateOf<String?>(null) }
    var showAssignCategoryDialog by remember { mutableStateOf<String?>(null) } // token

    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it.resolveSuspend())
            vm.consumeToast()
        }
    }

    val categories = remember(shares) { shares.mapNotNull { it.category }.distinct() }
    val visibleShares = remember(shares, categoryFilter) {
        if (categoryFilter == null) shares else shares.filter { it.category == categoryFilter }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        activeShare?.name ?: stringResource(Res.string.guest_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (activeShare != null) vm.leave() else onExit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    FlutGhostButton(onClick = onSignIn) {
                        Text(stringResource(Res.string.sign_in))
                    }
                }
            )

            Text(
                stringResource(Res.string.guest_read_only_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            error?.let { err ->
                ErrorBanner(err.resolve(), Modifier.padding(horizontal = 16.dp))
                if (!loading && shares.isEmpty()) {
                    FlutGhostButton(onClick = { vm.load() }, modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(Res.string.retry))
                    }
                }
            }

            when {
                loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                activeShare == null -> Column(Modifier.fillMaxSize()) {
                    // Admin: category management bar.
                    if (isAdmin) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(Res.string.guest_admin_categories),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight(600)
                            )
                            IconButton(onClick = { showCreateCategoryDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.guest_admin_new_category))
                            }
                        }
                        // Existing category chips with delete.
                        if (allCategories.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (cat in allCategories) {
                                    FlutPill(
                                        text = cat,
                                        selected = false,
                                        onClick = { showDeleteCategoryDialog = cat }
                                    )
                                }
                            }
                        }
                    }

                    // Category filter (desktop-style pills)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FlutPill(
                            text = stringResource(Res.string.guest_all),
                            selected = categoryFilter == null,
                            onClick = { categoryFilter = null }
                        )
                        for (category in categories) {
                            FlutPill(
                                text = category,
                                selected = categoryFilter == category,
                                onClick = { categoryFilter = category }
                            )
                        }
                    }

                    if (visibleShares.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(Res.string.guest_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(visibleShares, key = { it.token }) { share ->
                                // Desktop-style card
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { vm.enter(share) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(share.name, style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                share.ownerDisplay ?: share.owner,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        share.category?.let {
                                            Spacer(Modifier.width(8.dp))
                                            FlutBadge(text = it)
                                        }
                                        // Admin: assign-category button.
                                        if (isAdmin) {
                                            Spacer(Modifier.width(8.dp))
                                            IconButton(onClick = { showAssignCategoryDialog = share.token }) {
                                                Icon(Icons.Default.Add, contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                ) {
                    items(entries, key = { it.path }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (entry.isDir) vm.navigateTo(entry.path)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!entry.isDir) {
                                    Text(
                                        formatBytes(entry.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!entry.isDir) {
                                IconButton(onClick = { vm.download(entry) }) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                }
                            } else if (isAdmin) {
                                // Admin: lock/unlock toggle for directories.
                                val token = activeShare?.token ?: ""
                                IconButton(onClick = { vm.lockPath(token, entry.path) }) {
                                    Icon(Icons.Default.Lock, contentDescription = stringResource(Res.string.guest_admin_lock))
                                }
                                IconButton(onClick = { vm.unlockPath(token, entry.path) }) {
                                    Icon(Icons.Default.LockOpen, contentDescription = stringResource(Res.string.guest_admin_unlock))
                                }
                            } else {
                                Spacer(Modifier.width(48.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // --- Admin dialogs ------------------------------------------------------

    if (showCreateCategoryDialog) {
        CreateCategoryDialog(
            onConfirm = { name, prefixless ->
                vm.createCategory(name, prefixless)
                showCreateCategoryDialog = false
            },
            onDismiss = { showCreateCategoryDialog = false }
        )
    }

    showDeleteCategoryDialog?.let { cat ->
        DeleteCategoryDialog(
            name = cat,
            onConfirm = {
                vm.deleteCategory(cat)
                showDeleteCategoryDialog = null
            },
            onDismiss = { showDeleteCategoryDialog = null }
        )
    }

    showAssignCategoryDialog?.let { token ->
        AssignCategoryDialog(
            categories = allCategories,
            onAssign = { category ->
                vm.assignCategory(token, category)
                showAssignCategoryDialog = null
            },
            onRemove = {
                vm.unassignCategory(token)
                showAssignCategoryDialog = null
            },
            onDismiss = { showAssignCategoryDialog = null }
        )
    }
}

@Composable
private fun CreateCategoryDialog(
    onConfirm: (name: String, prefixless: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var prefixless by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.guest_admin_new_category)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.guest_admin_category_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = prefixless, onCheckedChange = { prefixless = it })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(Res.string.guest_admin_prefixless))
                        Text(
                            stringResource(Res.string.guest_admin_prefixless_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            FlutPrimaryButton(onClick = { onConfirm(name.trim(), prefixless) },
                enabled = name.isNotBlank()) {
                Text(stringResource(Res.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}

@Composable
private fun DeleteCategoryDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.guest_admin_delete_category)) },
        text = { Text(stringResource(Res.string.guest_admin_delete_category_confirm, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}

@Composable
private fun AssignCategoryDialog(
    categories: List<String>,
    onAssign: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.guest_admin_assign_category)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (categories.isEmpty()) {
                    Text(
                        stringResource(Res.string.guest_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    for (cat in categories) {
                        FlutOutlineButton(
                            onClick = { onAssign(cat) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(cat) }
                    }
                }
                FlutOutlineButton(
                    onClick = onRemove,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(Res.string.guest_admin_remove_category)) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}
