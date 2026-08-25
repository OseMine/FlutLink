package com.flutcloud.flutlink

import com.flutcloud.flutlink.core.AccountStore
import com.flutcloud.flutlink.core.AppConfig
import com.flutcloud.flutlink.core.Platform
import com.flutcloud.flutlink.core.SessionManager
import com.flutcloud.flutlink.core.SettingsStore
import com.flutcloud.flutlink.data.FlutCloudApi
import com.flutcloud.flutlink.data.HttpClientFactory
import com.flutcloud.flutlink.data.ListCache
import com.flutcloud.flutlink.data.QuotaCache
import com.flutcloud.flutlink.data.UpdateChecker
import com.flutcloud.flutlink.data.WebDavApi

/**
 * Implemented by the platform Application class so shared UI code can reach
 * the [container] without depending on the concrete Application type
 * (which lives in the :android-app module since the AGP 9 split).
 */
interface ContainerHost {
    val container: AppContainer
}

/**
 * Simple service locator for the app. Fully platform-agnostic: all OS
 * specifics are reached through the injected [Platform].
 */
class AppContainer(
    userAgent: String,
    val config: AppConfig,
    val platform: Platform
) {

    private val httpClient = HttpClientFactory.create(userAgent)

    val settingsStore = SettingsStore(platform.plainStorage())
    val accountStore = AccountStore(
        prefs = platform.plainStorage(),
        securePrefs = platform.secureStorage()
    )
    val sessionManager = SessionManager(accountStore)

    val ocsApi = FlutCloudApi(httpClient)
    val webDavApi = WebDavApi(httpClient)
    val listCache = ListCache(platform.cacheDir())
    val quotaCache = QuotaCache(platform.plainStorage())
    val updateChecker = UpdateChecker(httpClient)

    /** Self-updater available (Settings shows the update row). */
    val updatesSupported: Boolean get() = platform.updatesEnabled

    /** Material You dynamic color available on this device. */
    val supportsDynamicColor: Boolean get() = platform.supportsDynamicColor
}