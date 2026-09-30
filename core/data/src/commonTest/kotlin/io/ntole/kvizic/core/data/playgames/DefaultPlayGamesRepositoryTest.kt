package io.ntole.kvizic.core.data.playgames

import io.ktor.http.HttpStatusCode
import io.ntole.kvizic.core.data.BASE_URL
import io.ntole.kvizic.core.data.FakeServer
import io.ntole.kvizic.core.data.account.DefaultAccountRepository
import io.ntole.kvizic.core.data.player.DefaultPlayerRepository
import io.ntole.kvizic.core.data.session
import io.ntole.kvizic.core.data.session.DefaultSessionRepository
import io.ntole.kvizic.core.data.session.PlayGamesSettled
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.player.NameSource
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Signing in with Play Games through the real client, against [FakeServer]. */
class DefaultPlayGamesRepositoryTest {
    private val server = FakeServer()
    private val storage = InMemoryTokenStorage()
    private val store = SessionStore(storage, KvizicEnvironment.DEV)
    private val client = KvizicHttpClient.create(BASE_URL, store, server.engine)
    private val sessions =
        DefaultSessionRepository(AuthApi(client), store, PlayGamesSettled(storage, KvizicEnvironment.DEV))
    private val playGames = DefaultPlayGamesRepository(AuthApi(client), sessions)
    private val players = DefaultPlayerRepository(PlayerApi(client), sessions)

    @Test
    fun `a Play Games player linked to nobody is linked to the guest playing who keeps everything`() =
        runTest {
            sessions.ensure()
            server.playGamesCodes["code-1"] = "gp-1"
            assertFalse(playGames.isSettled(), "a fresh install is unsettled")

            val player = playGames.signIn("code-1")

            assertEquals("guest1", player)
            assertEquals<List<Pair<String?, String>>>(
                listOf("Bearer access-guest1" to "code-1"),
                server.playGamesSentAs,
            )
            assertEquals("guest1", store.read()?.playerId, "a new session of the same player")
            assertTrue(playGames.isSettled())
            val profile = players.profile()
            assertTrue(profile.playGamesLinked)
            assertEquals(NameSource.PLAY_GAMES, profile.nameSource, "the name is Play Games' from now on")
            assertNotNull(storage.read("kvizic.playgames.settled.dev"), "kept for DEV's server alone")
            assertNull(storage.read("kvizic.playgames.settled.prod"))
        }

    @Test
    fun `a Play Games player linked already signs in as its player`() =
        runTest {
            sessions.ensure()
            server.playGamesLinks["gp-1"] = "linked-player"
            server.playGamesCodes["code-1"] = "gp-1"

            assertEquals("linked-player", playGames.signIn("code-1"))
            assertEquals("linked-player", store.read()?.playerId)
        }

    @Test
    fun `a code Google refuses changes nothing and settles nothing`() =
        runTest {
            sessions.ensure()
            val before = store.read()

            val refused = assertFailsWith<KvizicException> { playGames.signIn("spent") }

            assertEquals(CoreError.PLAY_GAMES_CODE_REFUSED, refused.error)
            assertEquals(before, store.read())
            assertFalse(playGames.isSettled())
        }

    @Test
    fun `a server that cannot ask Google is PLAY_GAMES_UNAVAILABLE`() =
        runTest {
            sessions.ensure()
            server.refusePlayGamesWith = HttpStatusCode.BadGateway to ErrorCode.PLAY_GAMES_UNAVAILABLE

            val refused = assertFailsWith<KvizicException> { playGames.signIn("code-1") }

            assertEquals(CoreError.PLAY_GAMES_UNAVAILABLE, refused.error)
            assertFalse(playGames.isSettled())
        }

    /** A dead session is a 401 before the code is spent, so the retry sends it as the fresh guest. */
    @Test
    fun `a sign-in on a dead session sends the code again as the fresh guest`() =
        runTest {
            store.write(session("dead"))
            server.playGamesCodes["code-1"] = "gp-1"

            assertEquals("guest1", playGames.signIn("code-1"))
            assertEquals(listOf("Bearer access-dead", "Bearer access-guest1"), server.playGamesSentAs.map { it.first })
            assertTrue(playGames.isSettled())
        }

    /**
     * The exchange with Google takes seconds, more after a cold start: a logout landing meanwhile is the
     * player's later choice, which the answer does not undo.
     */
    @Test
    fun `a sign-in answered after a logout stores nothing`() =
        runTest {
            sessions.ensure()
            server.playGamesCodes["code-1"] = "gp-1"
            server.whilePlayGamesExchanges = { sessions.clear() }

            assertNull(playGames.signIn("code-1"))
            assertNull(store.read(), "logged out, the fresh guest minted by the next call")
        }

    /** A logout and a deletion are the player's doing, as a sign-in is; a dead session replaced is nobody's. */
    @Test
    fun `a logout and a deletion settle who plays here and a dead session forgets it`() =
        runTest {
            val accounts = DefaultAccountRepository(AuthApi(client), PlayerApi(client), sessions)
            sessions.ensure()

            sessions.resetIfStill(store.read())
            assertFalse(playGames.isSettled(), "after the dead session was replaced")

            accounts.logOut()
            assertTrue(playGames.isSettled(), "after a logout")
            sessions.ensure()
            assertTrue(playGames.isSettled(), "the logout's fresh guest")

            sessions.resetIfStill(store.read())
            accounts.deleteAccount()
            assertTrue(playGames.isSettled(), "after a deletion")
        }
}
