package com.flutcloud.flutlink.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.R
import com.flutcloud.flutlink.core.AccountMeta
import com.flutcloud.flutlink.data.AppUpdate
import com.flutcloud.flutlink.data.NetworkException
import com.flutcloud.flutlink.data.dto.AppInfoDto
import com.flutcloud.flutlink.ui.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val accounts = MutableStateFlow<List<AccountMeta>>(emptyList())
    val themePreference = MutableStateFlow("system")
    val dynamicColor = MutableStateFlow(true)
    val accentHue = MutableStateFlow<Int?>(null)
    val serverInfo = MutableStateFlow<AppInfoDto?>(null)

    val appVersion = container.config.appVersion

    private val _toast = MutableStateFlow<UiMessage?>(null)
    val toast: StateFlow<UiMessage?> = _toast.asStateFlow()

    private val _update = MutableStateFlow<AppUpdate?>(null)
    val update: StateFlow<AppUpdate?> = _update.asStateFlow()

    private val _checkingUpdate = MutableStateFlow(false)
    val checkingUpdate: StateFlow<Boolean> = _checkingUpdate.asStateFlow()

    private val _installingUpdate = MutableStateFlow(false)
    val installingUpdate: StateFlow<Boolean> = _installingUpdate.asStateFlow()

    init {
        viewModelScope.launch {
            container.sessionManager.accounts.collect { accounts.value = it }
        }
        viewModelScope.launch {
            container.settingsStore.themePreference.collect { themePreference.value = it }
        }
        viewModelScope.launch {
            container.settingsStore.dynamicColor.collect { dynamicColor.value = it }
        }
        viewModelScope.launch {
            container.settingsStore.accentHue.collect { accentHue.value = it }
        }
    }

    fun loadServerInfo() {
        val s = container.sessionManager.session.value ?: return
        viewModelScope.launch {
            serverInfo.value = runCatching { container.ocsApi.ping(s) }.getOrNull()
        }
    }

    fun setThemePreference(pref: String) {
        viewModelScope.launch { container.settingsStore.setThemePreference(pref) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setDynamicColor(enabled) }
    }

    fun setAccentHue(hue: Int?) {
        viewModelScope.launch { container.settingsStore.setAccentHue(hue) }
    }

    fun switchAccount(meta: AccountMeta) {
        viewModelScope.launch {
            container.sessionManager.switchAccount(meta)
            _toast.value = UiMessage(R.string.account_switched_to, meta.username)
        }
    }

    fun removeAccount(meta: AccountMeta) {
        viewModelScope.launch {
            container.sessionManager.removeAccount(meta)
        }
    }

    fun signOut() {
        container.sessionManager.signOut()
    }

    fun checkForUpdate() {
        if (_checkingUpdate.value) return
        viewModelScope.launch {
            _checkingUpdate.value = true
            try {
                val found = container.updater.checkForUpdate(appVersion)
                if (found != null) {
                    _update.value = found
                } else {
                    _toast.value = UiMessage(R.string.update_up_to_date, appVersion)
                }
            } catch (e: NetworkException) {
                val detail = e.cause?.message
                _toast.value = if (detail.isNullOrBlank()) {
                    UiMessage(R.string.update_check_failed)
                } else {
                    UiMessage(R.string.update_check_failed_detail, detail)
                }
            } finally {
                _checkingUpdate.value = false
            }
        }
    }

    fun dismissUpdate() {
        _update.value = null
    }

    fun installUpdate() {
        val target = _update.value ?: return
        if (_installingUpdate.value) return
        viewModelScope.launch {
            _installingUpdate.value = true
            try {
                val apk = container.updater.download(target.apkUrl)
                container.updater.install(apk)
                _update.value = null
            } catch (e: NetworkException) {
                val detail = e.cause?.message
                _toast.value = if (detail.isNullOrBlank()) {
                    UiMessage(R.string.update_download_failed)
                } else {
                    UiMessage(R.string.update_download_failed_detail, detail)
                }
            } finally {
                _installingUpdate.value = false
            }
        }
    }

    fun consumeToast() {
        _toast.value = null
    }
}