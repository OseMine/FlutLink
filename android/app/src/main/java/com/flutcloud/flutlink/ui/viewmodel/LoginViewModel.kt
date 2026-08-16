package com.flutcloud.flutlink.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.BuildConfig
import com.flutcloud.flutlink.R
import com.flutcloud.flutlink.core.AccountMeta
import com.flutcloud.flutlink.data.ApiException
import com.flutcloud.flutlink.data.AuthSession
import com.flutcloud.flutlink.data.NetworkException
import com.flutcloud.flutlink.ui.UiMessage
import com.flutcloud.flutlink.ui.networkUiMessage
import com.flutcloud.flutlink.ui.toUiMessage
import com.flutcloud.flutlink.ui.unexpectedUiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val container: AppContainer) : ViewModel() {

    val serverUrl = MutableStateFlow("")
    val username = MutableStateFlow("")
    val token = MutableStateFlow("")
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<UiMessage?>(null)

    private val _step = MutableStateFlow<UiMessage?>(null)
    val step: StateFlow<UiMessage?> = _step.asStateFlow()

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
            error.value = UiMessage(R.string.error_fill_fields)
            return
        }
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                _step.value = UiMessage(R.string.signing_in)
                val session = AuthSession(url, user, pass)
                val info = container.ocsApi.getCurrentUser(session)
                _step.value = UiMessage(R.string.verifying_server)
                container.ocsApi.verifyServer(session)
                val admin = runCatching { container.ocsApi.isAdmin(session) }.getOrDefault(false)
                container.settingsStore.setDefaultServerUrl(url)
                container.sessionManager.addAccount(
                    AccountMeta(user, url, info.displayName, admin, isActive = true),
                    pass
                )
                onSuccess()
            } catch (e: NetworkException) {
                error.value = networkUiMessage(e.cause)
            } catch (e: ApiException) {
                error.value = e.toUiMessage()
            } catch (e: Exception) {
                error.value = unexpectedUiMessage(e.message)
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