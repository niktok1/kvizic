package io.ntole.kvizic.home

import io.ntole.kvizic.analytics.RecordingAnalytics
import io.ntole.kvizic.core.domain.analytics.Analytics
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.player.GetProfile
import io.ntole.kvizic.core.domain.player.NameSource
import io.ntole.kvizic.core.domain.player.PlayerRepository
import io.ntole.kvizic.core.domain.player.PlayerStats
import io.ntole.kvizic.core.domain.player.Profile
import io.ntole.kvizic.core.domain.player.SetAvatar
import io.ntole.kvizic.core.domain.playgames.LinkPlayGames
import io.ntole.kvizic.core.domain.playgames.PlayGames
import io.ntole.kvizic.core.domain.playgames.PlayGamesRepository
import io.ntole.kvizic.core.domain.session.CurrentSession
import io.ntole.kvizic.core.domain.session.SessionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** The placeholder Home's player: read when shown, and again when another player plays or Play Games names them. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val main: TestDispatcher = StandardTestDispatcher()
    private val session = FakeSession()
    private val players = FakePlayers(session)
    private val analytics = RecordingAnalytics()
    private val links = LinksPlayerPlaying(session, players)
    private val linking = LinkPlayGames(SignedInPlayGames, links, session, Analytics.None)

    /** Less than the wait an avatar picked stands before it is sent. */
    private val halfASettle = 1.5.seconds

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a first launch mints a guest and shows their profile once`() =
        runTest(main) {
            val home = viewModel()

            home.shown()
            testScheduler.advanceUntilIdle()

            assertEquals(
                "guest1",
                home.state.value.profile
                    ?.playerId,
            )
            assertEquals(1, players.reads, "the guest minted is heard, and read no second time")
        }

    /** A launch's Play Games sign-in makes the device the linked player: their name, not the guest's. */
    @Test
    fun `the device becoming another player drops the name shown and reads theirs`() =
        runTest(main) {
            val home = viewModel()
            home.shown()
            testScheduler.advanceUntilIdle()
            val reading = CompletableDeferred<Unit>()
            players.hold = reading

            session.player.value = "linked"
            testScheduler.runCurrent()

            assertNull(home.state.value.profile, "the guest's name is gone while the linked player's is read")
            reading.complete(Unit)
            testScheduler.advanceUntilIdle()
            assertEquals(
                "linked",
                home.state.value.profile
                    ?.playerId,
            )
        }

    /** A launch's Play Games sign-in that links the guest playing: the same player, with Play Games' name. */
    @Test
    fun `a Play Games sign-in that links the player shown reads the name it gave them`() =
        runTest(main) {
            val home = viewModel()
            home.shown()
            testScheduler.advanceUntilIdle()

            players.name = "TrolePro"
            assertTrue(linking.automatically())
            testScheduler.advanceUntilIdle()

            assertEquals(
                "TrolePro",
                home.state.value.profile
                    ?.displayName,
            )
            assertEquals(2, players.reads)
        }

    /** The read out when the sign-in lands asked before it, so came back with the name before it. */
    @Test
    fun `a Play Games sign-in during a read reads the name again once it is back`() =
        runTest(main) {
            val home = viewModel()
            home.shown()
            testScheduler.advanceUntilIdle()
            val reading = CompletableDeferred<Unit>()
            players.hold = reading
            home.shown()
            testScheduler.runCurrent()

            players.name = "TrolePro"
            assertTrue(linking.automatically())
            reading.complete(Unit)
            testScheduler.advanceUntilIdle()

            assertEquals(
                "TrolePro",
                home.state.value.profile
                    ?.displayName,
            )
        }

    @Test
    fun `a read that fails says why and is reported and Try again reads again`() =
        runTest(main) {
            val home = viewModel()
            players.failWith = KvizicException(CoreError.RATE_LIMITED, retryAfter = 42.seconds)

            home.shown()
            testScheduler.advanceUntilIdle()

            assertEquals(HomeFailure(CoreError.RATE_LIMITED, 42.seconds), home.state.value.failure)
            val shown = analytics.named(AnalyticsEvent.ERROR_SHOWN).single()
            assertEquals(
                mapOf(AnalyticsProperty.CODE to "RATE_LIMITED", AnalyticsProperty.ACTION to "profile"),
                shown.properties,
            )

            players.failWith = null
            home.retry()
            testScheduler.advanceUntilIdle()

            assertNull(home.state.value.failure)
            assertEquals(
                "guest1",
                home.state.value.profile
                    ?.playerId,
            )
        }

    @Test
    fun `an avatar picked is the profile's once the server has it`() =
        runTest(main) {
            val home = viewModel()
            home.shown()
            testScheduler.advanceUntilIdle()

            home.changeAvatar("frog")
            assertEquals("frog", home.state.value.changingAvatar)
            testScheduler.advanceUntilIdle()

            assertEquals(
                "frog",
                home.state.value.profile
                    ?.avatarId,
            )
            assertNull(home.state.value.changingAvatar)
        }

    /** Trying every avatar spent the server's budget for picks, a request a tap, and then waited an hour. */
    @Test
    fun `avatars tried one after another send only the one the player settles on`() =
        runTest(main) {
            val home = viewModel()
            home.shown()
            testScheduler.advanceUntilIdle()

            listOf("frog", "wolf", "bear").forEach { avatar ->
                home.changeAvatar(avatar)
                testScheduler.advanceTimeBy(halfASettle)
            }
            assertEquals("bear", home.state.value.changingAvatar, "the pick shows before it is sent")
            assertEquals(emptyList(), players.avatarsSent)

            testScheduler.advanceUntilIdle()
            assertEquals(listOf("bear"), players.avatarsSent)
            assertEquals(
                "bear",
                home.state.value.profile
                    ?.avatarId,
            )
        }

    @Test
    fun `an avatar picked is sent at once when the profile is left`() =
        runTest(main) {
            val home = viewModel()
            home.shown()
            testScheduler.advanceUntilIdle()

            home.changeAvatar("frog")
            home.keepAvatar()
            testScheduler.runCurrent()

            assertEquals(listOf("frog"), players.avatarsSent)
            testScheduler.advanceUntilIdle()
            assertEquals(listOf("frog"), players.avatarsSent, "the wait over sends nothing more")
        }

    @Test
    fun `picking back the avatar the server has sends nothing`() =
        runTest(main) {
            val home = viewModel()
            home.shown()
            testScheduler.advanceUntilIdle()

            home.changeAvatar("frog")
            home.changeAvatar("hedgehog")
            testScheduler.advanceUntilIdle()

            assertEquals(emptyList(), players.avatarsSent)
            assertNull(home.state.value.changingAvatar)
        }

    @Test
    fun `an avatar picked while one is sent goes after it so the server keeps the last`() =
        runTest(main) {
            val home = viewModel()
            home.shown()
            testScheduler.advanceUntilIdle()
            val sending = CompletableDeferred<Unit>()
            players.avatarHold = sending
            home.changeAvatar("frog")
            home.keepAvatar()
            testScheduler.runCurrent()

            home.changeAvatar("wolf")
            home.keepAvatar()
            testScheduler.runCurrent()
            assertEquals("wolf", home.state.value.changingAvatar)
            sending.complete(Unit)
            testScheduler.advanceUntilIdle()

            assertEquals(listOf("frog", "wolf"), players.avatarsSent)
            assertEquals("wolf", players.avatar)
            assertEquals(
                "wolf",
                home.state.value.profile
                    ?.avatarId,
            )
            assertNull(home.state.value.changingAvatar)
        }

    @Test
    fun `an avatar the server refuses says why and keeps the one before`() =
        runTest(main) {
            val home = viewModel()
            home.shown()
            testScheduler.advanceUntilIdle()
            players.avatarFailWith = KvizicException(CoreError.NETWORK)

            home.changeAvatar("frog")
            testScheduler.advanceUntilIdle()

            assertEquals(
                "hedgehog",
                home.state.value.profile
                    ?.avatarId,
            )
            assertEquals(
                CoreError.NETWORK,
                home.state.value.avatarFailure
                    ?.error,
            )
        }

    @Test
    fun `a guest signs in with Play Games from Home and is shown linked`() =
        runTest(main) {
            val home = viewModel()
            home.shown()
            testScheduler.advanceUntilIdle()
            assertTrue(home.state.value.offersPlayGames, "a guest is offered Play Games")

            home.linkPlayGames()
            testScheduler.advanceUntilIdle()

            assertEquals(
                true,
                home.state.value.profile
                    ?.playGamesLinked,
            )
            assertFalse(home.state.value.offersPlayGames, "a linked player is offered it no more")
            assertFalse(home.state.value.linkingPlayGames)
        }

    @Test
    fun `a Play Games sign-in the server refuses says why until taken down`() =
        runTest(main) {
            val home = viewModel()
            home.shown()
            testScheduler.advanceUntilIdle()
            links.failWith = KvizicException(CoreError.NETWORK)

            home.linkPlayGames()
            testScheduler.advanceUntilIdle()

            assertEquals(
                CoreError.NETWORK,
                home.state.value.playGamesFailure
                    ?.error,
            )
            assertTrue(home.state.value.offersPlayGames, "still a guest, so offered it again")
            assertFalse(home.state.value.linkingPlayGames)
            assertEquals("play_games", analytics.recorded.last().properties[AnalyticsProperty.ACTION])

            home.dismissPlayGamesFailure()
            assertNull(home.state.value.playGamesFailure)
        }

    /** Play Games refusing the build's key answers as a player backing out does: either way the tap is answered. */
    @Test
    fun `a tap Play Games signs nobody in for says so`() =
        runTest(main) {
            val refusing = LinkPlayGames(NotSignedInPlayGames, links, session, Analytics.None)
            val home = viewModel(refusing)
            home.shown()
            testScheduler.advanceUntilIdle()

            home.linkPlayGames()
            testScheduler.advanceUntilIdle()

            assertEquals(
                CoreError.PLAY_GAMES_NOT_SIGNED_IN,
                home.state.value.playGamesFailure
                    ?.error,
            )
            assertTrue(home.state.value.offersPlayGames, "still a guest, so offered it again")
            assertFalse(home.state.value.linkingPlayGames)
        }

    private fun viewModel(link: LinkPlayGames = linking): HomeViewModel =
        HomeViewModel(GetProfile(players, session), SetAvatar(players, session), session, link, analytics)

    /** The device's session: none until [ensure] mints guests one after the other. */
    private class FakeSession :
        SessionRepository,
        CurrentSession {
        val player = MutableStateFlow<String?>(null)
        private var minted = 0

        override suspend fun ensure(): String = player.value ?: "guest${++minted}".also { player.value = it }

        override fun current(): String? = player.value

        override val sessions: Flow<String> = player.filterNotNull()
    }

    /** Play Games with a player signed in, who always has a code. */
    private object SignedInPlayGames : PlayGames {
        override val available: Boolean = true

        override suspend fun isAuthenticated(): Boolean = true

        override suspend fun signIn(): Boolean = true

        override suspend fun serverAuthCode(): String = "code"
    }

    /** Play Games with nobody signed in, and nobody signing in when asked. */
    private object NotSignedInPlayGames : PlayGames {
        override val available: Boolean = true

        override suspend fun isAuthenticated(): Boolean = false

        override suspend fun signIn(): Boolean = false

        override suspend fun serverAuthCode(): String? = null
    }

    /**
     * The server linking the player playing to Play Games: who plays stays, so [FakeSession] tells nobody,
     * and only what the server reads of them changes ([FakePlayers.name], [FakePlayers.linked]); or it
     * refuses, with [failWith].
     */
    private class LinksPlayerPlaying(
        private val session: FakeSession,
        private val players: FakePlayers,
    ) : PlayGamesRepository {
        var failWith: KvizicException? = null

        override fun isSettled(): Boolean = false

        override suspend fun signIn(serverAuthCode: String): String? {
            failWith?.let { throw it }
            players.linked = true
            return session.player.value
        }
    }

    /** The server's profile of whoever the session names, by its [name] when asked, held while [hold] is incomplete. */
    private class FakePlayers(
        private val session: FakeSession,
    ) : PlayerRepository {
        var reads = 0
        var failWith: KvizicException? = null
        var hold: CompletableDeferred<Unit>? = null
        var name = "Брзи Јеж"
        var linked = false

        override suspend fun profile(): Profile {
            reads++
            val asked = name
            hold?.await()
            failWith?.let { throw it }
            return profileOf(session.player.value ?: error("no session"), asked)
        }

        var avatar = "hedgehog"
        var avatarFailWith: KvizicException? = null
        var avatarHold: CompletableDeferred<Unit>? = null
        val avatarsSent = mutableListOf<String>()

        override suspend fun setAvatar(avatarId: String): Profile {
            avatarsSent += avatarId
            avatarHold?.await()
            avatarFailWith?.let { throw it }
            avatar = avatarId
            return profileOf(session.player.value ?: error("no session"), name)
        }

        private fun profileOf(
            player: String,
            name: String,
        ): Profile =
            Profile(
                playerId = player,
                displayName = name,
                nameSource = NameSource.GENERATED,
                avatarId = avatar,
                playGamesLinked = linked,
                stats = PlayerStats(),
            )
    }
}
