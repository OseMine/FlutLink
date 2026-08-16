package com.flutcloud.flutlink.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.BuildConfig
import com.flutcloud.flutlink.core.AccountMeta
import com.flutcloud.flutlink.data.ApiException
import com.flutcloud.flutlink.data.AuthSession
import com.flutcloud.flutlink.data.FlutCloudAppMissing
import com.flutcloud.flutlink.data.NetworkException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val container: AppContainer) : ViewModel() {

    val serverUrl = MutableStateFlow("")
    val username = MutableStateFlow("")
    val token = MutableStateFlow("")
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    private val _step = MutableStateFlow<String?>(null)
    val step: StateFlow<String?> = _step.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = container.settingsStore.defaultServerUrlOrEmpty()
            serverUrl.value = saved.ifEmpty { BuildConfig.FLUTCLOUD_URL }
        }
    }

    fun signIn(onSuccess: () -> Unit) {
        if (loading.value) return
        val url = serverUrl.value.trim().trimEnd('/')
        val user = username.value.trim()
        val pass = token.value.trim()
        if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            error.value = "Fill in the server URL, username and app token."
            return
        }
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                _step.value = "Signing in…"
                val session = AuthSession(url, user, pass)
                val info = container.ocsApi.getCurrentUser(session)
                _step.value = "Verifying FlutCloud server…"
                container.ocsApi.verifyServer(session)
                val previousAdmin = container.sessionManager.accounts.value
                    .firstOrNull { it.key == "$user@${url.trimEnd('/')}" }
                    ?.isAdmin ?: false
                val admin = try {
                    container.ocsApi.isAdmin(session)
                } catch (e: NetworkException) {
                    // Transient probe failure: keep the previously stored flag
                    // so an admin account is not demoted; the startup
                    // re-check fixes it later.
                    previousAdmin
                }
                container.settingsStore.setDefaultServerUrl(url)
                container.sessionManager.addAccount(
                    AccountMeta(user, url, info.displayName, admin, isActive = true),
                    pass
                )
                onSuccess()
            } catch (e: FlutCloudAppMissing) {
                error.value = e.message
            } catch (e: NetworkException) {
                error.value = "Could not reach the server: ${e.cause?.message ?: "network error"}"
            } catch (e: ApiException) {
                error.value = e.message
            } catch (e: Exception) {
                error.value = e.message ?: "Sign-in failed"
            } finally {
                loading.value = false
                _step.value = null
            }
        }
    }

    fun clearError() {
        error.value = null
    }
}