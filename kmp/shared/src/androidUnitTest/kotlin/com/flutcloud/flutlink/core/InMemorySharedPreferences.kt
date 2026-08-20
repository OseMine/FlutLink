package com.flutcloud.flutlink.core

import android.content.SharedPreferences

/** In-memory [SharedPreferences] so persistence logic is testable on the JVM. */
class InMemorySharedPreferences(
    private val backing: MutableMap<String, Any?> = mutableMapOf()
) : SharedPreferences {

    override fun getAll(): MutableMap<String, *> = backing.toMutableMap()

    override fun getString(key: String, defValue: String?): String? =
        backing[key] as? String ?: defValue

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (backing[key] as? Set<*>)?.map { it as String }?.toMutableSet() ?: defValues

    override fun getInt(key: String, defValue: Int): Int = backing[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = backing[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = backing[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        backing[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = backing.containsKey(key)

    override fun edit(): SharedPreferences.Editor = InMemoryEditor(backing)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    private class InMemoryEditor(
        private val backing: MutableMap<String, Any?>
    ) : SharedPreferences.Editor {

        private val staged = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            staged[key] = value
            return this
        }

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?
        ): SharedPreferences.Editor {
            staged[key] = values
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            staged[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            staged[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            staged[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            staged[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            removed += key
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            staged.clear()
            removed.clear()
            backing.clear()
            return this
        }

        override fun commit(): Boolean = applyAndReturn()

        override fun apply() {
            applyAndReturn()
        }

        private fun applyAndReturn(): Boolean {
            backing.putAll(staged)
            removed.forEach { backing.remove(it) }
            staged.clear()
            removed.clear()
            return true
        }
    }
}