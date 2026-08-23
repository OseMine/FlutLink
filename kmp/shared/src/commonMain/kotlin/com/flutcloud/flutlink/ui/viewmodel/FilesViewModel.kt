package com.flutcloud.flutlink.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.data.ApiException
import com.flutcloud.flutlink.data.NetworkException
import com.flutcloud.flutlink.data.PickedFile
import com.flutcloud.flutlink.data.createShare
import com.flutcloud.flutlink.data.deleteShare
import com.flutcloud.flutlink.data.listShares
import com.flutcloud.flutlink.data.systemFileSystem
import com.flutcloud.flutlink.data.dto.Quota
import com.flutcloud.flutlink.data.dto.Share
import com.flutcloud.flutlink.data.dto.WebDavEntry
import com.flutcloud.flutlink.ui.UiMessage
import com.flutcloud.flutlink.ui.networkUiMessage
import com.flutcloud.flutlink.ui.toUiMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okio.Path
import okio.buffer
import okio.use
import com.flutcloud.flutlink.resources.Res
import com.flutcloud.flutlink.resources.downloaded_to_downloads
import com.flutcloud.flutlink.resources.error_invalid_folder_name
import com.flutcloud.flutlink.resources.error_not_admin_impersonation
import com.flutcloud.flutlink.resources.share_recipient_required


/** Bytes copied so far vs. total bytes of a streaming transfer. */
data class TransferProgress(val transferred: Long, val total: Long)

/** Holds a pending upload until the user confirms overwrite. */
data class PendingUpload(val targetDir: String, val name: String, val file: PickedFile)

class FilesViewModel(private val container: AppContainer) : ViewModel() {

    private val session get() = container.sessionManager.session.value

    val path = MutableStateFlow("/")
    val entries = MutableStateFlow<List<WebDavEntry>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<UiMessage?>(null)
    val offline = MutableStateFlow(false)
    val quota = MutableStateFlow<Quota?>(null)

    /** Admin impersonation: the user whose files are browsed (null = own files). */
    val targetUser = MutableStateFlow<String?>(null)

    val searchQuery = MutableStateFlow("")
    val searchResults = MutableStateFlow<List<WebDavEntry>>(emptyList())
    val searching = MutableStateFlow(false)

    private val _lastShare = MutableStateFlow<Share?>(null)
    val lastShare: StateFlow<Share?> = _lastShare.asStateFlow()

    private val _shares = MutableStateFlow<List<Share>>(emptyList())
    val shares: StateFlow<List<Share>> = _shares.asStateFlow()

    private val _sharesLoading = MutableStateFlow(false)
    val sharesLoading: StateFlow<Boolean> = _sharesLoading.asStateFlow()

    private val _pendingUpload = MutableStateFlow<PendingUpload?>(null)
    val pendingUpload: StateFlow<PendingUpload?> = _pendingUpload.asStateFlow()

    private val _downloaded = MutableStateFlow<String?>(null)
    val downloaded: StateFlow<String?> = _downloaded.asStateFlow()

    private val _sharePath = MutableStateFlow<String?>(null)
    val sharePath: StateFlow<String?> = _sharePath.asStateFlow()

    private val _toast = MutableStateFlow<UiMessage?>(null)
    val toast: StateFlow<UiMessage?> = _toast.asStateFlow()

    private var searchJob: Job? = null

    private val _transferProgress = MutableStateFlow<TransferProgress?>(null)
    val transferProgress: StateFlow<TransferProgress?> = _transferProgress.asStateFlow()

    val sessionKey: String?
        get() = session?.let { "${it.baseUrl}|${it.username}|${targetUser.value ?: ""}" }

    /**
     * Whether the signed-in account is an administrator (mirrors the desktop
     * command-level admin gate: non-admins are refused with Forbidden).
     */
    private fun isAdmin(): Boolean {
        val s = session ?: return false
        return container.sessionManager.accounts.value.any {
            it.username == s.username &&
                it.instanceUrl.trimEnd('/') == s.baseUrl.trimEnd('/') &&
                it.isAdmin
        }
    }

    /**
     * Switch the browsed namespace to another user's files (admin
     * impersonation). Non-admins are refused; the browser resets to that
     * user's root, mirroring the desktop `files.setTargetUser`.
     */
    fun setTargetUser(userId: String?) {
        val s = session ?: return
        val target = userId?.takeIf { it != s.username }
        if (target != null && !isAdmin()) {
            error.value = UiMessage(Res.string.error_not_admin_impersonation)
            return
        }
        targetUser.value = target
        path.value = "/"
        listFolder("/")
    }

    fun refresh() {
        listFolder(path.value)
        refreshQuota()
    }

