package com.flutcloud.flutlink

import androidx.compose.ui.window.ComposeUIViewController
import com.flutcloud.flutlink.core.AppConfig
import com.flutcloud.flutlink.core.IosPlatform
import com.flutcloud.flutlink.core.IosPresenter
import com.flutcloud.flutlink.ui.FlutLinkRoot
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

/**
 * iOS entry point used by the `iosApp/` Xcode shell: hosts the full shared
 * Compose UI (same feature set as the Android client) via [FlutLinkRoot].
 *
 * `enforceStrictPlistSanityCheck = false` disables CMP 1.11's runtime check
 * for UILaunchScreen/UIApplicationSceneManifest in Info.plist — the Xcode
 * shell generates the plist from INFOPLIST_KEY_* build settings, which the
 * check does not recognize (false-positive crash at launch).
 */
fun MainViewController(): UIViewController {
    val platform = IosPlatform()
    val version = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String
        ?: "1.0.0"
    val container = AppContainer(
        userAgent = "FlutLink-iOS/$version",
        config = AppConfig(
            // No compile-time server URL on iOS yet; the user enters it once.
            defaultServerUrl = "",
            appVersion = version
        ),
        platform = platform
    )
    val controller = ComposeUIViewController(
        configure = { enforceStrictPlistSanityCheck = false }
    ) {
        FlutLinkRoot(container)
    }
    IosPresenter.hostController = controller
    return controller
}
