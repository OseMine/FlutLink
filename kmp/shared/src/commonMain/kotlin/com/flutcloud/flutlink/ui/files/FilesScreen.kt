package com.flutcloud.flutlink.ui.files

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.data.dto.Share
import com.flutcloud.flutlink.data.dto.WebDavEntry
import okio.Path.Companion.toPath
import com.flutcloud.flutlink.ui.components.Breadcrumb
import com.flutcloud.flutlink.ui.components.EmptyState
import com.flutcloud.flutlink.ui.components.QuotaBar
import com.flutcloud.flutlink.ui.components.fileIcon
import com.flutcloud.flutlink.ui.components.FileMetaLine
import com.flutcloud.flutlink.ui.flutLinkViewModel
import com.flutcloud.flutlink.ui.format.formatBytes
import com.flutcloud.flutlink.ui.rememberDownloadsPermissionRequester
import com.flutcloud.flutlink.ui.rememberFilePickLauncher
import com.flutcloud.flutlink.ui.viewmodel.FilesViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.flutcloud.flutlink.resources.Res
import com.flutcloud.flutlink.resources.actions
import com.flutcloud.flutlink.resources.back
import com.flutcloud.flutlink.resources.bulk_delete_confirm
import com.flutcloud.flutlink.resources.bulk_selected_count
import com.flutcloud.flutlink.resources.cancel
import com.flutcloud.flutlink.resources.close_search
import com.flutcloud.flutlink.resources.create
import com.flutcloud.flutlink.resources.delete
import com.flutcloud.flutlink.resources.delete_confirm
import com.flutcloud.flutlink.resources.delete_file_confirm
import com.flutcloud.flutlink.resources.delete_folder_confirm
import com.flutcloud.flutlink.resources.download
import com.flutcloud.flutlink.resources.download_permission_denied
import com.flutcloud.flutlink.resources.downloaded_to_downloads
import com.flutcloud.flutlink.resources.download_zip
import com.flutcloud.flutlink.resources.file_exists_confirm
import com.flutcloud.flutlink.resources.files_offline_banner
import com.flutcloud.flutlink.resources.folder
import com.flutcloud.flutlink.resources.folder_empty_hint
import com.flutcloud.flutlink.resources.folder_empty_title
import com.flutcloud.flutlink.resources.folder_name
import com.flutcloud.flutlink.resources.impersonation_notice
import com.flutcloud.flutlink.resources.jump_to_writable_part
import com.flutcloud.flutlink.resources.link_created
import com.flutcloud.flutlink.resources.new_folder
import com.flutcloud.flutlink.resources.new_name
import com.flutcloud.flutlink.resources.new_share
import com.flutcloud.flutlink.resources.no_matches
import com.flutcloud.flutlink.resources.no_matches_hint
import com.flutcloud.flutlink.resources.no_url
import com.flutcloud.flutlink.resources.overwrite
import com.flutcloud.flutlink.resources.rename
import com.flutcloud.flutlink.resources.search
import com.flutcloud.flutlink.resources.search_placeholder
import com.flutcloud.flutlink.resources.share
import com.flutcloud.flutlink.resources.share_existing_shares
import com.flutcloud.flutlink.resources.share_expiry_optional
import com.flutcloud.flutlink.resources.share_failed
import com.flutcloud.flutlink.resources.share_link
import com.flutcloud.flutlink.resources.share_loading_shares
import com.flutcloud.flutlink.resources.share_meta_expires
import com.flutcloud.flutlink.resources.share_meta_has_password
import com.flutcloud.flutlink.resources.share_no_shares_yet
import com.flutcloud.flutlink.resources.share_password_optional
import com.flutcloud.flutlink.resources.share_public_upload
import com.flutcloud.flutlink.resources.share_recipient
import com.flutcloud.flutlink.resources.share_revoke
import com.flutcloud.flutlink.resources.share_type_generic
import com.flutcloud.flutlink.resources.share_type_group
import com.flutcloud.flutlink.resources.share_type_public_link
import com.flutcloud.flutlink.resources.share_type_user
import com.flutcloud.flutlink.resources.stop_impersonation
import com.flutcloud.flutlink.resources.tab_files
import com.flutcloud.flutlink.resources.upload
import com.flutcloud.flutlink.resources.view_grid
import com.flutcloud.flutlink.resources.view_list
import com.flutcloud.flutlink.resources.virtual


