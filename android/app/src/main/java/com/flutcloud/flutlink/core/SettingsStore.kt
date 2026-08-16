package com.flutcloud.flutlink.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.flutlinkDataStore by preferencesDataStore(name = "flutlink_prefs")

/** App settings persisted via DataStore. */
class SettingsStore(private val context: Context) {

    private object Keys {
        val defaultServerUrl = stringPreferencesKey("default_server_url")
        val themePreference = stringPreferencesKey("theme_preference") // system|light|dark
        val dynamicColor = booleanPreferencesKey("dynamic_color")
    }

    val defaultServerUrl: Flow<String> =
        context.flutlinkDataStore.data.map { it[Keys.defaultServerUrl] ?: "" }

    val themePreference: Flow<String> =
        context.flutlinkDataStore.data.map { it[Keys.themePreference] ?: "system" }

    val dynamicColor: Flow<Boolean> =
        context.flutlinkDataStore.data.map { it[Keys.dynamicColor] ?: true }

    suspend fun setDefaultServerUrl(url: String) {
        context.flutlinkDataStore.edit { it[Keys.defaultServerUrl] = url }
    }

    suspend fun setThemePreference(pref: String) {
        context.flutlinkDataStore.edit { it[Keys.themePreference] = pref }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.flutlinkDataStore.edit { it[Keys.dynamicColor] = enabled }
    }

    suspend fun defaultServerUrlOrEmpty(): String = defaultServerUrl.first()

    suspend fun dynamicColorEnabled(): Boolean = dynamicColor.first()
}