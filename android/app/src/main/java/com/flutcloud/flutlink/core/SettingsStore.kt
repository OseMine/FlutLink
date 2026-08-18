package com.flutcloud.flutlink.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
        // operationflut|midnight|system (legacy light|dark still resolve).
        val themePreference = stringPreferencesKey("theme_preference")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val accentHue = intPreferencesKey("accent_hue")
    }

    val defaultServerUrl: Flow<String> =
        context.flutlinkDataStore.data.map { it[Keys.defaultServerUrl] ?: "" }

    val themePreference: Flow<String> =
        context.flutlinkDataStore.data.map { it[Keys.themePreference] ?: "system" }

    val dynamicColor: Flow<Boolean> =
        context.flutlinkDataStore.data.map { it[Keys.dynamicColor] ?: true }

    /** Material You accent seed; null keeps the theme's default hue. */
    val accentHue: Flow<Int?> =
        context.flutlinkDataStore.data.map { it[Keys.accentHue] }

    suspend fun setDefaultServerUrl(url: String) {
        context.flutlinkDataStore.edit { it[Keys.defaultServerUrl] = url }
    }

    suspend fun setThemePreference(pref: String) {
        context.flutlinkDataStore.edit { it[Keys.themePreference] = pref }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.flutlinkDataStore.edit { it[Keys.dynamicColor] = enabled }
    }

    suspend fun setAccentHue(hue: Int?) {
        context.flutlinkDataStore.edit { prefs ->
            if (hue == null) prefs.remove(Keys.accentHue) else prefs[Keys.accentHue] = hue
        }
    }

    suspend fun defaultServerUrlOrEmpty(): String = defaultServerUrl.first()

    suspend fun dynamicColorEnabled(): Boolean = dynamicColor.first()
}