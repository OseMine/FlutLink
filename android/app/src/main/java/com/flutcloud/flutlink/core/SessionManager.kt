package com.flutcloud.flutlink.core

import com.flutcloud.flutlink.data.AuthSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the active account + session for the UI. Kept out of the repository
 * layer so network code only ever sees an immutable [AuthSession].
 */
class SessionManager(private val accountStore: AccountStore) {

    private val _session = MutableStateFlow<AuthSession?>(null)
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    private val _accounts = MutableStateFlow<List<AccountMeta>>(emptyList())
    val accounts: StateFlow<List<AccountMeta>> = _accounts.asStateFlow()

    suspend fun init() {
        val accounts = accountStore.loadAccounts()
        _accounts.value = accounts
        restoreSession()
    }

    private suspend fun restoreSession() {
        val active = _accounts.value.firstOrNull { it.isActive } ?: _accounts.value.firstOrNull()
        if (active == null) {
            _session.value = null
            return
        }
        val token = accountStore.tokenFor(active)
        _session.value = token?.let { AuthSession(active.instanceUrl, active.username, it) }
    }

    fun addAccount(meta: AccountMeta, token: String) {
        val withoutOld = _accounts.value.filterNot {
            it.username == meta.username && it.instanceUrl.trimEnd('/') == meta.instanceUrl.trimEnd('/')
        }
        val updated = (withoutOld + meta.copy(isActive = true))
            .map { it.copy(isActive = it.username == meta.username) }
        accountStore.saveAccounts(updated)
        accountStore.saveToken(meta, token)
        _accounts.value = updated
        _session.value = AuthSession(meta.instanceUrl, meta.username, token)
    }

    fun updateAccounts(accounts: List<AccountMeta>) {
        accountStore.saveAccounts(accounts)
        _accounts.value = accounts
    }

    suspend fun switchAccount(meta: AccountMeta) {
        val updated = _accounts.value.map { it.copy(isActive = it.username == meta.username) }
        updateAccounts(updated)
        restoreSession()
    }

    fun signOut() {
        _session.value = null
    }

    fun removeAccount(meta: AccountMeta) {
        accountStore.deleteToken(meta)
        val updated = _accounts.value.filterNot {
            it.username == meta.username && it.instanceUrl.trimEnd('/') == meta.instanceUrl.trimEnd('/')
        }
        updateAccounts(updated)
        if (updated.isEmpty()) {
            _session.value = null
        } else {
            if (_session.value?.username == meta.username) {
                val next = updated.firstOrNull { it.isActive } ?: updated.first()
                val token = accountStore.tokenFor(next)
                _session.value = token?.let { AuthSession(next.instanceUrl, next.username, it) }
            }
        }
    }
}