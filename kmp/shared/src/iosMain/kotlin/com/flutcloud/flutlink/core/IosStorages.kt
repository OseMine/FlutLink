package com.flutcloud.flutlink.core

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.NSUserDefaults
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
    private val suiteName: String? = null
) : KeyValueStorage {

    private val defaults = suiteName?.let { NSUserDefaults(suiteName = it) }
        ?: NSUserDefaults.standardUserDefaults

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
 * Holds account tokens; values are device-encrypted and scoped to the app's
 * keychain access group.
 */
@OptIn(ExperimentalForeignApi::class)
class IosKeychainStorage(
    private val service: String = "com.flutcloud.flutlink.ios"
) : KeyValueStorage {

    override fun getString(key: String): String? = memScoped {
        val query = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cfString(service),
            kSecAttrAccount to cfString(key),
            kSecReturnData to kCFBooleanTrue
        )
        val out = alloc<CFTypeRefVar>()
        if (SecItemCopyMatching(query, out.ptr) != errSecSuccess) return@memScoped null
        @Suppress("UNCHECKED_CAST")
        val data = out.value as CFDataRef ?: return@memScoped null
        val length = CFDataGetLength(data)
        val bytes = CFDataGetBytePtr(data) ?: return@memScoped null
        bytes.readBytes(length.toInt())?.decodeToString()
    }

    override fun putString(key: String, value: String) {
        remove(key)
        memScoped {
            val bytes = value.encodeToByteArray()
            val data = CFDataCreate(null, bytes.toCValues(), bytes.size.toLong())
                ?: return@memScoped
            val attributes = cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to cfString(service),
                kSecAttrAccount to cfString(key),
                kSecValueData to data
            )
            SecItemAdd(attributes, null)
        }
    }

    override fun remove(key: String) {
        memScoped {
            val query = cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to cfString(service),
                kSecAttrAccount to cfString(key)
            )
            SecItemDelete(query)
        }
    }

    /**
     * Build a CFDictionary of raw CF pointers (strings / booleans / CFData).
     * NULL callbacks: the dictionary does not retain — every referenced value
     * stays alive for the duration of the synchronous Security call.
     */
    private fun MemScope.cfDictionaryOf(
        vararg pairs: Pair<CFStringRef?, CFTypeRef?>
    ): CFDictionaryRef? {
        val count = pairs.size
        val keys = allocArray<CPointerVar<COpaquePointer>>(count)
        val values = allocArray<CPointerVar<COpaquePointer>>(count)
        pairs.forEachIndexed { index, (key, value) ->
            keys[index] = key
            value?.let { values[index] = it }
        }
        return CFDictionaryCreate(
            allocator = null,
            keys = keys,
            values = values,
            numEntries = count.toLong(),
            keyCallBacks = null,
            valueCallBacks = null
        )
    }

    private fun cfString(text: String): CFStringRef =
        CFStringCreateWithCString(null, text, kCFStringEncodingUTF8)
}
