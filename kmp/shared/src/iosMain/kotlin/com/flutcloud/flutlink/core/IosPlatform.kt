package com.flutcloud.flutlink.core

import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUUID
import platform.Foundation.NSTemporaryDirectory
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/** Presents system dialogs from the Compose root view controller. */
internal object IosPresenter {
    lateinit var hostController: UIViewController

    fun topViewController(): UIViewController? {
        var current: UIViewController = hostController
        while (true) {
            val presented = current.presentedViewController ?: break
            current = presented
        }
        return current
    }
}

/** Keeps the document interaction controller alive while its preview is shown. */
internal object IosDocumentInteraction {
    var current: UIDocumentInteractionController? = null
}

/**
 * iOS implementation of [Platform]: NSUserDefaults/Keychain storage, the
 * Files-app Documents directory for downloads and QuickLook / share sheet
 * for opening and sharing.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPlatform : Platform {

    override val name: String get() = "iOS"

    private val fileManager = NSFileManager.defaultManager

    override fun plainStorage(): KeyValueStorage = IosDefaultsStorage()

    override fun secureStorage(): KeyValueStorage = IosKeychainStorage()

    /** App Documents dir — visible to the user in the Files app. */
    override fun appFilesDir(): Path {
        val dir = documentsDir()
        fileManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        return dir.toPath()
    }

    override fun cacheDir(): Path {
        val dir = NSTemporaryDirectory() + "/FlutLink"
        fileManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        return dir.toPath()
    }

    private fun documentsDir(): String =
        NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull()?.toString()
            ?: "${NSHomeDirectory()}/Documents"

    /**
     * Stream into a unique temp file under Documents/.flutlink-staging, then
     * move it to its final Documents location so the user only ever sees
     * complete files.
     */
    override suspend fun saveToDownloads(fileName: String, write: suspend (Path) -> Unit): String {
        val docs = documentsDir()
        val staging = docs.toPath().resolve(".flutlink-staging", normalize = false)
        fileManager.createDirectoryAtPath(docs, withIntermediateDirectories = true, attributes = null, error = null)
        fileManager.createDirectoryAtPath(staging.toString(), withIntermediateDirectories = true, attributes = null, error = null)

        val uniqueTmpName = "${NSUUID().UUIDString}-$fileName.tmp"
        val tmp = staging.resolve(uniqueTmpName, normalize = false)
        write(tmp)

        val target = docs.toPath().resolve(fileName, normalize = false)
        fileManager.removeItemAtPath(target.toString(), error = null)
        fileManager.moveItemAtPath(tmp.toString(), toPath = target.toString(), error = null)
        return target.toString()
    }

    /** Open via the QuickLook-backed document interaction preview. */
    override fun openFile(path: Path): Boolean {
        if (!fileManager.fileExistsAtPath(path.toString())) return false
        val top = IosPresenter.topViewController() ?: return false
        val url = NSURL(fileURLWithPath = path.toString())
        val controller = UIDocumentInteractionController.interactionControllerWithURL(url)
        controller.delegate = object : NSObject(), UIDocumentInteractionControllerDelegateProtocol {}
        IosDocumentInteraction.current = controller
        return controller.presentPreviewAnimated(true)
    }

    /** System share sheet for a local file URL. */
    override fun shareFile(path: Path): Boolean {
        if (!fileManager.fileExistsAtPath(path.toString())) return false
        val top = IosPresenter.topViewController() ?: return false
        val url = NSURL(fileURLWithPath = path.toString())
        val sheet = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        top.presentViewController(sheet, animated = true, completion = null)
        return true
    }
}
