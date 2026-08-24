package com.flutcloud.flutlink.data

import com.flutcloud.flutlink.core.KeyValueStorage
import com.flutcloud.flutlink.data.dto.Quota
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Offline cache of the last successfully fetched quota (CP-N4): when the
 * server is unreachable the files screen keeps showing the stored values
 * instead of dropping the row. Keyed per account; failures never propagate —
 * a broken cache entry simply reads as "no cached quota".
 */
class QuotaCache(private val storage: KeyValueStorage) {

    private val json = Json { ignoreUnknownKeys = true }

    fun read(accountKey: String): Quota? {
        val raw = storage.getString("$KEY_PREFIX$accountKey") ?: return null
        return runCatching { json.decodeFromString<Quota>(raw) }.getOrNull()
    }

    fun write(accountKey: String, quota: Quota) {
        runCatching { storage.putString("$KEY_PREFIX$accountKey", json.encodeToString(quota)) }
    }

    companion object {
        private const val KEY_PREFIX = "quota_cache:"
    }
}
