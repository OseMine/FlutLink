package com.flutcloud.flutlink.core

import com.flutcloud.flutlink.data.AuthSession
import com.flutcloud.flutlink.data.FlutCloudApi
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

    suspend fun init(ocsApi: FlutCloudApi) {
        val accounts = accountStore.loadAccounts()
        _accounts.value = accounts
        restoreSession()
        refreshAdminFlags(ocsApi)
    }

    /**
     * Re-evaluate the admin flag of every stored account against the server.
     * Mirrors the desktop client's `refresh_admin_flags`: the stored flag is
     * only overwritten when the OCS probe succeeds, so a transient network
     * error never demotes an admin account to a regular one. Persists and
     * emits only when something actually changed.
     */
    suspend fun refreshAdminFlags(ocsApi: FlutCloudApi) {
        val updated = _accounts.value.map { account ->
            val token = accountStore.tokenFor(account) ?: return@map account
            val session = AuthSession(account.instanceUrl, account.username, token)
            runCatching { ocsApi.isAdmin(session) }
                .fold(
                    onSuccess = { isAdmin -> account.copy(isAdmin = isAdmin) },
                    onFailure = { account }
                )
        }
        if (updated != _accounts.value) {
            updateAccounts(updated)
        }
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