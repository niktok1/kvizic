package io.ntole.kvizic.core.data.account

import io.ktor.http.HttpStatusCode
import io.ntole.kvizic.core.data.BASE_URL
import io.ntole.kvizic.core.data.FakeServer
import io.ntole.kvizic.core.data.session
import io.ntole.kvizic.core.data.session.DefaultSessionRepository
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.network.InMemoryTokenStorage
import io.ntole.kvizic.core.network.KvizicHttpClient
import io.ntole.kvizic.core.network.SessionStore
import io.ntole.kvizic.core.network.api.AuthApi
import io.ntole.kvizic.core.network.api.PlayerApi
import io.ntole.kvizic.core.network.environment.KvizicEnvironment
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Logging out and deleting the account through the real client, against [FakeServer]. */
class DefaultAccountRepositoryTest {
    private val server = FakeServer()
    private val store = SessionStore(InMemoryTokenStorage(), KvizicEnvironment.LOCAL)
    private val client = KvizicHttpClient.create(BASE_URL, store, server.engine)
    private val sessions = DefaultSessionRepository(AuthApi(client), store)
    private val accounts = DefaultAccountRepository(AuthApi(client), PlayerApi(client), sessions)

    @Test
    fun `a deletion goes as the player and leaves the next call a fresh guest`() =
        runTest {
            sessions.ensure()

            accounts.deleteAccount()

            assertEquals(listOf<String?>("Bearer access-guest1"), server.deletionsSentAs)
            assertNull(store.read())
            assertEquals("guest2", sessions.ensure())
        }

    /** The account went already, from another device or by an answer that never arrived. */
    @Test
    fun `a deletion the server refuses as a player it no longer has counts as done`() =
        runTest {
            store.write(session("gone"))

            accounts.deleteAccount()

            assertEquals(listOf<String?>("Bearer access-gone"), server.deletionsSentAs, "sent once, never retried")
            assertNull(store.read())
            assertEquals(0, server.guestsMinted, "no guest minted to delete in its place")
        }

    @Test
    fun `a deletion that failed otherwise forgets nothing`() =
        runTest {
            sessions.ensure()
            val before = store.read()
            server.refuseDeletionsWith = HttpStatusCode.TooManyRequests to ErrorCode.RATE_LIMITED

            val failure = assertFailsWith<KvizicException> { accounts.deleteAccount() }

            assertEquals(CoreError.RATE_LIMITED, failure.error)
            assertEquals(before, store.read())
        }

    @Test
    fun `with no session stored a deletion sends nothing`() =
        runTest {
            accounts.deleteAccount()

            assertEquals(emptyList(), server.deletionsSentAs)
            assertEquals(0, server.guestsMinted)
        }

    @Test
    fun `a logout ends the session on the server and here`() =
        runTest {
            sessions.ensure()

            accounts.logOut()

            assertEquals(listOf<String?>("Bearer access-guest1"), server.logoutsSentAs)
            assertNull(store.read())
        }

    /** Best effort: the device forgets its session whatever the server answered. */
    @Test
    fun `a logout the server refuses still forgets the session`() =
        runTest {
            sessions.ensure()
            server.refuseLogoutsWith = HttpStatusCode.ServiceUnavailable to ErrorCode.INTERNAL

            accounts.logOut()

            assertNull(store.read())
        }

    @Test
    fun `with no session stored a logout sends nothing`() =
        runTest {
            accounts.logOut()

            assertEquals(emptyList(), server.logoutsSentAs)
        }
}
