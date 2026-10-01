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

        override suspend fun setAvatar(avatarId: String): Profile {
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
