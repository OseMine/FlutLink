package com.flutcloud.flutlink.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.R
import com.flutcloud.flutlink.data.ApiException
import com.flutcloud.flutlink.data.NetworkException
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
import java.io.IOException

/** Bytes copied so far vs. total bytes of a streaming transfer. */
data class TransferProgress(val transferred: Long, val total: Long)

/** Holds a pending upload until the user confirms overwrite. */
data class PendingUpload(val targetDir: String, val name: String, val uri: android.net.Uri, val contentType: String)

class FilesViewModel(private val container: AppContainer) : ViewModel() {

    private val session get() = container.sessionManager.session.value

    val path = MutableStateFlow("/")
    val entries = MutableStateFlow<List<WebDavEntry>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<UiMessage?>(null)
    val offline = MutableStateFlow(false)
    val quota = MutableStateFlow<Quota?>(null)

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
        get() = session?.let { "${it.baseUrl}|${it.username}" }

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
                val result = container.webDavApi.list(s, folderPath)
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

    /**
     * Download a file into the app's files directory (previous opens are
     * removed first, so the cache holds at most one file) and expose it to the
     * screen, which launches the external app. Mirror of the desktop
     * `open_remote_file`.
     */
    private fun downloadAndOpen(entry: WebDavEntry) {
        val s = session ?: return
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                val dir = container.appFilesDir()
                dir.mkdirs()
                val file = java.io.File(dir, entry.name)
                container.webDavApi.downloadToFile(
                    session = s,
                    path = entry.path,
                    dest = file,
                    onProgress = { transferred, total ->
                        _transferProgress.value = TransferProgress(transferred, total)
                    }
                )
                _downloaded.value = file.absolutePath
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
     * Download a file into the public Downloads folder (MediaStore on
     * Android 10+, direct write below) and confirm with a toast. Mirrors the
     * desktop `download` action.
     */
    fun downloadToDownloads(entry: WebDavEntry) {
        val s = session ?: return
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                container.downloadToDownloads(entry.name) { dest ->
                    container.webDavApi.downloadToFile(
                        session = s,
                        path = entry.path,
                        dest = dest,
                        onProgress = { transferred, total ->
                            _transferProgress.value = TransferProgress(transferred, total)
                        }
                    )
                }
                _toast.value = UiMessage(R.string.downloaded_to_downloads, entry.name)
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
     * screen, which launches the Android share sheet. The cached file is
     * served via FileProvider (see `file_paths.xml`).
     */
    fun downloadAndShare(entry: WebDavEntry) {
        val s = session ?: return
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                val dir = container.appFilesDir()
                dir.mkdirs()
                val file = java.io.File(dir, entry.name)
                container.webDavApi.downloadToFile(
                    session = s,
                    path = entry.path,
                    dest = file,
                    onProgress = { transferred, total ->
                        _transferProgress.value = TransferProgress(transferred, total)
                    }
                )
                _sharePath.value = file.absolutePath
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
            error.value = UiMessage(R.string.error_invalid_folder_name)
            return
        }
        viewModelScope.launch {
            error.value = null
            try {
                val target = if (path.value == "/") "/$name" else "${path.value}/$name"
                container.webDavApi.mkdir(s, target)
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
                container.webDavApi.rename(s, entry.path, newPath)
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
                container.webDavApi.delete(s, entry.path)
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
            error.value = UiMessage(R.string.share_recipient_required)
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
     * Upload a SAF content [uri] by streaming from the content provider instead
     * of reading it into memory. Falls back to a buffered read only when the
     * provider does not report the content size.
     *
     * Before uploading, checks whether a file with the same name already exists
     * on the server. If it does, a confirmation dialog is shown via
     * [pendingUpload]; call [confirmUpload] or [cancelUpload] to proceed.
     */
    fun uploadStream(targetDir: String, name: String, uri: Uri, contentType: String = "application/octet-stream") {
        val s = session ?: return
        viewModelScope.launch {
            error.value = null
            try {
                val remotePath = if (targetDir == "/") "/$name" else "$targetDir/$name"
                val exists = container.webDavApi.exists(s, remotePath)
                if (exists) {
                    _pendingUpload.value = PendingUpload(targetDir, name, uri, contentType)
                    return@launch
                }
                doUpload(targetDir, name, uri, contentType)
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
        doUpload(pending.targetDir, pending.name, pending.uri, pending.contentType)
    }

    fun cancelUpload() {
        _pendingUpload.value = null
    }

    private fun doUpload(targetDir: String, name: String, uri: Uri, contentType: String) {
        val s = session ?: return
        viewModelScope.launch {
            error.value = null
            try {
                val remotePath = if (targetDir == "/") "/$name" else "$targetDir/$name"
                val size = container.contentSize(uri)
                if (size != null && size >= 0) {
                    container.webDavApi.uploadStream(
                        session = s,
                        path = remotePath,
                        openStream = {
                            container.openContentStream(uri)
                                ?: throw IOException("Cannot open $uri")
                        },
                        contentLength = size,
                        contentType = contentType,
                        onProgress = { transferred, total ->
                            _transferProgress.value = TransferProgress(transferred, total)
                        }
                    )
                } else {
                    val tmpFile = container.streamToTempFile(uri, name)
                    try {
                        container.webDavApi.uploadStream(
                            session = s,
                            path = remotePath,
                            openStream = { tmpFile.inputStream() },
                            contentLength = tmpFile.length(),
                            contentType = contentType,
                            onProgress = { transferred, total ->
                                _transferProgress.value = TransferProgress(transferred, total)
                            }
                        )
                    } finally {
                        tmpFile.delete()
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
                searchResults.value = container.webDavApi.search(s, query.trim())
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

    fun clearErrors() {
        error.value = null
        _downloaded.value = null
        _lastShare.value = null
        _pendingUpload.value = null
        _sharePath.value = null
        _toast.value = null
    }
}