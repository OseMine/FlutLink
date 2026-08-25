package com.flutcloud.flutlink.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App settings persisted through a plain [KeyValueStorage] (platform-provided:
 * SharedPreferences on Android, NSUserDefaults elsewhere). Mirrors the old
 * DataStore-based Android store: values are exposed as [Flow]s so screens can
 * `collectAsState` them.
 */
class SettingsStore(private val storage: KeyValueStorage) {

    private object Keys {
        const val defaultServerUrl = "default_server_url"
        // midnight|light|system (legacy light|dark|operationflut still resolve).
        const val themePreference = "theme_preference"
        const val dynamicColor = "dynamic_color"
        const val accentHue = "accent_hue"
        // Remembered guest choice: start in guest mode instead of asking
        // for login/register on every launch.
        const val guestMode = "guest_mode"
    }

    private val _defaultServerUrl =
        MutableStateFlow(storage.getString(Keys.defaultServerUrl) ?: "")
    private val _themePreference =
        MutableStateFlow(storage.getString(Keys.themePreference) ?: "system")
    private val _dynamicColor =
        MutableStateFlow((storage.getString(Keys.dynamicColor))?.toBooleanStrictOrNull() ?: true)
    private val _accentHue = MutableStateFlow(storage.getString(Keys.accentHue)?.toIntOrNull())
    private val _guestMode =
        MutableStateFlow(storage.getString(Keys.guestMode)?.toBooleanStrictOrNull() ?: false)

    val defaultServerUrl: Flow<String> = _defaultServerUrl.asStateFlow()
    val themePreference: Flow<String> = _themePreference.asStateFlow()
    val dynamicColor: Flow<Boolean> = _dynamicColor.asStateFlow()

    /** Material You accent seed; null keeps the theme's default hue. */
    val accentHue: Flow<Int?> = _accentHue.asStateFlow()

    /** Guest-mode flow so UI can react to changes live. */
    val guestMode: Flow<Boolean> = _guestMode.asStateFlow()

    suspend fun setDefaultServerUrl(url: String) {
        storage.putString(Keys.defaultServerUrl, url)
        _defaultServerUrl.value = url
    }

    suspend fun setThemePreference(pref: String) {
        storage.putString(Keys.themePreference, pref)
        _themePreference.value = pref
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        storage.putString(Keys.dynamicColor, enabled.toString())
        _dynamicColor.value = enabled
    }

    suspend fun setAccentHue(hue: Int?) {
        if (hue == null) storage.remove(Keys.accentHue) else storage.putString(Keys.accentHue, hue.toString())
        _accentHue.value = hue
    }

    suspend fun setGuestMode(enabled: Boolean) {
        storage.putString(Keys.guestMode, enabled.toString())
        _guestMode.value = enabled
    }

    /** Synchronous snapshot for the early start-destination decision. */
    fun isGuestMode(): Boolean = _guestMode.value

    suspend fun defaultServerUrlOrEmpty(): String = _defaultServerUrl.value
}