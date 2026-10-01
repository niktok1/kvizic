package io.ntole.kvizic.home

import io.ntole.kvizic.analytics.RecordingAnalytics
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
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/** The placeholder Home's player: read when shown, and again when the device becomes another player. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val main: TestDispatcher = StandardTestDispatcher()
    private val session = FakeSession()
    private val players = FakePlayers(session)
    private val analytics = RecordingAnalytics()

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

    private fun viewModel(): HomeViewModel =
        HomeViewModel(GetProfile(players, session), SetAvatar(players, session), session, analytics)

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

    /** The server's profile of whoever the session names, held while [hold] is incomplete. */
    private class FakePlayers(
        private val session: FakeSession,
    ) : PlayerRepository {
        var reads = 0
        var failWith: KvizicException? = null
        var hold: CompletableDeferred<Unit>? = null

        override suspend fun profile(): Profile {
            reads++
            hold?.await()
            failWith?.let { throw it }
            return profileOf(session.player.value ?: error("no session"))
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
            return profileOf(session.player.value ?: error("no session"))
        }

        private fun profileOf(player: String): Profile =
            Profile(
                playerId = player,
                displayName = "Брзи Јеж",
                nameSource = NameSource.GENERATED,
                avatarId = avatar,
                playGamesLinked = false,
                stats = PlayerStats(),
            )
    }
}
