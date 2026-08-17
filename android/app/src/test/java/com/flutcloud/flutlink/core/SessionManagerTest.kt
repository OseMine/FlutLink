package com.flutcloud.flutlink.core

import com.flutcloud.flutlink.data.AuthSession
import com.flutcloud.flutlink.data.FlutCloudApi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** JVM tests for account switching/removal/sign-out in [SessionManager]. */
class SessionManagerTest {

    private fun store() = AccountStore(
        prefs = InMemorySharedPreferences(),
        securePrefs = InMemorySharedPreferences()
    )

    /**
     * An OCS API that fails fast on any network probe. `init` only uses it for
     * the best-effort admin-flag refresh, which keeps the stored flag when the
     * probe fails — so a short connect timeout keeps the tests hermetic.
     */
    private fun ocsApi() = FlutCloudApi(
        OkHttpClient.Builder()
            .connectTimeout(50, TimeUnit.MILLISECONDS)
            .readTimeout(50, TimeUnit.MILLISECONDS)
            .build()
    )

    private fun meta(
        username: String,
        instance: String = "https://flutcloud.de",
        isActive: Boolean = false
    ) = AccountMeta(username, instance, isActive = isActive)

    private fun session(manager: SessionManager): AuthSession? = manager.session.value

    @Test
    fun `init restores the active account session`() = runBlocking {
        val store = store()
        val admin = meta("admin", isActive = true)
        val bob = meta("bob")
        store.saveAccounts(listOf(admin, bob))
        store.saveToken(admin, "tok-admin")
        store.saveToken(bob, "tok-bob")

        val manager = SessionManager(store)
        manager.init(ocsApi())

        assertEquals(listOf("admin", "bob"), manager.accounts.value.map { it.username })
        assertEquals("admin", session(manager)?.username)
        assertEquals("tok-admin", session(manager)?.token)
        assertEquals("https://flutcloud.de", session(manager)?.baseUrl)
    }

    @Test
    fun `init without accounts leaves session null`() = runBlocking {
        val manager = SessionManager(store())
        manager.init(ocsApi())

        assertTrue(manager.accounts.value.isEmpty())
        assertNull(session(manager))
    }

    @Test
    fun `init does not auto-sign-in when no account is active`() = runBlocking {
        val store = store()
        val bob = meta("bob")
        store.saveAccounts(listOf(bob))
        store.saveToken(bob, "tok-bob")

        val manager = SessionManager(store)
        manager.init(ocsApi())

        assertNull(session(manager))
    }

    @Test
    fun `init skips accounts without a stored token`() = runBlocking {
        val store = store()
        store.saveAccounts(listOf(meta("admin", isActive = true)))

        val manager = SessionManager(store)
        manager.init(ocsApi())

        assertNull(session(manager))
    }

    @Test
    fun `addAccount makes the account active and starts a session`() = runBlocking {
        val manager = SessionManager(store())
        manager.init(ocsApi())

        manager.addAccount(meta("alice"), "tok-alice")

        assertEquals("alice", session(manager)?.username)
        assertEquals("tok-alice", session(manager)?.token)
        assertTrue(manager.accounts.value.first { it.username == "alice" }.isActive)
    }

    @Test
    fun `addAccount replaces an existing account for the same user and instance`() = runBlocking {
        val store = store()
        val admin = meta("admin", isActive = true)
        store.saveAccounts(listOf(admin))
        store.saveToken(admin, "old-token")

        val manager = SessionManager(store)
        manager.init(ocsApi())
        assertEquals("old-token", session(manager)?.token)

        manager.addAccount(meta("admin", instance = "https://flutcloud.de/"), "new-token")

        assertEquals(1, manager.accounts.value.size)
        assertEquals("new-token", session(manager)?.token)
        assertEquals("new-token", store.tokenFor(admin))
    }

    @Test
    fun `switchAccount updates the active flag and session`() = runBlocking {
        val store = store()
        val admin = meta("admin", isActive = true)
        val bob = meta("bob")
        store.saveAccounts(listOf(admin, bob))
        store.saveToken(admin, "tok-admin")
        store.saveToken(bob, "tok-bob")

        val manager = SessionManager(store)
        manager.init(ocsApi())
        assertEquals("admin", session(manager)?.username)

        manager.switchAccount(bob)

        assertEquals("bob", session(manager)?.username)
        assertEquals("tok-bob", session(manager)?.token)
        assertFalse(manager.accounts.value.first { it.username == "admin" }.isActive)
        assertTrue(manager.accounts.value.first { it.username == "bob" }.isActive)
    }

    @Test
    fun `removeAccount deletes the token and switches to the next active account`() = runBlocking {
        val store = store()
        val admin = meta("admin", isActive = true)
        val bob = meta("bob")
        store.saveAccounts(listOf(admin, bob))
        store.saveToken(admin, "tok-admin")
        store.saveToken(bob, "tok-bob")

        val manager = SessionManager(store)
        manager.init(ocsApi())
        assertEquals("admin", session(manager)?.username)

        manager.removeAccount(admin)

        assertEquals(listOf("bob"), manager.accounts.value.map { it.username })
        assertNull(store.tokenFor(admin))
        assertEquals("bob", session(manager)?.username)
        assertEquals("tok-bob", session(manager)?.token)
    }

    @Test
    fun `removeAccount of an inactive account keeps the current session`() = runBlocking {
        val store = store()
        val admin = meta("admin", isActive = true)
        val bob = meta("bob")
        store.saveAccounts(listOf(admin, bob))
        store.saveToken(admin, "tok-admin")
        store.saveToken(bob, "tok-bob")

        val manager = SessionManager(store)
        manager.init(ocsApi())

        manager.removeAccount(bob)

        assertEquals(listOf("admin"), manager.accounts.value.map { it.username })
        assertEquals("admin", session(manager)?.username)
        assertNull(store.tokenFor(bob))
    }

    @Test
    fun `removeAccount clears the session when the last account is removed`() = runBlocking {
        val store = store()
        val admin = meta("admin", isActive = true)
        store.saveAccounts(listOf(admin))
        store.saveToken(admin, "tok-admin")

        val manager = SessionManager(store)
        manager.init(ocsApi())
        assertEquals("admin", session(manager)?.username)

        manager.removeAccount(admin)

        assertTrue(manager.accounts.value.isEmpty())
        assertNull(session(manager))
    }

    @Test
    fun `signOut clears the session but keeps the accounts`() = runBlocking {
        val store = store()
        val admin = meta("admin", isActive = true)
        store.saveAccounts(listOf(admin))
        store.saveToken(admin, "tok-admin")

        val manager = SessionManager(store)
        manager.init(ocsApi())

        manager.signOut()

        assertNull(session(manager))
        assertEquals(1, manager.accounts.value.size)

        manager.switchAccount(admin)
        assertEquals("admin", session(manager)?.username)
    }
}