internal const val ROOT = "/"

/** View mode for file listing (list vs grid). Labels are localized at the
 *  point of use (`view_list`/`view_grid`), not hard-coded (KMP-F12). */
private enum class ViewMode(val icon: ImageVector) {
    List(Icons.AutoMirrored.Filled.List),
    Grid(Icons.Default.GridView)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    container: AppContainer,
    impersonateTarget: String? = null,
    onImpersonationHandled: () -> Unit = {}
) {
    val vm = flutLinkViewModel { FilesViewModel(it) }
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
    val selected by vm.selected.collectAsState()
    val previews by vm.previews.collectAsState()

    var showSearch by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<WebDavEntry?>(null) }
    var shareTarget by remember { mutableStateOf<WebDavEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<WebDavEntry?>(null) }
    var pendingDownload by remember { mutableStateOf<WebDavEntry?>(null) }
    var pendingZipDownload by remember { mutableStateOf<WebDavEntry?>(null) }
    var bulkDeleteConfirm by remember { mutableStateOf(false) }
    // KMP-F10: persist the view mode so it survives tab switches and app
    // restarts (mirrors the desktop `filesView`). Initialised from the store
    // and written back on every change.
    val savedViewMode = container.settingsStore.filesViewModeSnapshot()
    var viewMode by remember {
        mutableStateOf(if (savedViewMode == "grid") ViewMode.Grid else ViewMode.List)
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val downloadsPermissionDeniedText = stringResource(Res.string.download_permission_denied)

    // Downloads permission gate: null when no runtime permission is needed.
    val downloadsPermission = rememberDownloadsPermissionRequester { granted ->
        val target = pendingDownload
        val zipTarget = pendingZipDownload
        pendingDownload = null
        pendingZipDownload = null
        when {
            !granted -> scope.launch { snackbar.showSnackbar(downloadsPermissionDeniedText) }
            target != null -> vm.downloadToDownloads(target)
            zipTarget != null -> vm.downloadFolderZip(zipTarget)
        }
    }

    fun requestDownload(entry: WebDavEntry) {
        if (downloadsPermission == null) {
            vm.downloadToDownloads(entry)
        } else {
            pendingDownload = entry
            downloadsPermission.invoke()
        }
    }

    fun requestFolderZip(entry: WebDavEntry) {
        if (downloadsPermission == null) {
            vm.downloadFolderZip(entry)
        } else {
            pendingZipDownload = entry
            downloadsPermission.invoke()
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
            snackbar.showSnackbar(it.resolveSuspend())
            vm.clearError()
        }
    }
    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it.resolveSuspend())
            vm.clearToast()
        }
    }
    val downloadedText = downloaded?.let { stringResource(Res.string.downloaded_to_downloads, it) }
    LaunchedEffect(downloaded) {
        downloaded?.let { p ->
            if (!container.platform.openFile(toOkioPath(p))) {
                snackbar.showSnackbar(downloadedText.orEmpty())
            }
            vm.clearDownloaded()
        }
    }
    val shareFailedText = sharePath?.let { stringResource(Res.string.share_failed, it) }
    LaunchedEffect(sharePath) {
        sharePath?.let { p ->
            if (!container.platform.shareFile(toOkioPath(p))) {
                snackbar.showSnackbar(shareFailedText.orEmpty())
            }
            vm.clearSharePath()
        }
    }
    val noUrlText = stringResource(Res.string.no_url)
    val linkCreatedText = lastShare?.let {
        stringResource(Res.string.link_created, it.url ?: noUrlText)
    }
    LaunchedEffect(lastShare) {
        lastShare?.let {
            snackbar.showSnackbar(linkCreatedText.orEmpty())
            vm.clearLastShare()
        }
    }

    val uploadLauncher = rememberFilePickLauncher { picked ->
        picked?.let { vm.uploadPicked(path, it) }
    }

    // Preview thumbnails for image files of the current listing (CP-N3).
    LaunchedEffect(showSearch, entries) {
        if (!showSearch && entries.isNotEmpty()) vm.loadPreviews(entries)
    }

    Scaffold(
        topBar = {
            when {
                showSearch -> SearchBar(
                    query = searchQuery,
                    onQueryChange = { vm.search(it) },
                    onClose = {
                        showSearch = false
                        vm.clearSearch()
                    }
                )
                selected.isNotEmpty() -> SelectionTopBar(
                    count = selected.size,
                    onClose = { vm.clearSelection() },
                    onDelete = { bulkDeleteConfirm = true }
                )
                else -> FilesTopBar(
                    path = path,
                    onBack = {
                        val parent = parentOf(path)
                        if (parent != null) vm.listFolder(parent)
                    },
                    canGoBack = path != ROOT,
                    onSearch = { showSearch = true },
                    viewMode = viewMode,
                    onViewModeChange = { mode ->
                        viewMode = mode
                        scope.launch {
                            container.settingsStore.setFilesViewMode(
                                if (mode == ViewMode.Grid) "grid" else "list"
                            )
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (selected.isEmpty()) {
                Column(horizontalAlignment = Alignment.End) {
                    ExtendedFloatingActionButton(
                        onClick = { showNewFolder = true },
                        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                        text = { Text(stringResource(Res.string.folder)) }
                    )
                    Spacer(Modifier.height(12.dp))
                    ExtendedFloatingActionButton(
                        onClick = { uploadLauncher() },
                        icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                        text = { Text(stringResource(Res.string.upload)) }
                    )
                }
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

            // Breadcrumbs
            if (path != ROOT) {
                Breadcrumb(
                    segments = buildBreadcrumbSegments(stringResource(Res.string.tab_files), path) {
                        vm.listFolder(it)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                    title = stringResource(Res.string.folder_empty_title),
                    hint = stringResource(Res.string.folder_empty_hint)
                )
            } else {
                when (viewMode) {
                    ViewMode.List -> LazyColumn(Modifier.fillMaxSize()) {
                        items(entries, key = { it.path }) { entry ->
                            EntryRow(
                                entry = entry,
                                preview = if (!entry.isDir) previews[entry.path] else null,
                                selected = entry.path in selected,
                                selectionMode = selected.isNotEmpty(),
                                onClick = { vm.open(entry) },
                                onToggleSelect = { vm.toggleSelected(entry.path) },
                                onDownload = { requestDownload(entry) },
                                onDownloadZip = { requestFolderZip(entry) },
                                onShareFile = { vm.downloadAndShare(entry) },
                                onRename = { renameTarget = entry },
                                onShareLink = { shareTarget = entry },
                                onDelete = { deleteTarget = entry },
                                onJumpToPaired = { entry.linkTarget?.let { vm.listFolder(it) } }
                            )
                        }
                    }
                    ViewMode.Grid -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(entries, key = { it.path }) { entry ->
                            EntryGridItem(
                                entry = entry,
                                preview = if (!entry.isDir) previews[entry.path] else null,
                                selected = entry.path in selected,
                                selectionMode = selected.isNotEmpty(),
                                onClick = { vm.open(entry) },
                                onToggleSelect = { vm.toggleSelected(entry.path) },
                                onLongClick = { if (!entry.isVirtualLink) vm.toggleSelected(entry.path) },
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
            onCreate = { shareType, shareWith, password, expiry, publicUpload ->
                shareTarget = null
                vm.createShare(target, shareType, shareWith, password, expiry, publicUpload)
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
    if (bulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { bulkDeleteConfirm = false },
            title = { Text(stringResource(Res.string.bulk_delete_confirm, selected.size)) },
            text = { Text(stringResource(Res.string.delete_folder_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    bulkDeleteConfirm = false
                    vm.deleteMany(entries.filter { it.path in selected })
                }) { Text(stringResource(Res.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { bulkDeleteConfirm = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
    pendingUpload?.let { upload ->
        AlertDialog(
            onDismissRequest = { vm.clearPendingUpload() },
            title = { Text(stringResource(Res.string.overwrite)) },
            text = { Text(stringResource(Res.string.file_exists_confirm, upload.name)) },
            confirmButton = {
                TextButton(onClick = { vm.confirmUpload() }) { Text(stringResource(Res.string.overwrite)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.clearPendingUpload() }) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }
}

/** Convert a platform-provided file path string back into an okio [okio.Path]. */
private fun toOkioPath(path: String): okio.Path = path.toPath()

/** Build breadcrumb segments from a path. Root label is localized by the
 *  caller (KMP-F12) instead of being a hard-coded literal. */
private fun buildBreadcrumbSegments(
    rootLabel: String,
    path: String,
    onNavigate: (String) -> Unit
): List<Pair<String, () -> Unit>> {
    if (path == ROOT) return emptyList()
    val segments = mutableListOf<Pair<String, () -> Unit>>()
    segments.add(rootLabel to { onNavigate(ROOT) })
    val parts = path.trim('/').split('/')
    var accumulated = ""
    for (part in parts) {
        accumulated += "/$part"
        val capturedPath = accumulated
        segments.add(part to { onNavigate(capturedPath) })
    }
    return segments
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilesTopBar(
    path: String,
    onBack: () -> Unit,
    canGoBack: Boolean,
    onSearch: () -> Unit,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit
) {
    TopAppBar(
        title = {
            Text(
                if (path == ROOT) stringResource(Res.string.tab_files) else path,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (canGoBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                }
            }
        },
        actions = {
            // View mode toggle (M3 segmented control)
            SingleChoiceSegmentedButtonRow(Modifier.padding(end = 8.dp)) {
                ViewMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = viewMode == mode,
                        onClick = { onViewModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ViewMode.entries.size)
                    ) {
                        Icon(
                            mode.icon,
                            contentDescription = stringResource(
                                if (mode == ViewMode.List) Res.string.view_list
                                else Res.string.view_grid
                            ),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.search))
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
        placeholder = { Text(stringResource(Res.string.search_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.close_search))
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
            title = stringResource(Res.string.no_matches),
            hint = stringResource(Res.string.no_matches_hint)
        )
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(results, key = { it.path }) { entry ->
                EntryRow(
                    entry = entry,
                    onClick = { onOpen(entry) },
                    onToggleSelect = {},
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, onClose: () -> Unit, onDelete: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(Res.string.bulk_selected_count, count)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.cancel))
            }
        },
        actions = {
            IconButton(onClick = onDelete, enabled = count > 0) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.delete))
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EntryRow(
    entry: WebDavEntry,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onDownload: () -> Unit,
    onShareFile: () -> Unit,
    onRename: () -> Unit,
    onShareLink: () -> Unit,
    onDelete: () -> Unit,
    onJumpToPaired: () -> Unit,
    onDownloadZip: (() -> Unit)? = null,
    preview: ImageBitmap? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false
) {
    var menuOpen by remember { mutableStateOf(false) }
    val (icon, tint) = fileIcon(entry)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onClick() },
                onLongClick = { if (!entry.isVirtualLink) onToggleSelect() }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (preview != null && !entry.isDir) {
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            }
        }
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
            FileMetaLine(entry)
        }
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
            Spacer(Modifier.width(2.dp))
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.actions))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (entry.isVirtualLink && entry.linkTarget != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.jump_to_writable_part)) },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onJumpToPaired()
                        }
                    )
                }
                if (entry.isDir && onDownloadZip != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.download_zip)) },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDownloadZip()
                        }
                    )
                }
                if (!entry.isDir) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.download)) },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDownload()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.share)) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onShareFile()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.rename)) },
                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.share_link)) },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onShareLink()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.delete)) },
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

