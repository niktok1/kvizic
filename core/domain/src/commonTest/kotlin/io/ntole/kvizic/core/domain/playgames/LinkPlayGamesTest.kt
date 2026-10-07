package io.ntole.kvizic.core.domain.playgames

import io.ntole.kvizic.core.domain.analytics.Analytics
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.DomainError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.session.CurrentSession
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Signing a device in with Play Games, at launch and from a button. */
class LinkPlayGamesTest {
    private val calls = mutableListOf<String>()
    private val playGames = FakePlayGames(calls)
    private val link = FakeLink(calls)
    private val session = FakeSession()
    private val analytics = RecordingAnalytics(calls)
    private val linking = LinkPlayGames(playGames, link, session, analytics)

    @Test
    fun `a build without Play Games asks it nothing`() =
        runTest {
            playGames.available = false
            session.player.value = "p1"

            assertFalse(linking.automatically())
            assertFalse(linking.manually())
            assertEquals(emptyList(), calls)
            assertFalse(linking.available)
        }

    @Test
    fun `a launch signs the player Play Games signed in to the server with no tap`() =
        runTest {
            session.player.value = "p1"

            assertTrue(linking.automatically())

            assertEquals(listOf("isAuthenticated", "serverAuthCode", "signIn code-1", "identify p1"), calls)
            val signedIn = analytics.events.single()
            assertEquals(AnalyticsEvent.PLAY_GAMES_SIGNED_IN, signedIn.first)
            assertEquals(
                mapOf(AnalyticsProperty.AUTOMATIC to true, AnalyticsProperty.SWITCHED to false),
                signedIn.second,
            )
        }

    /** A first launch mints its guest only as the player starts to play: the sign-in waits for it. */
    @Test
    fun `a launch waits for a session before it signs in`() =
        runTest {
            val launched = async { linking.automatically() }
            testScheduler.advanceUntilIdle()
            assertEquals(emptyList(), calls, "nothing before a session")

            session.player.value = "guest1"

            assertTrue(launched.await())
            assertTrue("signIn code-1" in calls)
        }

    @Test
    fun `a launch on a settled device signs in no more`() =
        runTest {
            session.player.value = "p1"
            link.settled = true

            assertFalse(linking.automatically())
            assertEquals(emptyList(), calls)
        }

    /**
     * The launch's try found the device settled, then its stored session dead (a dev server's data
     * reset): the fresh guest that replaced it unsettled the device, and is signed in at once.
     */
    @Test
    fun `a dead session replaced after the launch's try signs its fresh guest in`() =
        runTest {
            session.player.value = "p1"
            link.settled = true
            backgroundScope.launch { linking.run() }
            testScheduler.runCurrent()
            assertEquals(emptyList(), calls, "settled at launch")

            link.settled = false
            session.player.value = "guest2"
            testScheduler.runCurrent()

            assertEquals(listOf("isAuthenticated", "serverAuthCode", "signIn code-1", "identify guest2"), calls)
        }

    /** A session stored while the app was in the background is signed in as it comes back, and once. */
    @Test
    fun `coming back to the foreground signs in an unsettled session and mints none`() =
        runTest {
            assertFalse(linking.cameToForeground())
            assertEquals(emptyList(), calls, "no session yet: nothing asked")

            session.player.value = "guest2"
            assertTrue(linking.cameToForeground())
            assertFalse(linking.cameToForeground(), "settled by its sign-in")

            assertEquals(listOf("isAuthenticated", "serverAuthCode", "signIn code-1", "identify guest2"), calls)
        }

    /** The session a sign-in stores settles the device: its own session asks Play Games nothing more. */
    @Test
    fun `the session a sign-in stores asks for no second sign-in`() =
        runTest {
            session.player.value = "guest1"
            link.answer = "linked-player"
            backgroundScope.launch { linking.run() }
            testScheduler.runCurrent()

            assertEquals(1, calls.count { it == "serverAuthCode" })
            assertEquals("linked-player", session.current())
        }

    @Test
    fun `a launch with nobody signed in to Play Games asks for no code`() =
        runTest {
            session.player.value = "p1"
            playGames.authenticated = false

            assertFalse(linking.automatically())
            assertEquals(listOf("isAuthenticated"), calls, "and never asks the player to sign in")
            assertEquals(
                listOf<Pair<String, Map<String, Any?>>>(
                    AnalyticsEvent.PLAY_GAMES_SIGN_IN_FAILED to
                        mapOf(
                            AnalyticsProperty.AUTOMATIC to true,
                            AnalyticsProperty.CODE to "NOT_AUTHENTICATED",
                            AnalyticsProperty.REASON to "NO_ACTIVITY",
                        ),
                ),
                analytics.events,
            )
        }

    /** The Play Games player was another player's already: the device is theirs now, and the analytics say so. */
    @Test
    fun `a sign-in as another player is reported as a switch`() =
        runTest {
            session.player.value = "guest1"
            link.answer = "linked-player"

            assertTrue(linking.automatically())

            assertEquals(listOf("isAuthenticated", "serverAuthCode", "signIn code-1", "identify linked-player"), calls)
            assertEquals(true, analytics.events.single().second[AnalyticsProperty.SWITCHED])
        }

