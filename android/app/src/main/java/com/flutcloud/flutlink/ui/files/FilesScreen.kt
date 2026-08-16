package com.flutcloud.flutlink.ui.files

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.provider.OpenableColumns
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.data.FileOpener
import com.flutcloud.flutlink.data.dto.Share
import com.flutcloud.flutlink.data.dto.WebDavEntry
import com.flutcloud.flutlink.ui.components.EmptyState
import com.flutcloud.flutlink.ui.components.QuotaBar
import com.flutcloud.flutlink.ui.components.fileIcon
import com.flutcloud.flutlink.ui.flutLinkViewModel
import com.flutcloud.flutlink.ui.viewmodel.FilesViewModel

private const val ROOT = "/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(container: AppContainer) {
    val vm = flutLinkViewModel { FilesViewModel(it) }
    val context = LocalContext.current
    val path by vm.path.collectAsState()
    val entries by vm.entries.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val offline by vm.offline.collectAsState()
    val quota by vm.quota.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val searching by vm.searching.collectAsState()
    val downloaded by vm.downloaded.collectAsState()
    val lastShare by vm.lastShare.collectAsState()
    val shares by vm.shares.collectAsState()
    val sharesLoading by vm.sharesLoading.collectAsState()
    val sessionKey = vm.sessionKey

    var showSearch by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<WebDavEntry?>(null) }
    var shareTarget by remember { mutableStateOf<WebDavEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<WebDavEntry?>(null) }

    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(sessionKey) {
        if (sessionKey != null) vm.refresh()
    }
    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            vm.clearErrors()
        }
    }
    LaunchedEffect(downloaded) {
        downloaded?.let { path ->
            if (!FileOpener.open(context, path)) {
                snackbar.showSnackbar("Downloaded to $path (no app could open it)")
            }
            vm.clearErrors()
        }
    }
    LaunchedEffect(lastShare) {
        lastShare?.let {
            snackbar.showSnackbar("Link created: ${it.url ?: "no URL"}")
            vm.clearErrors()
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val name = context.displayName(it) ?: "upload.bin"
            val bytes = context.readAllBytes(it)
            vm.upload(path, name, bytes)
        }
    }

    Scaffold(
        topBar = {
            if (showSearch) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { vm.search(it) },
                    onClose = {
                        showSearch = false
                        vm.clearSearch()
                    }
                )
            } else {
                FilesTopBar(
                    path = path,
                    onBack = {
                        val parent = parentOf(path)
                        if (parent != null) vm.listFolder(parent)
                    },
                    canGoBack = path != ROOT,
                    onSearch = { showSearch = true }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                ExtendedFloatingActionButton(
                    onClick = { showNewFolder = true },
                    icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                    text = { Text("Folder") }
                )
                Spacer(Modifier.height(12.dp))
                ExtendedFloatingActionButton(
                    onClick = { uploadLauncher.launch(arrayOf("*/*")) },
                    icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                    text = { Text("Upload") }
                )
            }
        }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            QuotaBar(quota)
            if (offline) {
                OfflineBanner()
            }
            if (showSearch) {
                SearchResults(
                    results = searchResults,
                    searching = searching,
                    onOpen = { vm.open(it) }
                )
            } else if (loading && entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Cloud,
                    title = "This folder is empty",
                    hint = "Use Upload or the folder button to add files."
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.path }) { entry ->
                        EntryRow(
                            entry = entry,
                            onClick = { vm.open(entry) },
                            onRename = { renameTarget = entry },
                            onShare = { shareTarget = entry },
                            onDelete = { deleteTarget = entry },
                            onJumpToPaired = { entry.linkTarget?.let { vm.listFolder(it) } }
                        )
                    }
                }
            }
        }
    }

    if (showNewFolder) {
        NewFolderDialog(
            onDismiss = { showNewFolder = false },
            onCreate = { name ->
                showNewFolder = false
                vm.mkdir(name)
            }
        )
    }
    renameTarget?.let { target ->
        RenameDialog(
            initialName = target.name,
            onDismiss = { renameTarget = null },
            onRename = { name ->
                renameTarget = null
                vm.rename(target, name)
            }
        )
    }
    shareTarget?.let { target ->
        ShareDialog(
            entry = target,
            shares = shares,
            sharesLoading = sharesLoading,
            onLoadShares = { vm.loadShares(target) },
            onRevoke = { vm.deleteShare(it) },
            onDismiss = {
                shareTarget = null
                vm.resetShares()
            },
            onCreate = { password, expiry ->
                shareTarget = null
                vm.createPublicShare(target, password, expiry)
            }
        )
    }
    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            entry = target,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                vm.delete(target)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilesTopBar(
    path: String,
    onBack: () -> Unit,
    canGoBack: Boolean,
    onSearch: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                if (path == ROOT) "Files" else path,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (canGoBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        }
    )
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search all files…") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close search")
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SearchResults(
    results: List<WebDavEntry>,
    searching: Boolean,
    onOpen: (WebDavEntry) -> Unit
) {
    when {
        searching -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        results.isEmpty() -> EmptyState(
            icon = Icons.Default.Search,
            title = "No matches",
            hint = "Try a different search term."
        )
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(results, key = { it.path }) { entry ->
                EntryRow(
                    entry = entry,
                    onClick = { onOpen(entry) },
                    onRename = {},
                    onShare = {},
                    onDelete = {},
                    onJumpToPaired = {}
                )
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: WebDavEntry,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onJumpToPaired: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val (icon, tint) = fileIcon(entry)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.isVirtualLink) {
                    Spacer(Modifier.width(8.dp))
                    LinkBadge()
                }
            }
            com.flutcloud.flutlink.ui.components.FileMetaLine(entry)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Actions")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (entry.isVirtualLink && entry.linkTarget != null) {
                    DropdownMenuItem(
                        text = { Text("Jump to writable part") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onJumpToPaired()
                        }
                    )
                }
                if (entry.isDir) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Share link") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onShare()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
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

