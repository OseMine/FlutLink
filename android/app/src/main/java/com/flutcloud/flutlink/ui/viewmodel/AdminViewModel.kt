package com.flutcloud.flutlink.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.data.ApiException
import com.flutcloud.flutlink.data.NetworkException
import com.flutcloud.flutlink.data.dto.ManagedUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminViewModel(private val container: AppContainer) : ViewModel() {

    private val session get() = container.sessionManager.session.value

    val users = MutableStateFlow<List<ManagedUser>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val search = MutableStateFlow("")

    fun refresh() = loadUsers()

    fun loadUsers() {
        val s = session ?: return
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                val ids = container.ocsApi.listUsers(s, search.value.trim())
                val result = ids.map { id ->
                    runCatching { container.ocsApi.getUser(s, id) }
                        .getOrElse { ManagedUser(id = id, displayName = null, email = null, quota = null, groups = emptyList(), enabled = true) }
                }
                users.value = result
            } catch (e: NetworkException) {
                error.value = "Could not reach the server: ${e.cause?.message ?: "network error"}"
            } catch (e: ApiException) {
                error.value = e.message
            } finally {
                loading.value = false
            }
        }
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
                error.value = "Could not reach the server: ${e.cause?.message ?: "network error"}"
            } catch (e: ApiException) {
                error.value = e.message
            }
        }
    }

    fun deleteUser(user: ManagedUser) {
        val s = session ?: return
        viewModelScope.launch {
            error.value = null
            try {
                container.ocsApi.deleteUser(s, user.id)
                users.value = users.value.filterNot { it.id == user.id }
            } catch (e: NetworkException) {
                error.value = "Could not reach the server: ${e.cause?.message ?: "network error"}"
            } catch (e: ApiException) {
                error.value = e.message
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
                error.value = "Could not reach the server: ${e.cause?.message ?: "network error"}"
            } catch (e: ApiException) {
                error.value = e.message
            }
        }
    }

    fun setEnabled(user: ManagedUser, enabled: Boolean) {
        val s = session ?: return
        viewModelScope.launch {
            error.value = null
            try {
                container.ocsApi.updateUser(s, user.id, "enabled", if (enabled) "1" else "0")
                loadUsers()
            } catch (e: NetworkException) {
                error.value = "Could not reach the server: ${e.cause?.message ?: "network error"}"
            } catch (e: ApiException) {
                error.value = e.message
            }
        }
    }

    fun clearError() {
        error.value = null
    }
}