    /** What a screen showing the player's name hears: a sign-in that links the player playing keeps their id. */
    @Test
    fun `each sign-in that stores a session is heard by its player and one that stores none is not`() =
        runTest {
            val heard = mutableListOf<String>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { linking.signedIn.collect { heard += it } }
            session.player.value = "guest1"

            assertTrue(linking.automatically())
            link.changedMeanwhile = "other"
            assertFalse(linking.manually())

            assertEquals(listOf("guest1"), heard)
        }

    @Test
    fun `a launch whose sign-in is refused leaves the player as they are and shows nothing`() =
        runTest {
            session.player.value = "p1"
            link.refuseWith = CoreError.PLAY_GAMES_CODE_REFUSED

            assertFalse(linking.automatically())

            assertEquals(listOf("isAuthenticated", "serverAuthCode", "signIn code-1"), calls)
            val failed = analytics.events.single()
            assertEquals(AnalyticsEvent.PLAY_GAMES_SIGN_IN_FAILED, failed.first)
            assertEquals("PLAY_GAMES_CODE_REFUSED", failed.second[AnalyticsProperty.CODE])
        }

    /** The exchange with Google takes seconds: a change of player landing meanwhile is the player's later choice. */
    @Test
    fun `a launch whose answer comes after the device changed player leaves that standing`() =
        runTest {
            session.player.value = "guest1"
            link.changedMeanwhile = "other-player"

            assertFalse(linking.automatically())

            assertEquals(listOf("isAuthenticated", "serverAuthCode", "signIn code-1"), calls, "nobody identified")
            assertEquals(emptyList(), analytics.events)
            assertEquals("other-player", session.current())
        }

    @Test
    fun `a launch Play Games gives no code for signs in to nothing`() =
        runTest {
            session.player.value = "p1"
            playGames.code = null

            assertFalse(linking.automatically())
            assertEquals(listOf("isAuthenticated", "serverAuthCode"), calls)
        }

    @Test
    fun `a button asks the player to sign in to Play Games first`() =
        runTest {
            session.player.value = "p1"
            playGames.authenticated = false
            playGames.signsIn = true

            assertTrue(linking.manually())

            assertEquals(listOf("isAuthenticated", "signIn", "serverAuthCode", "signIn code-1", "identify p1"), calls)
            assertEquals(false, analytics.events.single().second[AnalyticsProperty.AUTOMATIC])
        }

    @Test
    fun `a player Play Games does not sign in sends nothing and is told so`() =
        runTest {
            session.player.value = "p1"
            playGames.authenticated = false

            assertEquals(
                CoreError.PLAY_GAMES_NOT_SIGNED_IN,
                assertFailsWith<KvizicException> { linking.manually() }.error,
            )
            assertEquals(listOf("isAuthenticated", "signIn"), calls)
        }

    /** A settled device signs in all the same when the player asks: a tap is the player's own doing. */
    @Test
    fun `a button signs in on a settled device and says why it could not`() =
        runTest {
            session.player.value = "p1"
            link.settled = true
            link.refuseWith = CoreError.PLAY_GAMES_UNAVAILABLE

            val refused = assertFailsWith<KvizicException> { linking.manually() }

            assertEquals(CoreError.PLAY_GAMES_UNAVAILABLE, refused.error)
            assertEquals(listOf("isAuthenticated", "serverAuthCode", "signIn code-1"), calls)

            playGames.code = null
            assertEquals(
                CoreError.PLAY_GAMES_UNAVAILABLE,
                assertFailsWith<KvizicException> { linking.manually() }.error,
            )
        }

    private class FakePlayGames(
        private val calls: MutableList<String>,
    ) : PlayGames {
        override var available = true
        var authenticated = true
        var signsIn = false
        var code: String? = "code-1"
        override var lastFailure: String? = "NO_ACTIVITY"

        override suspend fun isAuthenticated(): Boolean {
            calls += "isAuthenticated"
            return authenticated
        }

        override suspend fun signIn(): Boolean {
            calls += "signIn"
            authenticated = signsIn
            return signsIn
        }

        override suspend fun serverAuthCode(): String? {
            calls += "serverAuthCode"
            return code
        }
    }

    private inner class FakeLink(
        private val calls: MutableList<String>,
    ) : PlayGamesRepository {
        var settled = false
        var refuseWith: DomainError? = null

        /** The player the server signs in as: the one playing, unless the Play Games player was another's. */
        var answer: String? = null

        /** When set, the device became this player while the sign-in was in flight. */
        var changedMeanwhile: String? = null

        override fun isSettled(): Boolean = settled

        override suspend fun signIn(serverAuthCode: String): String? {
            calls += "signIn $serverAuthCode"
            refuseWith?.let { throw KvizicException(it) }
            changedMeanwhile?.let {
                session.player.value = it
                settled = true
                return null
            }
            val player = answer ?: session.player.value ?: "minted"
            session.player.value = player
            settled = true
            return player
        }
    }

    private class FakeSession : CurrentSession {
        val player = MutableStateFlow<String?>(null)

        override fun current(): String? = player.value

        override val sessions: Flow<String> = player.filterNotNull()
    }

    private class RecordingAnalytics(
        private val calls: MutableList<String>,
    ) : Analytics by Analytics.None {
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()

        override fun identify(playerId: String) {
            calls += "identify $playerId"
        }

        override fun track(
            event: String,
            properties: Map<String, Any?>,
        ) {
            events += event to properties
        }
    }
}
