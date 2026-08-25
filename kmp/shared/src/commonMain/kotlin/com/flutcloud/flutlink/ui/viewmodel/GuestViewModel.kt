package com.flutcloud.flutlink.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.data.ApiException
import com.flutcloud.flutlink.data.FlutCloudAppMissing
import com.flutcloud.flutlink.data.NetworkException
import com.flutcloud.flutlink.data.assignGuestCategory
import com.flutcloud.flutlink.data.deleteGuestCategory
import com.flutcloud.flutlink.data.downloadGuestFile
import com.flutcloud.flutlink.data.listGuestEntries
import com.flutcloud.flutlink.data.listGuestShares
import com.flutcloud.flutlink.data.lockGuestPath
import com.flutcloud.flutlink.data.setGuestCategory
import com.flutcloud.flutlink.data.unassignGuestCategory
import com.flutcloud.flutlink.data.unlockGuestPath
import com.flutcloud.flutlink.data.verifyGuestServer
import com.flutcloud.flutlink.data.dto.GuestEntry
import com.flutcloud.flutlink.data.dto.GuestShare
import com.flutcloud.flutlink.resources.Res
import com.flutcloud.flutlink.resources.downloaded_to_downloads
import com.flutcloud.flutlink.resources.error_flutcloud_app_missing
import com.flutcloud.flutlink.resources.guest_locked
import com.flutcloud.flutlink.resources.guest_unlocked
import com.flutcloud.flutlink.ui.UiMessage
import com.flutcloud.flutlink.ui.networkUiMessage
import com.flutcloud.flutlink.ui.toUiMessage
import com.flutcloud.flutlink.ui.unexpectedUiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Guest mode (complete public shares): strictly read-only browsing without an
 * account. Mirrors the desktop `GuestBrowser.vue` flow — bundled share list at
 * the root, folder browsing inside one share. When signed in as admin, also
 * exposes category/lock management for public shares.
 */
class GuestViewModel(private val container: AppContainer) : ViewModel() {

    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<UiMessage?>(null)
    val shares = MutableStateFlow<List<GuestShare>>(emptyList())
    private val _toast = MutableStateFlow<UiMessage?>(null)
    val toast: StateFlow<UiMessage?> = _toast.asStateFlow()

    // Browsing state inside one public share.
    val activeShare = MutableStateFlow<GuestShare?>(null)
    val path = MutableStateFlow("/")
    val entries = MutableStateFlow<List<GuestEntry>>(emptyList())

    /** Admin state — derived from the active session. */
    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    /** All known category names (from the share list). */
    private val _allCategories = MutableStateFlow<List<String>>(emptyList())
    val allCategories: StateFlow<List<String>> = _allCategories.asStateFlow()

    /** The fixed server URL for guest access. */
    private suspend fun baseUrl(): String? {
        val saved = container.settingsStore.defaultServerUrlOrEmpty()
        return saved.ifEmpty { container.config.defaultServerUrl }.ifBlank { null }
    }