/** Grid item for file listing. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryGridItem(
    entry: WebDavEntry,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onDownload: () -> Unit,
    onShareFile: () -> Unit,
    onRename: () -> Unit,
    onShareLink: () -> Unit,
    onDelete: () -> Unit,
    onJumpToPaired: () -> Unit,
    preview: ImageBitmap? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: () -> Unit = {}
) {
    var menuOpen by remember { mutableStateOf(false) }
    val (icon, tint) = fileIcon(entry)

    Surface(
        modifier = Modifier
            .padding(4.dp)
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onClick() },
                onLongClick = onLongClick
            ),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon or preview
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                if (preview != null && !entry.isDir) {
                    Image(
                        bitmap = preview,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Name
            Text(
                entry.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            // Size
            if (!entry.isDir) {
                entry.size?.let { size ->
                    Text(
                        formatBytes(size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Virtual link badge
            if (entry.isVirtualLink) {
                Spacer(Modifier.height(2.dp))
                LinkBadge()
            }
        }
    }
}

@Composable
private fun LinkBadge() {
    AssistChip(
        onClick = {},
        label = { Text(stringResource(Res.string.virtual)) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
            )
        }
    )
}

@Composable
private fun NewFolderDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.new_folder)) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(Res.string.folder_name)) }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(Res.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}

@Composable
private fun RenameDialog(initialName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.rename)) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(Res.string.new_name)) }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onRename(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(Res.string.rename)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
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
    onCreate: (shareType: Int, shareWith: String?, password: String?, expiry: String?, publicUpload: Boolean) -> Unit
) {
    var shareType by remember { mutableStateOf(3) }
    var shareWith by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var publicUpload by remember { mutableStateOf(false) }
    LaunchedEffect(entry.path) { onLoadShares() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.share)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    entry.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Res.string.share_existing_shares),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                when {
                    sharesLoading -> Text(stringResource(Res.string.share_loading_shares), style = MaterialTheme.typography.bodySmall)
                    shares.isEmpty() -> Text(
                        stringResource(Res.string.share_no_shares_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> shares.forEach { share ->
                        ShareRow(share = share, onRevoke = { onRevoke(share) })
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(Res.string.new_share),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = shareType == 3,
                        onClick = { shareType = 3 },
                        label = { Text(stringResource(Res.string.share_type_public_link)) }
                    )
                    FilterChip(
                        selected = shareType == 0,
                        onClick = { shareType = 0 },
                        label = { Text(stringResource(Res.string.share_type_user)) }
                    )
                    FilterChip(
                        selected = shareType == 1,
                        onClick = { shareType = 1 },
                        label = { Text(stringResource(Res.string.share_type_group)) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (shareType < 3) {
                    TextField(
                        value = shareWith,
                        onValueChange = { shareWith = it },
                        singleLine = true,
                        label = { Text(stringResource(Res.string.share_recipient)) }
                    )
                } else {
                    TextField(value = password, onValueChange = { password = it }, singleLine = true, label = { Text(stringResource(Res.string.share_password_optional)) })
                    Spacer(Modifier.height(8.dp))
                    TextField(value = expiry, onValueChange = { expiry = it }, singleLine = true, label = { Text(stringResource(Res.string.share_expiry_optional)) })
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = publicUpload, onCheckedChange = { publicUpload = it })
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.share_public_upload), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onCreate(shareType, shareWith, password.ifBlank { null }, expiry.ifBlank { null }, publicUpload)
            }) { Text(stringResource(Res.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
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
                share.hasPassword?.takeIf { it }?.let { stringResource(Res.string.share_meta_has_password) },
                share.expiration?.let { stringResource(Res.string.share_meta_expires, it) }
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onRevoke) { Text(stringResource(Res.string.share_revoke)) }
    }
}

@Composable
private fun shareLabel(share: Share): String = when (share.shareType) {
    0 -> stringResource(Res.string.share_type_user)
    1 -> stringResource(Res.string.share_type_group)
    3 -> stringResource(Res.string.share_type_public_link)
    else -> stringResource(Res.string.share_type_generic)
}

private fun shareTarget(share: Share): String =
    share.url ?: share.shareWithDisplayName ?: share.shareWith ?: ""

@Composable
private fun ImpersonationBanner(user: String, onStop: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(Res.string.impersonation_notice, "@$user"),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onStop) {
                Text(stringResource(Res.string.stop_impersonation))
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Text(
            stringResource(Res.string.files_offline_banner),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun DeleteConfirmDialog(entry: WebDavEntry, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.delete_confirm, entry.name)) },
        text = {
            Text(
                if (entry.isDir) stringResource(Res.string.delete_folder_confirm)
                else stringResource(Res.string.delete_file_confirm)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}

internal fun parentOf(path: String): String? {
    if (path == ROOT) return null
    val trimmed = path.trimEnd('/')
    val idx = trimmed.lastIndexOf('/')
    return if (idx <= 0) ROOT else trimmed.substring(0, idx)
}
