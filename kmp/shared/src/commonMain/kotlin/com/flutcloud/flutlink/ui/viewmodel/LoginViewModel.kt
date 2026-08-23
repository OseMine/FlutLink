package com.flutcloud.flutlink.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.core.AccountMeta
import com.flutcloud.flutlink.data.*
import com.flutcloud.flutlink.data.ApiException
import com.flutcloud.flutlink.data.AuthSession
import com.flutcloud.flutlink.data.FlutCloudAppMissing
import com.flutcloud.flutlink.data.NetworkException
import com.flutcloud.flutlink.ui.UiMessage
import com.flutcloud.flutlink.ui.networkUiMessage
import com.flutcloud.flutlink.ui.toUiMessage
import com.flutcloud.flutlink.ui.unexpectedUiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.flutcloud.flutlink.resources.Res
import com.flutcloud.flutlink.resources.error_fill_fields
import com.flutcloud.flutlink.resources.error_fill_fields_register
import com.flutcloud.flutlink.resources.error_flutcloud_app_missing
import com.flutcloud.flutlink.resources.error_wrong_server_url
import com.flutcloud.flutlink.resources.signing_in
import com.flutcloud.flutlink.resources.step_creating_account
import com.flutcloud.flutlink.resources.step_setting_up_folder
import com.flutcloud.flutlink.resources.step_verifying_server
import com.flutcloud.flutlink.resources.verifying_server


class LoginViewModel(private val container: AppContainer) : ViewModel() {

    val serverUrl = MutableStateFlow("")
    val username = MutableStateFlow("")
    val token = MutableStateFlow("")
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<UiMessage?>(null)

    val registerMode = MutableStateFlow(false)
    val displayName = MutableStateFlow("")
    val adminUsername = MutableStateFlow("")
    val adminPassword = MutableStateFlow("")

    private val _step = MutableStateFlow<UiMessage?>(null)
    val step: StateFlow<UiMessage?> = _step.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = container.settingsStore.defaultServerUrlOrEmpty()
            serverUrl.value = saved.ifEmpty { container.config.defaultServerUrl }
        }
    }

    /** When a build-time URL is configured, the server field is locked. */
    val urlLocked: Boolean get() = container.config.defaultServerUrl.isNotBlank()

    fun toggleMode() {
        registerMode.value = !registerMode.value
    }

    fun signIn(onSuccess: () -> Unit) {
        if (loading.value) return
        val url = serverUrl.value.trim().trimEnd('/')
        val user = username.value.trim()
        val pass = token.value.trim()
        if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            error.value = UiMessage(Res.string.error_fill_fields)
            return
        }
        if (container.config.defaultServerUrl.isNotBlank() &&
            url.trimEnd('/') != container.config.defaultServerUrl.trimEnd('/')
        ) {
            error.value = UiMessage(Res.string.error_wrong_server_url)
            return
        }
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                _step.value = UiMessage(Res.string.signing_in)
                val session = AuthSession(url, user, pass)
                val info = container.ocsApi.getCurrentUser(session)
                _step.value = UiMessage(Res.string.verifying_server)
                container.ocsApi.verifyServer(session)
                // Mirror the desktop client: a transient probe failure keeps the
                // previously stored admin flag instead of demoting the account.
                val existing = container.sessionManager.accounts.value.firstOrNull {
                    it.username == user && it.instanceUrl.trimEnd('/') == url.trimEnd('/')
                }
                val admin = runCatching { container.ocsApi.isAdmin(session) }
                    .getOrElse { existing?.isAdmin ?: false }
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

    /**
     * Register a real new account on the server, mirroring the desktop
     * `register_user`: the admin credentials create the account via the OCS
     * Provisioning API, the FlutCloud project folder is ensured (best-effort),
     * then the new account signs in with its real password.
     */
    fun register(onSuccess: () -> Unit) {
        if (loading.value) return
        val url = serverUrl.value.trim().trimEnd('/')
        val user = username.value.trim()
        val pass = token.value.trim()
        if (url.isEmpty() || user.isEmpty() || pass.isEmpty() ||
            adminUsername.value.isBlank() || adminPassword.value.isBlank()
        ) {
            error.value = UiMessage(Res.string.error_fill_fields_register)
            return
        }
        if (container.config.defaultServerUrl.isNotBlank() &&
            url.trimEnd('/') != container.config.defaultServerUrl.trimEnd('/')
        ) {
            error.value = UiMessage(Res.string.error_wrong_server_url)
            return
        }
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                val adminSession = AuthSession(url, adminUsername.value.trim(), adminPassword.value)
                _step.value = UiMessage(Res.string.step_verifying_server)
                container.ocsApi.verifyServer(adminSession)
                _step.value = UiMessage(Res.string.step_creating_account)
                container.ocsApi.createUser(
                    adminSession, user, pass,
                    displayName.value.trim().ifBlank { null }
                )
                _step.value = UiMessage(Res.string.step_setting_up_folder)
                ensureProjectFolder(adminSession)
                _step.value = UiMessage(Res.string.signing_in)
                val session = AuthSession(url, user, pass)
                val info = container.ocsApi.getCurrentUser(session)
                container.ocsApi.verifyServer(session)
                val admin = runCatching { container.ocsApi.isAdmin(session) }.getOrDefault(false)
                container.settingsStore.setDefaultServerUrl(url)
                container.sessionManager.addAccount(
                    AccountMeta(user, url, info.displayName, admin, isActive = true),
                    pass
                )
                onSuccess()
            } catch (e: FlutCloudAppMissing) {
                error.value = UiMessage(Res.string.error_flutcloud_app_missing)
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

    /** Best-effort creation of the FlutCloud project folder (never fails the
     *  registration), mirroring the desktop's `register_user`. */
    private suspend fun ensureProjectFolder(adminSession: AuthSession) {
        runCatching { container.webDavApi.mkdir(adminSession, FLUTCLOUD_PROJECT_PATH) }
        runCatching {
            container.webDavApi.upload(
                adminSession,
                "$FLUTCLOUD_PROJECT_PATH/README.md",
                FLUTCLOUD_README.encodeToByteArray()
            )
        }
    }

    fun clearError() {
        error.value = null
    }

    companion object {
        private const val FLUTCLOUD_PROJECT_PATH = "/FlutLink/FlutCloud"
        private val FLUTCLOUD_README = """
            # FlutCloud — Nextcloud App

            Shared project space of the **FlutCloud Nextcloud app**.
        """.trimIndent()
    }
}