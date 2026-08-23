package com.flutcloud.flutlink.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/** [KeyValueStorage] backed by NSUserDefaults (plain, non-secret data). */
class IosDefaultsStorage(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) : KeyValueStorage {

    override fun getString(key: String): String? =
        defaults.stringForKey(key)

    override fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }
}

/**
 * [KeyValueStorage] backed by the iOS Keychain (generic password items).
 * Holds account tokens; values are device-encrypted and never leave the
 * app's keychain access group.
 */
@OptIn(ExperimentalForeignApi::class)
class IosKeychainStorage(
    private val service: String = "com.flutcloud.flutlink.ios"
) : KeyValueStorage {

    private fun baseQuery(account: String): Map<Any?, Any?> = mapOf(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to service,
        kSecAttrAccount to account
    )

    override fun getString(key: String): String? {
        val query = baseQuery(key) + mapOf(kSecReturnData to kCFBooleanTrue)
        val data = memScoped {
            val out = alloc<ObjCObjectVar<Any?>>()
            val status = SecItemCopyMatching(query, out.ptr)
            if (status == errSecSuccess) out.value as? NSData else null
        } ?: return null
        return NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
    }

    override fun putString(key: String, value: String) {
        remove(key)
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val attributes = baseQuery(key) + mapOf(kSecValueData to data)
        SecItemAdd(attributes, null)
    }

    override fun remove(key: String) {
        SecItemDelete(baseQuery(key))
    }
}
