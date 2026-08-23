package com.flutcloud.flutlink.data

import com.flutcloud.flutlink.data.dto.WebDavEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.buffer
import okio.use
import okio.Path

/**
 * Small offline cache for folder listings, mirroring the desktop client's
 * `cache.rs`: every successful listing is persisted under the cache dir
 * (keyed by account + path hash) and served when the server is unreachable.
 * The number of cached listings is capped; the oldest entries are evicted on
 * every write (mtime-based LRU, mirroring `cache.rs::evict_oldest`).
 */
class ListCache(private val baseDir: Path, private val fs: FileSystem = systemFileSystem()) {

    private val json = Json { ignoreUnknownKeys = true }

    private val dir: Path
        get() = baseDir.resolve("cache/listings", normalize = false).also {
            runCatching { fs.createDirectories(it) }
        }

    /** Read the last successful listing for `path`, or null if none was cached. */
    fun read(accountKey: String, path: String): List<WebDavEntry>? {
        val file = fileFor(accountKey, path)
        if (!fs.exists(file)) return null
        val entries = runCatching {
            fs.source(file).buffer().use { src -> json.decodeFromString<CachedListing>(src.readUtf8()) }.entries
        }.getOrNull()
        return entries
    }

    /** Persist a successful listing so it survives offline folder opens. */
    fun write(accountKey: String, path: String, entries: List<WebDavEntry>) {
        try {
            fs.sink(fileFor(accountKey, path)).buffer().use { sink ->
                sink.writeUtf8(json.encodeToString(CachedListing(path, entries)))
            }
            evictOldest()
        } catch (_: Exception) {
            // Cache failures must never break the actual operation.
        }
    }

    private fun fileFor(accountKey: String, path: String): Path =
        dir.resolve("${sha256("$accountKey|$path")}.json")

    /**
     * Remove the oldest cache files so the directory holds at most
     * [MAX_CACHE_ENTRIES] entries (mtime-based LRU, mirroring `cache.rs`).
     */
    private fun evictOldest() {
        val files = runCatching { fs.list(dir) }.getOrDefault(emptyList())
            .filter { fs.metadata(it).isRegularFile && it.name.endsWith(".json") }
            .sortedBy { runCatching { fs.metadata(it).lastModifiedAtMillis }.getOrNull() ?: 0L }
        val remove = files.size - MAX_CACHE_ENTRIES
        if (remove <= 0) return
        files.take(remove).forEach { runCatching { fs.delete(it) } }
    }

    companion object {
        private const val MAX_CACHE_ENTRIES = 500

        /** Deterministic SHA-256 hex of `text`, used as cache file name. */
        internal fun sha256(text: String): String = Sha256.hex(text.encodeToByteArray())
    }
}

@Serializable
private data class CachedListing(val path: String, val entries: List<WebDavEntry>)