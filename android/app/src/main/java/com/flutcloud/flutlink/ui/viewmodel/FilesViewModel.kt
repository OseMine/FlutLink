package com.flutcloud.flutlink.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.data.ApiException
import com.flutcloud.flutlink.data.NetworkException
import com.flutcloud.flutlink.data.dto.Quota
import com.flutcloud.flutlink.data.dto.Share
import com.flutcloud.flutlink.data.dto.WebDavEntry
import com.flutcloud.flutlink.ui.UiMessage
import com.flutcloud.flutlink.ui.networkUiMessage
import com.flutcloud.flutlink.ui.toUiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** Bytes copied so far vs. total bytes of a streaming transfer. */
data class TransferProgress(val transferred: Long, val total: Long)

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

    private val _downloaded = MutableStateFlow<String?>(null)
    val downloaded: StateFlow<String?> = _downloaded.asStateFlow()

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
     * Download a file into the app's open-cache directory (previous opens are
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

    fun mkdir(name: String, onDone: () -> Unit = {}) {
        val s = session ?: return
        if (name.isBlank() || name == "." || name == ".." || name.contains("/")) {
            error.value = "The folder name must not contain '/', '.' or '..'."
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

    fun createPublicShare(entry: WebDavEntry, password: String? = null, expireDate: String? = null) {
        val s = session ?: return
        viewModelScope.launch {
            error.value = null
            try {
                _lastShare.value = container.ocsApi.createShare(
                    session = s,
                    path = entry.path,
                    shareType = 3,
                    password = password?.ifBlank { null },
                    expireDate = expireDate?.ifBlank { null }
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
     */
    fun uploadStream(targetDir: String, name: String, uri: Uri, contentType: String = "application/octet-stream") {
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
                    val bytes = container.readAllBytes(uri)
                    container.webDavApi.upload(s, remotePath, bytes, contentType = contentType)
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
        viewModelScope.launch {
            searchQuery.value = query
            if (query.isBlank()) {
                searchResults.value = emptyList()
                searching.value = false
                return@launch
            }
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
    }
}