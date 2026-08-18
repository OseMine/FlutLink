package com.flutcloud.flutlink.ui.files

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.R
import com.flutcloud.flutlink.data.FileOpener
import com.flutcloud.flutlink.data.ShareSheet
import com.flutcloud.flutlink.data.dto.Share
import com.flutcloud.flutlink.data.dto.WebDavEntry
import com.flutcloud.flutlink.ui.components.EmptyState
import com.flutcloud.flutlink.ui.components.QuotaBar
import com.flutcloud.flutlink.ui.components.fileIcon
import com.flutcloud.flutlink.ui.flutLinkViewModel
import com.flutcloud.flutlink.ui.viewmodel.FilesViewModel
import kotlinx.coroutines.launch

private const val ROOT = "/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    container: AppContainer,
    impersonateTarget: String? = null,
    onImpersonationHandled: () -> Unit = {}
) {
    val vm = flutLinkViewModel { FilesViewModel(it) }
    val context = LocalContext.current
    val path by vm.path.collectAsState()
    val entries by vm.entries.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val offline by vm.offline.collectAsState()
    val quota by vm.quota.collectAsState()
    val targetUser by vm.targetUser.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val searching by vm.searching.collectAsState()
    val downloaded by vm.downloaded.collectAsState()
    val sharePath by vm.sharePath.collectAsState()
    val toast by vm.toast.collectAsState()
    val lastShare by vm.lastShare.collectAsState()
    val shares by vm.shares.collectAsState()
    val sharesLoading by vm.sharesLoading.collectAsState()
    val sessionKey = vm.sessionKey
    val pendingUpload by vm.pendingUpload.collectAsState()

    var showSearch by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<WebDavEntry?>(null) }
    var shareTarget by remember { mutableStateOf<WebDavEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<WebDavEntry?>(null) }
    var downloadTarget by remember { mutableStateOf<WebDavEntry?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val downloadPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        downloadTarget?.let { target ->
            if (granted) {
                vm.downloadToDownloads(target)
            } else {
                scope.launch {
                    snackbar.showSnackbar(context.getString(R.string.download_permission_denied))
                }
            }
        }
        downloadTarget = null
    }

    fun requestDownload(entry: WebDavEntry) {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            downloadTarget = entry
            downloadPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            vm.downloadToDownloads(entry)
        }
    }

    LaunchedEffect(sessionKey) {
        if (sessionKey != null) vm.refresh()
    }
    LaunchedEffect(impersonateTarget) {
        if (impersonateTarget != null) {
            vm.setTargetUser(impersonateTarget)
            onImpersonationHandled()
        }
    }
    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it.resolve(context))
            vm.clearErrors()
        }
    }
    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it.resolve(context))
            vm.clearErrors()
        }
    }
    LaunchedEffect(downloaded) {
        downloaded?.let { path ->
            if (!FileOpener.open(context, path)) {
                snackbar.showSnackbar(context.getString(R.string.downloaded_to_downloads, path))
            }
            vm.clearErrors()
        }
    }
    LaunchedEffect(sharePath) {
        sharePath?.let { path ->
            if (!ShareSheet.share(context, path)) {
                snackbar.showSnackbar(context.getString(R.string.share_failed, path))
            }
            vm.clearErrors()
        }
    }
    LaunchedEffect(lastShare) {
        lastShare?.let {
            val url = it.url ?: context.getString(R.string.no_url)
            snackbar.showSnackbar(context.getString(R.string.link_created, url))
            vm.clearErrors()
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val name = context.displayName(it) ?: "upload.bin"
            val mime = context.contentResolver.getType(it) ?: "application/octet-stream"
            vm.uploadStream(path, name, it, mime)
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
                    text = { Text(stringResource(R.string.folder)) }
                )
                Spacer(Modifier.height(12.dp))
                ExtendedFloatingActionButton(
                    onClick = { uploadLauncher.launch(arrayOf("*/*")) },
                    icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                    text = { Text(stringResource(R.string.upload)) }
                )
            }
        }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            QuotaBar(quota)
            val impersonated = targetUser
            if (impersonated != null) {
                ImpersonationBanner(
                    user = impersonated,
                    onStop = { vm.setTargetUser(null) }
                )
            }
            val progressState by vm.transferProgress.collectAsState()
            val progress = progressState
            if (progress != null) {
                val total = progress.total.coerceAtLeast(1L)
                LinearProgressIndicator(
                    progress = {
                        (progress.transferred.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (offline) {
                OfflineBanner()
            }
            if (showSearch) {
                SearchResults(
                    results = searchResults,
                    searching = searching,
                    onOpen = { vm.open(it) },
                    onDownload = { requestDownload(it) },
                    onShareFile = { vm.downloadAndShare(it) },
                    onRename = { renameTarget = it },
                    onShareLink = { shareTarget = it },
                    onDelete = { deleteTarget = it },
                    onJumpToPaired = { entry -> entry.linkTarget?.let { vm.listFolder(it) } }
                )
            } else if (loading && entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Cloud,
                    title = stringResource(R.string.folder_empty_title),
                    hint = stringResource(R.string.folder_empty_hint)
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.path }) { entry ->
                        EntryRow(
                            entry = entry,
                            onClick = { vm.open(entry) },
                            onDownload = { requestDownload(entry) },
                            onShareFile = { vm.downloadAndShare(entry) },
                            onRename = { renameTarget = entry },
                            onShareLink = { shareTarget = entry },
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
    pendingUpload?.let { upload ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.cancelUpload() },
            title = { Text(stringResource(R.string.overwrite)) },
            text = { Text(stringResource(R.string.file_exists_confirm, upload.name)) },
            confirmButton = {
                TextButton(onClick = { vm.confirmUpload() }) { Text(stringResource(R.string.overwrite)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelUpload() }) { Text(stringResource(R.string.cancel)) }
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
                if (path == ROOT) stringResource(R.string.tab_files) else path,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (canGoBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
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
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_search))
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
    onOpen: (WebDavEntry) -> Unit,
    onDownload: (WebDavEntry) -> Unit,
    onShareFile: (WebDavEntry) -> Unit,
    onRename: (WebDavEntry) -> Unit,
    onShareLink: (WebDavEntry) -> Unit,
    onDelete: (WebDavEntry) -> Unit,
    onJumpToPaired: (WebDavEntry) -> Unit
) {
    when {
        searching -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        results.isEmpty() -> EmptyState(
            icon = Icons.Default.Search,
            title = stringResource(R.string.no_matches),
            hint = stringResource(R.string.no_matches_hint)
        )
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(results, key = { it.path }) { entry ->
                EntryRow(
                    entry = entry,
                    onClick = { onOpen(entry) },
                    onDownload = { onDownload(entry) },
                    onShareFile = { onShareFile(entry) },
                    onRename = { onRename(entry) },
                    onShareLink = { onShareLink(entry) },
                    onDelete = { onDelete(entry) },
                    onJumpToPaired = { onJumpToPaired(entry) }
                )
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: WebDavEntry,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onShareFile: () -> Unit,
    onRename: () -> Unit,
    onShareLink: () -> Unit,
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
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.actions))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (entry.isVirtualLink && entry.linkTarget != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.jump_to_writable_part)) },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onJumpToPaired()
                        }
                    )
                }
                if (!entry.isDir) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.download)) },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDownload()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onShareFile()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename)) },
                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share_link)) },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onShareLink()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
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
            stringResource(R.string.virtual),
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
        title = { Text(stringResource(R.string.new_folder)) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.folder_name)) }
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun RenameDialog(initialName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename)) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.new_name)) }
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (name.isNotBlank()) onRename(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.rename)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
        title = { Text(stringResource(R.string.share_link)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    entry.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.share_existing_shares),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                when {
                    sharesLoading -> Text(stringResource(R.string.share_loading_shares), style = MaterialTheme.typography.bodySmall)
                    shares.isEmpty() -> Text(
                        stringResource(R.string.share_no_shares_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> shares.forEach { share ->
                        ShareRow(share = share, onRevoke = { onRevoke(share) })
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.share_new_public_link),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                TextField(value = password, onValueChange = { password = it }, singleLine = true, label = { Text(stringResource(R.string.share_password_optional)) })
                Spacer(Modifier.height(8.dp))
                TextField(value = expiry, onValueChange = { expiry = it }, singleLine = true, label = { Text(stringResource(R.string.share_expiry_optional)) })
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                onCreate(password.ifBlank { null }, expiry.ifBlank { null })
            }) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
                share.hasPassword?.takeIf { it }?.let { stringResource(R.string.share_meta_has_password) },
                share.expiration?.let { stringResource(R.string.share_meta_expires, it) }
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onRevoke) { Text(stringResource(R.string.share_revoke)) }
    }
}

@Composable
private fun shareLabel(share: Share): String = when (share.shareType) {
    0 -> stringResource(R.string.share_type_user)
    1 -> stringResource(R.string.share_type_group)
    3 -> stringResource(R.string.share_type_public_link)
    else -> stringResource(R.string.share_type_generic)
}

private fun shareTarget(share: Share): String =
    share.url ?: share.shareWithDisplayName ?: share.shareWith ?: ""

@Composable
private fun ImpersonationBanner(user: String, onStop: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.impersonation_notice, "@$user"),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onStop) {
                Text(stringResource(R.string.stop_impersonation))
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            stringResource(R.string.files_offline_banner),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun DeleteConfirmDialog(entry: WebDavEntry, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_confirm, entry.name)) },
        text = {
            Text(
                if (entry.isDir) stringResource(R.string.delete_folder_confirm)
                else stringResource(R.string.delete_file_confirm)
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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