@Composable
private fun LinkBadge() {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            "virtual",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun NewFolderDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New folder") },
        text = {
            TextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Folder name") })
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun RenameDialog(initialName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            TextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("New name") })
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (name.isNotBlank()) onRename(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Rename") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ShareDialog(
    entry: WebDavEntry,
    shares: List<Share>,
    sharesLoading: Boolean,
    onLoadShares: () -> Unit,
    onRevoke: (Share) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (String?, String?) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    LaunchedEffect(entry.path) { onLoadShares() }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share — ${entry.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    entry.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Existing shares",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                when {
                    sharesLoading -> Text("Loading shares…", style = MaterialTheme.typography.bodySmall)
                    shares.isEmpty() -> Text(
                        "No shares yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> shares.forEach { share ->
                        ShareRow(share = share, onRevoke = { onRevoke(share) })
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "New public link",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                TextField(value = password, onValueChange = { password = it }, singleLine = true, label = { Text("Password (optional)") })
                Spacer(Modifier.height(8.dp))
                TextField(value = expiry, onValueChange = { expiry = it }, singleLine = true, label = { Text("Expiry YYYY-MM-DD (optional)") })
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                onCreate(password.ifBlank { null }, expiry.ifBlank { null })
            }) { Text("Create link") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun ShareRow(share: Share, onRevoke: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(shareLabel(share), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            shareTarget(share).takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val meta = listOfNotNull(
                share.hasPassword?.takeIf { it }?.let { "password" },
                share.expiration?.let { "expires $it" }
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onRevoke) { Text("Revoke") }
    }
}

private fun shareLabel(share: Share): String = when (share.shareType) {
    0 -> "User"
    1 -> "Group"
    3 -> "Public link"
    else -> "Share"
}

private fun shareTarget(share: Share): String =
    share.url ?: share.shareWithDisplayName ?: share.shareWith ?: ""

@Composable
private fun OfflineBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            "Offline — showing cached data",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun DeleteConfirmDialog(entry: WebDavEntry, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${entry.name}?") },
        text = {
            Text(
                if (entry.isDir) "The folder and all its contents will be deleted from the server."
                else "The file will be deleted from the server."
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun parentOf(path: String): String? {
    if (path == ROOT) return null
    val trimmed = path.trimEnd('/')
    val idx = trimmed.lastIndexOf('/')
    return if (idx <= 0) ROOT else trimmed.substring(0, idx)
}

private fun Context.displayName(uri: Uri): String? =
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
    }

private fun Context.readAllBytes(uri: Uri): ByteArray =
    contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)