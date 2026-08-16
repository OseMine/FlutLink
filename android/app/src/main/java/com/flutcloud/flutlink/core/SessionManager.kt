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

    private fun restoreSession() {
        val active = _accounts.value.firstOrNull { it.isActive }
        if (active == null) {
            _session.value = null
            return
        }
        val token = accountStore.tokenFor(active)
        _session.value = token?.let { AuthSession(active.instanceUrl, active.username, it) }
    }

    fun addAccount(meta: AccountMeta, token: String) {
        val withoutOld = _accounts.value.filterNot { it.key == meta.key }
        val updated = (withoutOld + meta.copy(isActive = true))
            .map { it.copy(isActive = it.key == meta.key) }
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
        val updated = _accounts.value.map { it.copy(isActive = it.key == meta.key) }
        updateAccounts(updated)
        restoreSession()
    }

    /**
     * Re-probe admin status for every stored account. Only overwrites the
     * stored flag when the OCS probe succeeds; a transient network failure
     * keeps the previous value (an admin account is never demoted by a
     * flaky connection). Persists only when something actually changed.
     */
    suspend fun refreshAdminFlags(probe: suspend (AuthSession) -> Boolean) {
        val refreshed = _accounts.value.map { meta ->
            val token = accountStore.tokenFor(meta) ?: return@map meta
            val isAdmin = try {
                probe(AuthSession(meta.instanceUrl, meta.username, token))
            } catch (_: Exception) {
                meta.isAdmin
            }
            if (isAdmin == meta.isAdmin) meta else meta.copy(isAdmin = isAdmin)
        }
        if (refreshed != _accounts.value) updateAccounts(refreshed)
    }

    /** Persist the sign-out: the active account is deactivated so a restart
     *  does not silently restore its session (login screen instead). */
    fun signOut() {
        val active = _session.value
        _session.value = null
        if (active != null) {
            val updated = _accounts.value.map {
                if (it.key == "${active.username}@${active.baseUrl.trimEnd('/')}") {
                    it.copy(isActive = false)
                } else it
            }
            updateAccounts(updated)
        }
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