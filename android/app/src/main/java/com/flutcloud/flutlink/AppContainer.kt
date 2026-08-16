package com.flutcloud.flutlink

import android.content.Context
import com.flutcloud.flutlink.core.AccountStore
import com.flutcloud.flutlink.core.SessionManager
import com.flutcloud.flutlink.core.SettingsStore
import com.flutcloud.flutlink.data.FlutCloudApi
import com.flutcloud.flutlink.data.HttpClientFactory
import com.flutcloud.flutlink.data.ListCache
import com.flutcloud.flutlink.data.Updater
import com.flutcloud.flutlink.data.WebDavApi

/** Simple service locator for the single-activity app. */
class AppContainer(context: Context) {

    private val httpClient = HttpClientFactory.create("FlutLink-Android/${BuildConfig.VERSION_NAME}")

    private val appContext = context.applicationContext

    val settingsStore = SettingsStore(context)
    val accountStore = AccountStore(context)
    val sessionManager = SessionManager(accountStore)

    val ocsApi = FlutCloudApi(httpClient)
    val webDavApi = WebDavApi(httpClient)
    val listCache = ListCache(appContext)
    val updater = Updater(httpClient, appContext)

    /** Cache dir holding the most recent file opened with an external app. */
    fun openCacheDir(): java.io.File = java.io.File(appContext.cacheDir, "opened")
}