    fun listFolder(folderPath: String) {
        val s = session ?: return
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                val result = container.webDavApi.list(s, folderPath, targetUser.value)
                    .sortedWith(compareByDescending<WebDavEntry> { it.isDir }.thenBy { it.name.lowercase() })
                entries.value = result
                offline.value = false
                sessionKey?.let { container.listCache.write(it, folderPath, result) }
                path.value = folderPath
            } catch (e: NetworkException) {
                val cached = sessionKey?.let { container.listCache.read(it, folderPath) }
                if (cached != null) {
                    entries.value = cached
                    offline.value = true
                    path.value = folderPath
                } else {
                    error.value = networkUiMessage(e.cause)
                }
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            } finally {
                loading.value = false
            }
        }
    }

    fun refreshQuota() {
        val s = session ?: return
        viewModelScope.launch {
            quota.value = runCatching { container.ocsApi.getCurrentQuota(s) }.getOrNull()
        }
    }

    fun open(entry: WebDavEntry) {
        if (entry.isDir) {
            listFolder(entry.path)
        } else {
            downloadAndOpen(entry)
        }
    }

    private suspend fun downloadInto(
        entry: WebDavEntry,
        dest: Path,
        reportProgress: Boolean
    ) {
        val s = session ?: throw IllegalStateException("No session")
        container.webDavApi.downloadToFile(
            session = s,
            path = entry.path,
            dest = dest,
            onProgress = if (reportProgress) ({ transferred, total ->
                _transferProgress.value = TransferProgress(transferred, total)
            }) else null,
            targetUser = targetUser.value
        )
    }

    private suspend fun downloadWithProgress(entry: WebDavEntry, dest: Path) =
        downloadInto(entry, dest, reportProgress = true)

    /**
     * Download a file into the app's files directory (previous opens share the
     * same name slot, so the cache stays small) and expose it to the screen,
     * which launches the external viewer. Mirror of `open_remote_file`.
     */
    private fun downloadAndOpen(entry: WebDavEntry) {
        val s = session ?: return
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                val dir = container.platform.appFilesDir()
                val file = dir.resolve(entry.name, normalize = false)
                downloadWithProgress(entry, file)
                _downloaded.value = file.toString()
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            } finally {
                loading.value = false
                _transferProgress.value = null
            }
        }
    }

    /**
     * Download a file into the public Downloads location and confirm with a
     * toast. Mirrors the desktop `download` action.
     */
    fun downloadToDownloads(entry: WebDavEntry) {
        val s = session ?: return
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                container.platform.saveToDownloads(entry.name) { dest ->
                    downloadWithProgress(entry, dest)
                }
                _toast.value = UiMessage(Res.string.downloaded_to_downloads, entry.name)
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            } finally {
                loading.value = false
                _transferProgress.value = null
            }
        }
    }

    /**
     * Download a file into the app's files directory and expose it to the
     * screen, which launches the system share sheet.
     */
    fun downloadAndShare(entry: WebDavEntry) {
        val s = session ?: return
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                val dir = container.platform.appFilesDir()
                val file = dir.resolve(entry.name, normalize = false)
                downloadWithProgress(entry, file)
                _sharePath.value = file.toString()
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            } finally {
                loading.value = false
                _transferProgress.value = null
            }
        }
    }

    fun mkdir(name: String, onDone: () -> Unit = {}) {
        val s = session ?: return
        if (name.isBlank() || name == "." || name == ".." || name.contains("/")) {
            error.value = UiMessage(Res.string.error_invalid_folder_name)
            return
        }
        viewModelScope.launch {
            error.value = null
            try {
                val target = if (path.value == "/") "/$name" else "${path.value}/$name"
                container.webDavApi.mkdir(s, target, targetUser.value)
                listFolder(path.value)
                onDone()
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            }
        }
    }

    fun rename(entry: WebDavEntry, newName: String) {
        if (newName.isBlank() || newName == entry.name) return
        val s = session ?: return
        viewModelScope.launch {
            error.value = null
            try {
                val newPath = entry.path.substringBeforeLast('/', "") + "/" + newName
                container.webDavApi.rename(s, entry.path, newPath, targetUser.value)
                listFolder(path.value)
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            }
        }
    }

    fun delete(entry: WebDavEntry) {
        val s = session ?: return
        viewModelScope.launch {
            error.value = null
            try {
                container.webDavApi.delete(s, entry.path, targetUser.value)
                entries.value = entries.value.filterNot { it.path == entry.path }
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            }
        }
    }

    /**
     * Create a share for [entry], mirroring the desktop `webdav_create_share`:
     * public link (3), user (0) or group (1) share. User/group shares require
     * a recipient; link shares support the [password]/[expireDate]/[publicUpload]
     * options. Refreshes the share list afterwards.
     */
    fun createShare(
        entry: WebDavEntry,
        shareType: Int,
        shareWith: String? = null,
        password: String? = null,
        expireDate: String? = null,
        publicUpload: Boolean = false
    ) {
        val s = session ?: return
        val with = shareWith?.trim()
        if (shareType < 3 && with.isNullOrBlank()) {
            error.value = UiMessage(Res.string.share_recipient_required)
            return
        }
        viewModelScope.launch {
            error.value = null
            try {
                _lastShare.value = container.ocsApi.createShare(
                    session = s,
                    path = entry.path,
                    shareType = shareType,
                    shareWith = with,
                    password = password?.ifBlank { null },
                    expireDate = expireDate?.ifBlank { null },
                    publicUpload = publicUpload
                )
                loadShares(entry)
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            }
        }
    }

    /** Load all shares of the entry's path (public links + user/group shares). */
    fun loadShares(entry: WebDavEntry) {
        val s = session ?: return
        viewModelScope.launch {
            _sharesLoading.value = true
            error.value = null
            try {
                _shares.value = container.ocsApi.listShares(s, entry.path)
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            } finally {
                _sharesLoading.value = false
            }
        }
    }

    /** Revoke a share by id, mirroring the desktop's `webdav_delete_share`. */
    fun deleteShare(share: Share) {
        val s = session ?: return
        viewModelScope.launch {
            error.value = null
            try {
                container.ocsApi.deleteShare(s, share.id)
                _shares.value = _shares.value.filterNot { it.id == share.id }
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            }
        }
    }

    fun resetShares() {
        _shares.value = emptyList()
        _sharesLoading.value = false
    }

    /**
     * Upload a file chosen through the platform document picker by streaming
     * from it instead of reading it into memory. Falls back to a buffered
     * copy into cache only when the picker does not report a content size.
     *
     * Before uploading, checks whether a file with the same name already
     * exists on the server; if so, [pendingUpload] is set for confirmation.
     */
    fun uploadPicked(targetDir: String, picked: PickedFile) {
        val s = session ?: return
        viewModelScope.launch {
            error.value = null
            try {
                val remotePath = if (targetDir == "/") "/${picked.displayName}" else "$targetDir/${picked.displayName}"
                val exists = container.webDavApi.exists(s, remotePath, targetUser.value)
                if (exists) {
                    _pendingUpload.value = PendingUpload(targetDir, picked.displayName, picked)
                    return@launch
                }
                doUpload(targetDir, picked)
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            }
        }
    }

    fun confirmUpload() {
        val pending = _pendingUpload.value ?: return
        _pendingUpload.value = null
        doUpload(pending.targetDir, pending.file)
    }

    fun clearPendingUpload() {
        _pendingUpload.value = null
    }

    private fun doUpload(targetDir: String, rawPicked: PickedFile) {
        val s = session ?: return
        viewModelScope.launch {
            error.value = null
            try {
                val picked = container.platform.materialize(rawPicked)
                val remotePath = if (targetDir == "/") "/${picked.displayName}" else "$targetDir/${picked.displayName}"
                val size = picked.size
                if (size != null && size >= 0) {
                    container.webDavApi.uploadStream(
                        session = s,
                        path = remotePath,
                        openStream = { picked.open() },
                        contentLength = size,
                        contentTypeValue = picked.contentType,
                        onProgress = { transferred, total ->
                            _transferProgress.value = TransferProgress(transferred, total)
                        },
                        targetUser = targetUser.value
                    )
                } else {
                    // Unknown size: buffer into cache once, then stream from disk.
                    val fs = systemFileSystem()
                    val tmp = container.platform.cacheDir()
                        .resolve("uploads", normalize = false)
                        .resolve(picked.displayName, normalize = false)
                    fs.createDirectories(tmp.parent!!)
                    fs.sink(tmp).buffer().use { sink ->
                        picked.open().use { src ->
                            sink.writeAll(src)
                        }
                    }
                    try {
                        container.webDavApi.uploadStream(
                            session = s,
                            path = remotePath,
                            openStream = { fs.source(tmp) },
                            contentLength = fs.metadata(tmp).size ?: 0L,
                            contentTypeValue = picked.contentType,
                            onProgress = { transferred, total ->
                                _transferProgress.value = TransferProgress(transferred, total)
                            },
                            targetUser = targetUser.value
                        )
                    } finally {
                        runCatching { fs.delete(tmp) }
                    }
                }
                listFolder(path.value)
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            } finally {
                _transferProgress.value = null
            }
        }
    }

    fun search(query: String) {
        val s = session ?: return
        searchJob?.cancel()
        searchQuery.value = query
        if (query.isBlank()) {
            searchResults.value = emptyList()
            searching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            searching.value = true
            error.value = null
            try {
                searchResults.value = container.webDavApi.search(s, query.trim(), targetUser.value)
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            } finally {
                searching.value = false
            }
        }
    }

    fun clearSearch() {
        searchQuery.value = ""
        searchResults.value = emptyList()
    }

    fun clearError() {
        error.value = null
    }

    fun clearDownloaded() {
        _downloaded.value = null
    }

    fun clearSharePath() {
        _sharePath.value = null
    }

    fun clearLastShare() {
        _lastShare.value = null
    }

    fun clearToast() {
        _toast.value = null
    }
}