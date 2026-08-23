package com.flutcloud.flutlink.core

/** In-memory [KeyValueStorage] so persistence logic is testable on the JVM. */
class InMemoryKeyValueStorage(
    private val backing: MutableMap<String, String> = mutableMapOf()
) : KeyValueStorage {

    override fun getString(key: String): String? = backing[key]

    override fun putString(key: String, value: String) {
        backing[key] = value
    }

    override fun remove(key: String) {
        backing.remove(key)
    }
}
