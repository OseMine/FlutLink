package com.flutcloud.flutlink.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.data.ApiException
import com.flutcloud.flutlink.data.AuthSession
import com.flutcloud.flutlink.data.NetworkException
import com.flutcloud.flutlink.data.dto.ManagedUser
import com.flutcloud.flutlink.ui.UiMessage
import com.flutcloud.flutlink.ui.networkUiMessage
import com.flutcloud.flutlink.ui.toUiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminViewModel(private val container: AppContainer) : ViewModel() {

    private val session get() = container.sessionManager.session.value

    val users = MutableStateFlow<List<ManagedUser>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<UiMessage?>(null)
    val search = MutableStateFlow("")
    val hasMore = MutableStateFlow(false)

    private var offset = 0
    private var searchTerm = ""

    fun refresh() = loadUsers()

    /** Reset the list when the search field is empty (no server round-trips). */
    fun clearSearch() {
        users.value = emptyList()
        hasMore.value = false
        searchTerm = ""
        offset = 0
        loading.value = false
        error.value = null
    }

    fun loadUsers() {
        val s = session ?: return
        val term = search.value.trim()
        if (term.isEmpty()) {
            searchTerm = ""
            offset = 0
            users.value = emptyList()
            hasMore.value = false
            loading.value = false
            return
        }
        searchTerm = term
        offset = 0
        viewModelScope.launch {
            loading.value = true
            error.value = null
            users.value = emptyList()
            hasMore.value = false
            try {
                loadPage(s, append = false)
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            } finally {
                loading.value = false
            }
        }
    }

    fun loadMore() {
        val s = session ?: return
        if (loading.value) return
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                loadPage(s, append = true)
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            } finally {
                loading.value = false
            }
        }
    }

    private suspend fun loadPage(s: AuthSession, append: Boolean) {
        val page = container.ocsApi.listUsersPage(s, searchTerm, offset)
        val managed = page.map { id ->
            runCatching { container.ocsApi.getUser(s, id) }
                .getOrElse { ManagedUser(id = id, displayName = null, email = null, quota = null, groups = emptyList(), enabled = true) }
        }
        users.value = if (append) users.value + managed else managed
        hasMore.value = page.size == 200
        offset += page.size
    }

    fun createUser(userId: String, password: String, displayName: String?) {
        if (userId.isBlank() || password.isBlank()) return
        val s = session ?: return
        viewModelScope.launch {
            error.value = null
            try {
                container.ocsApi.createUser(s, userId.trim(), password, displayName?.trim()?.ifBlank { null })
                loadUsers()
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            }
        }
    }

    fun deleteUser(user: ManagedUser) {
        val s = session ?: return
        if (user.id == s.username) {
            error.value = UiMessage(com.flutcloud.flutlink.R.string.cannot_delete_self)
            return
        }
        viewModelScope.launch {
            error.value = null
            try {
                container.ocsApi.deleteUser(s, user.id)
                users.value = users.value.filterNot { it.id == user.id }
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            }
        }
    }

    fun setQuota(user: ManagedUser, quotaBytes: Long?) {
        val s = session ?: return
        viewModelScope.launch {
            error.value = null
            try {
                container.ocsApi.setUserQuota(s, user.id, quotaBytes)
                loadUsers()
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            }
        }
    }

    fun setEnabled(user: ManagedUser, enabled: Boolean) {
        val s = session ?: return
        if (user.id == s.username && !enabled) {
            error.value = UiMessage(com.flutcloud.flutlink.R.string.cannot_disable_self)
            return
        }
        viewModelScope.launch {
            error.value = null
            try {
                container.ocsApi.updateUser(s, user.id, "enabled", if (enabled) "1" else "0")
                loadUsers()
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            }
        }
    }

    /** Add `user` to the group `group` (creates the membership on the server). */
    fun addToGroup(user: ManagedUser, group: String) {
        val g = group.trim()
        if (g.isEmpty()) return
        val s = session ?: return
        viewModelScope.launch {
            error.value = null
            try {
                container.ocsApi.addGroupMember(s, g, user.id)
                loadUsers()
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            }
        }
    }

    /** Remove `user` from the group `group`. */
    fun removeFromGroup(user: ManagedUser, group: String) {
        val s = session ?: return
        viewModelScope.launch {
            error.value = null
            try {
                container.ocsApi.removeGroupMember(s, group, user.id)
                loadUsers()
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            }
        }
    }

    /** Create a new group with the given id. */
    fun createGroup(name: String) {
        val g = name.trim()
        if (g.isEmpty()) return
        val s = session ?: return
        viewModelScope.launch {
            error.value = null
            try {
                container.ocsApi.createGroup(s, g)
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            }
        }
    }

    fun clearError() {
        error.value = null
    }
}