package com.flutcloud.flutlink.core

import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
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

/** Keeps the document interaction controller alive while its menu is shown. */
internal object IosDocumentInteraction {
    var current: UIDocumentInteractionController? = null
}

/**
 * iOS implementation of [Platform]: NSUserDefaults/Keychain storage, the
 * Files-app Documents directory for downloads and UIActivityViewController /
 * UIDocumentInteractionController for sharing and opening.
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
        return Path(dir)
    }

    override fun cacheDir(): Path {
        val dir = fileManager.temporaryDirectoryPath ?: "$NSHomeDirectory()/tmp"
        fileManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        return Path(dir)
    }

    private fun documentsDir(): String =
        NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull()?.let { it as? String ?: it.toString() }
            ?: "${NSHomeDirectory()}/Documents"

    /**
     * Stream into a unique temp file under Documents/.flutlink-staging, then
     * move it to its final Documents location so the user only ever sees
     * complete files.
     */
    override suspend fun saveToDownloads(fileName: String, write: suspend (Path) -> Unit): String {
        val docs = documentsDir()
        val staging = Path(docs).resolve(".flutlink-staging", normalize = false)
        fileManager.createDirectoryAtPath(docs, withIntermediateDirectories = true, attributes = null, error = null)
        fileManager.createDirectoryAtPath(staging.toString(), withIntermediateDirectories = true, attributes = null, error = null)

        val uniqueTmpName = "${NSDate().timeIntervalSince1970.toLong()}-$fileName.tmp"
        val tmp = staging.resolve(uniqueTmpName, normalize = false)
        write(tmp)

        val target = Path(docs).resolve(fileName, normalize = false)
        fileManager.removeItemAtPath(target.toString(), error = null)
        fileManager.moveItemAtPath(tmp.toString(), toPath = target.toString(), error = null)
        // Nudge the Files app to re-index the directory.
        fileManager.setAttributesOfItemAtPath(
            mapOf<Any?, Any?>(NSFileModificationDate to NSDate()),
            ofItemAtPath = docs,
            error = null
        )
        return target.toString()
    }

    /** Open via the QuickLook-backed document interaction preview. */
    override fun openFile(path: Path): Boolean {
        if (!fileManager.fileExistsAtPath(path.toString())) return false
        val url = NSURL(fileURLWithPath = path.toString())
        val controller = UIDocumentInteractionController.interactionControllerWithURL(url)
        controller.delegate = object : NSObject(), UIDocumentInteractionControllerDelegateProtocol {}
        IosDocumentInteraction.current = controller
        val top = IosPresenter.topViewController() ?: return false
        return controller.presentPreviewAnimated(true) ||
            controller.presentOptionsMenuFromRectInViewAnimated(
                CGRectMake(0.0, 0.0, 1.0, 1.0), top.view, true
            )
    }

    /** System share sheet for a local file URL. */
    override fun shareFile(path: Path): Boolean {
        if (!fileManager.fileExistsAtPath(path.toString())) return false
        val top = IosPresenter.topViewController() ?: return false
        val url = NSURL(fileURLWithPath = path.toString())
        val sheet = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        top.presentViewControllerAnimatedCompletion(sheet, true, null)
        return true
    }
}