    fun load() {
        if (loading.value) return
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                val url = baseUrl()
                    ?: throw ApiException("No FlutCloud server configured", "no_server")
                container.ocsApi.verifyGuestServer(url)
                shares.value = container.ocsApi.listGuestShares(url)
                _allCategories.value = shares.value.mapNotNull { it.category }.distinct()
                // Check admin status from the active session + account list.
                val s = container.sessionManager.session.value
                _isAdmin.value = s?.let {
                    container.sessionManager.accounts.value.firstOrNull { a ->
                        a.username == it.username && a.instanceUrl.trimEnd('/') == it.baseUrl.trimEnd('/')
                    }?.isAdmin
                } ?: false
            } catch (_: FlutCloudAppMissing) {
                error.value = UiMessage(Res.string.error_flutcloud_app_missing)
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            } catch (e: Exception) {
                error.value = unexpectedUiMessage(e.message)
            } finally {
                loading.value = false
            }
        }
    }

    fun enter(share: GuestShare) {
        activeShare.value = share
        navigateTo("/")
    }

    fun navigateTo(target: String) {
        val share = activeShare.value ?: return
        viewModelScope.launch {
            try {
                val url = baseUrl() ?: return@launch
                entries.value = container.ocsApi.listGuestEntries(url, share.token, target)
                path.value = target
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            } catch (e: Exception) {
                error.value = unexpectedUiMessage(e.message)
            }
        }
    }

    /** Back to the bundled overview of all complete public shares. */
    fun leave() {
        activeShare.value = null
        entries.value = emptyList()
        path.value = "/"
    }

    /** Read-only download into the public Downloads location. */
    fun download(entry: GuestEntry) {
        val share = activeShare.value ?: return
        viewModelScope.launch {
            try {
                val url = baseUrl() ?: return@launch
                container.platform.saveToDownloads(entry.name) { dest ->
                    container.ocsApi.downloadGuestFile(url, share.token, entry.path, dest)
                }
                _toast.value = UiMessage(Res.string.downloaded_to_downloads, entry.name)
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            } catch (e: Exception) {
                error.value = unexpectedUiMessage(e.message)
            }
        }
    }

    // --- Admin: categories -------------------------------------------------

    fun createCategory(name: String, prefixless: Boolean) {
        val s = container.sessionManager.session.value ?: return
        viewModelScope.launch {
            try {
                container.ocsApi.setGuestCategory(s, name, prefixless)
                // Refresh share list to pick up new category.
                refreshShares()
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            } catch (e: Exception) {
                error.value = unexpectedUiMessage(e.message)
            }
        }
    }

    fun deleteCategory(name: String) {
        val s = container.sessionManager.session.value ?: return
        viewModelScope.launch {
            try {
                container.ocsApi.deleteGuestCategory(s, name)
                refreshShares()
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            } catch (e: Exception) {
                error.value = unexpectedUiMessage(e.message)
            }
        }
    }

    // --- Admin: share-category assignment -----------------------------------

    fun assignCategory(token: String, category: String) {
        val s = container.sessionManager.session.value ?: return
        viewModelScope.launch {
            try {
                container.ocsApi.assignGuestCategory(s, token, category)
                refreshShares()
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            } catch (e: Exception) {
                error.value = unexpectedUiMessage(e.message)
            }
        }
    }

    fun unassignCategory(token: String) {
        val s = container.sessionManager.session.value ?: return
        viewModelScope.launch {
            try {
                container.ocsApi.unassignGuestCategory(s, token)
                refreshShares()
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            } catch (e: Exception) {
                error.value = unexpectedUiMessage(e.message)
            }
        }
    }

    // --- Admin: lock / unlock -----------------------------------------------

    fun lockPath(token: String, path: String) {
        val s = container.sessionManager.session.value ?: return
        viewModelScope.launch {
            try {
                container.ocsApi.lockGuestPath(s, token, path)
                _toast.value = UiMessage(Res.string.guest_locked)
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            } catch (e: Exception) {
                error.value = unexpectedUiMessage(e.message)
            }
        }
    }

    fun unlockPath(token: String, path: String) {
        val s = container.sessionManager.session.value ?: return
        viewModelScope.launch {
            try {
                container.ocsApi.unlockGuestPath(s, token, path)
                _toast.value = UiMessage(Res.string.guest_unlocked)
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            } catch (e: Exception) {
                error.value = unexpectedUiMessage(e.message)
            }
        }
    }

    /** Re-fetch the share list (after admin mutations). */
    private suspend fun refreshShares() {
        val url = baseUrl() ?: return
        try {
            shares.value = container.ocsApi.listGuestShares(url)
            _allCategories.value = shares.value.mapNotNull { it.category }.distinct()
        } catch (_: Exception) { /* best-effort refresh */ }
    }

    fun consumeToast() {
        _toast.value = null
    }

    init {
        load()
    }
}
