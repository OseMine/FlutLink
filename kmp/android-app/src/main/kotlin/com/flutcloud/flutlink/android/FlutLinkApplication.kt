package com.flutcloud.flutlink.android

import android.app.Application
import com.flutcloud.flutlink.AppContainer
import com.flutcloud.flutlink.ContainerHost
import com.flutcloud.flutlink.core.AndroidPlatform
import com.flutcloud.flutlink.core.AppConfig

class FlutLinkApplication : Application(), ContainerHost {

    override lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            userAgent = "FlutLink-Android/${BuildConfig.VERSION_NAME}",
            config = AppConfig(
                defaultServerUrl = BuildConfig.FLUTCLOUD_URL,
                appVersion = BuildConfig.VERSION_NAME
            ),
            platform = AndroidPlatform(this)
        )
    }
}
