package com.flutcloud.flutlink.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class StoredAccounts(val accounts: List<AccountMeta> = emptyList())

/**
 * Account persistence mirroring the desktop client: metadata lives in a
 * normal key/value store, the token in a dedicated secure store (Android
 * Keystore-backed EncryptedSharedPreferences; file-based with restricted
 * permissions on the JVM). The token never leaves the device.
 */
class AccountStore(
    private val prefs: KeyValueStorage,
    private val securePrefs: KeyValueStorage
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun saveAccounts(accounts: List<AccountMeta>) {
        prefs.putString(ACCOUNTS_KEY, json.encodeToString(StoredAccounts(accounts)))
    }

    fun loadAccounts(): List<AccountMeta> {
        val raw = prefs.getString(ACCOUNTS_KEY) ?: return emptyList()
        return runCatching {
            json.decodeFromString<StoredAccounts>(raw).accounts
        }.getOrDefault(emptyList())
    }

    fun saveToken(meta: AccountMeta, token: String) {
        securePrefs.putString(meta.key, token)
    }

    fun tokenFor(meta: AccountMeta): String? = securePrefs.getString(meta.key)

    fun deleteToken(meta: AccountMeta) {
        securePrefs.remove(meta.key)
    }

    companion object {
        const val ACCOUNTS_KEY = "accounts_meta"
    }
}