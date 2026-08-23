package com.flutcloud.flutlink.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** [KeyValueStorage] backed by a plain [SharedPreferences] instance. */
class SharedPreferencesKeyValueStorage(
    private val prefs: SharedPreferences
) : KeyValueStorage {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

/**
 * [KeyValueStorage] backed by EncryptedSharedPreferences (Android
 * Keystore-backed). Holds account tokens; values never leave the app sandbox
 * unencrypted.
 */
class EncryptedKeyValueStorage(context: Context) : KeyValueStorage {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        SECURE_PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        const val SECURE_PREFS_NAME = "flutlink_secure"
        const val PREFS_NAME = "flutlink_accounts"
    }
}
