package com.flutcloud.flutlink.core

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persisted account metadata (username, instance URL, flags) — no tokens. */
@Serializable
data class AccountMeta(
    @SerialName("username") val username: String,
    @SerialName("instanceUrl") val instanceUrl: String,
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("isAdmin") val isAdmin: Boolean = false,
    @SerialName("isActive") val isActive: Boolean = false
) {
    val key: String get() = "$username@${instanceUrl.trimEnd('/')}"
}

@Serializable
private data class StoredAccounts(val accounts: List<AccountMeta> = emptyList())

/**
 * Account persistence mirroring the desktop client: metadata lives in a
 * normal preferences/DataStore file, the token in EncryptedSharedPreferences
 * (Android Keystore-backed). The token never leaves the app sandbox.
 */
class AccountStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        SECURE_PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveAccounts(accounts: List<AccountMeta>) {
        prefs.edit().putString(ACCOUNTS_KEY, json.encodeToString(StoredAccounts(accounts))).apply()
    }

    fun loadAccounts(): List<AccountMeta> {
        val raw = prefs.getString(ACCOUNTS_KEY, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<StoredAccounts>(raw).accounts
        }.getOrDefault(emptyList())
    }

    fun saveToken(meta: AccountMeta, token: String) {
        securePrefs.edit().putString(meta.key, token).apply()
    }

    fun tokenFor(meta: AccountMeta): String? = securePrefs.getString(meta.key, null)

    fun deleteToken(meta: AccountMeta) {
        securePrefs.edit().remove(meta.key).apply()
    }

    companion object {
        private const val PREFS_NAME = "flutlink_accounts"
        private const val SECURE_PREFS_NAME = "flutlink_secure"
        private const val ACCOUNTS_KEY = "accounts_meta"
    }
}