package com.flutcloud.flutlink.core

/**
 * Minimal string key/value persistence contract so account metadata and
 * tokens can live in `commonMain`. Platform layers provide the backing
 * store: SharedPreferences (plain) + EncryptedSharedPreferences (secure) on
 * Android, properties files on the JVM.
 */
interface KeyValueStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}
