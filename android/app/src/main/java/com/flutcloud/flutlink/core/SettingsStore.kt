package com.flutcloud.flutlink.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
        val themePreference = stringPreferencesKey("theme_preference") // system|operationflut|midnight
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val accentHue = floatPreferencesKey("accent_hue") // -1 = theme default
    }

    val defaultServerUrl: Flow<String> =
        context.flutlinkDataStore.data.map { it[Keys.defaultServerUrl] ?: "" }

    val themePreference: Flow<String> =
        context.flutlinkDataStore.data.map {
            when (val v = it[Keys.themePreference]) {
                "dark" -> "operationflut" // legacy explicit dark → operationflut brand
                "light" -> "system"       // legacy explicit light → system (light via OS)
                else -> v ?: "system"
            }
        }

    val dynamicColor: Flow<Boolean> =
        context.flutlinkDataStore.data.map { it[Keys.dynamicColor] ?: true }

    val accentHue: Flow<Float> =
        context.flutlinkDataStore.data.map { it[Keys.accentHue] ?: -1f }

    suspend fun setDefaultServerUrl(url: String) {
        context.flutlinkDataStore.edit { it[Keys.defaultServerUrl] = url }
    }

    suspend fun setThemePreference(pref: String) {
        context.flutlinkDataStore.edit { it[Keys.themePreference] = pref }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.flutlinkDataStore.edit { it[Keys.dynamicColor] = enabled }
    }

    suspend fun setAccentHue(hue: Float) {
        context.flutlinkDataStore.edit { it[Keys.accentHue] = hue }
    }

    suspend fun defaultServerUrlOrEmpty(): String = defaultServerUrl.first()

    suspend fun dynamicColorEnabled(): Boolean = dynamicColor.first()
}