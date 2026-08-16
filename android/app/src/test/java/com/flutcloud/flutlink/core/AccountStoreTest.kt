package com.flutcloud.flutlink.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM roundtrip tests for [AccountStore] on in-memory preferences. */
class AccountStoreTest {

    private val store = AccountStore(
        prefs = InMemorySharedPreferences(),
        securePrefs = InMemorySharedPreferences()
    )

    @Test
    fun `loads empty when nothing was stored`() {
        assertTrue(store.loadAccounts().isEmpty())
        assertNull(store.tokenFor(account("admin")))
    }

    @Test
    fun `roundtrips account metadata`() {
        val accounts = listOf(
            account("admin", "https://flutcloud.de/", isAdmin = true, isActive = true),
            account("bob", "https://flutcloud.de/")
        )
        store.saveAccounts(accounts)

        val loaded = store.loadAccounts()
        assertEquals(2, loaded.size)
        assertEquals("admin", loaded[0].username)
        assertEquals("https://flutcloud.de/", loaded[0].instanceUrl)
        assertTrue(loaded[0].isAdmin)
        assertTrue(loaded[0].isActive)
        assertFalse(loaded[1].isActive)
    }

    @Test
    fun `overwrites previously stored accounts`() {
        store.saveAccounts(listOf(account("admin")))
        store.saveAccounts(listOf(account("bob")))
        assertEquals(listOf("bob"), store.loadAccounts().map { it.username })
    }

    @Test
    fun `roundtrips and deletes tokens`() {
        val meta = account("admin")
        assertNull(store.tokenFor(meta))

        store.saveToken(meta, "secret-token")
        assertEquals("secret-token", store.tokenFor(meta))

        store.deleteToken(meta)
        assertNull(store.tokenFor(meta))
    }

    @Test
    fun `tokens are keyed per account and instance`() {
        val a = account("admin", "https://flutcloud.de/")
        val b = account("admin", "https://other.cloud/")
        store.saveToken(a, "token-a")
        store.saveToken(b, "token-b")

        assertEquals("token-a", store.tokenFor(a))
        assertEquals("token-b", store.tokenFor(b))

        store.deleteToken(a)
        assertNull(store.tokenFor(a))
        assertEquals("token-b", store.tokenFor(b))
    }

    @Test
    fun `account key normalizes trailing slash`() {
        assertEquals(
            account("admin", "https://flutcloud.de/").key,
            account("admin", "https://flutcloud.de").key
        )
    }

    @Test
    fun `corrupt stored json falls back to empty list`() {
        store.saveAccounts(listOf(account("admin")))
        // Simulate a corrupt payload in the backing preferences.
        val prefs = InMemorySharedPreferences()
        prefs.edit().putString("accounts_meta", "{not valid json").apply()
        val corrupt = AccountStore(prefs, InMemorySharedPreferences())

        assertTrue(corrupt.loadAccounts().isEmpty())
    }

    private fun account(
        username: String,
        instanceUrl: String = "https://flutcloud.de",
        isAdmin: Boolean = false,
        isActive: Boolean = false
    ) = AccountMeta(
        username = username,
        instanceUrl = instanceUrl,
        displayName = null,
        isAdmin = isAdmin,
        isActive = isActive
    )
}