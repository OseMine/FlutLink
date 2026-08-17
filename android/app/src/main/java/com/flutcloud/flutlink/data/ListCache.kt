package com.flutcloud.flutlink.data

import android.content.Context
import com.flutcloud.flutlink.data.dto.WebDavEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Small offline cache for folder listings, mirroring the desktop client's
 * `cache.rs`: every successful listing is persisted under the app files dir
 * (keyed by account + path hash) and served when the server is unreachable.
 */
class ListCache(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val dir: File
        get() = File(context.filesDir, "cache/listings").apply { mkdirs() }

    /** Read the last successful listing for `path`, or null if none was cached. */
    fun read(accountKey: String, path: String): List<WebDavEntry>? {
        val file = File(dir, "${sha256("$accountKey|$path")}.json")
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<CachedListing>(file.readText()).entries
        }.getOrNull()
    }

    /** Persist a successful listing so it survives offline folder opens. */
    fun write(accountKey: String, path: String, entries: List<WebDavEntry>) {
        val file = File(dir, "${sha256("$accountKey|$path")}.json")
        file.writeText(json.encodeToString(CachedListing(path, entries)))
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

@Serializable
private data class CachedListing(val path: String, val entries: List<WebDavEntry>)