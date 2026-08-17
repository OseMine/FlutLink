package com.flutcloud.flutlink

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.flutcloud.flutlink.core.AccountStore
import com.flutcloud.flutlink.core.SessionManager
import com.flutcloud.flutlink.core.SettingsStore
import com.flutcloud.flutlink.data.FlutCloudApi
import com.flutcloud.flutlink.data.HttpClientFactory
import com.flutcloud.flutlink.data.ListCache
import com.flutcloud.flutlink.data.Updater
import com.flutcloud.flutlink.data.WebDavApi
import java.io.InputStream

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

    /** App-specific files dir for downloads (survives app updates). */
    fun appFilesDir(): java.io.File = appContext.filesDir

    /** Query the size of a SAF content [Uri], null when the provider reports none. */
    fun contentSize(uri: Uri): Long? =
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) cursor.getLong(idx) else null
        }

    /** Open an InputStream for a SAF content [Uri] (streamed, never read fully). */
    fun openContentStream(uri: Uri): InputStream? =
        appContext.contentResolver.openInputStream(uri)

    /** Fallback for providers without a reported size: read the whole [Uri] into memory. */
    fun readAllBytes(uri: Uri): ByteArray =
        openContentStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
}