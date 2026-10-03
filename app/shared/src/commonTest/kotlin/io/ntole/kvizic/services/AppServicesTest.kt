package io.ntole.kvizic.services

import io.ntole.kvizic.core.domain.analytics.Analytics
import io.ntole.kvizic.core.domain.playgames.LinkPlayGames
import io.ntole.kvizic.core.domain.playgames.PlayGames
import io.ntole.kvizic.core.domain.playgames.PlayGamesRepository
import io.ntole.kvizic.core.domain.session.CurrentSession
import io.ntole.kvizic.room.ScriptedLobbySession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** What the app does by itself as it comes to the foreground: the launch's Play Games sign-in, and again after. */
class AppServicesTest {
    private val signIns = mutableListOf<String>()
    private val session = FakeSession()
    private val playGames = FakePlayGames()
    private val link = FakeLink(signIns)
    private val lobby = ScriptedLobbySession()

    @Test
    fun `the launch signs in with Play Games once and coming back does not again`() =
        runTest {
            session.player.value = "p1"
            val services = services()

            services.foreground()
            testScheduler.runCurrent()
            services.foreground()
            services.foreground()
            testScheduler.runCurrent()

            assertEquals(listOf("code"), signIns)
        }

    @Test
    fun `the launch takes back a seat the server holds and coming back does not again`() =
        runTest {
            val services = services()
            services.foreground()
            services.foreground()
            testScheduler.runCurrent()
            assertEquals(listOf("rejoin"), lobby.commands)
        }

    /** A first launch has no session until the player plays, and the sign-in waits for it. */
    @Test
    fun `the launch's sign-in waits for the session the player starts playing with`() =
        runTest {
            val services = services()

            services.foreground()
            testScheduler.runCurrent()
            assertEquals(emptyList(), signIns)

            session.player.value = "guest1"
            testScheduler.runCurrent()
            assertEquals(listOf("code"), signIns)
        }

    /**
     * A dead session replaced by a fresh guest while the app was in the background, where Play Games, which
     * asks the activity on screen, could not be asked: the sign-in comes as the app does.
     */
    @Test
    fun `coming back to the foreground signs in the session stored in the background`() =
        runTest {
            session.player.value = "p1"
            val services = services()
            services.foreground()
            testScheduler.runCurrent()

            playGames.onScreen = false
            link.settled = false
            session.player.value = "guest2"
            testScheduler.runCurrent()
            assertEquals(listOf("code"), signIns, "nobody on screen to ask")

            playGames.onScreen = true
            services.foreground()
            testScheduler.runCurrent()
            assertEquals(listOf("code", "code"), signIns)
        }

    private fun TestScope.services(): AppServices {
        val linking = LinkPlayGames(playGames, link, session, Analytics.None)
        return AppServices(linking, lobby, scope = backgroundScope)
    }

    /** Play Games with a player signed in, who always has a code, asked only while the app is [onScreen]. */
    private class FakePlayGames : PlayGames {
        var onScreen = true

        override val available: Boolean = true

        override suspend fun isAuthenticated(): Boolean = onScreen

        override suspend fun signIn(): Boolean = true

        override suspend fun serverAuthCode(): String = "code"
    }

    /** A link that records every code it signs in with, and settles the device, as the real one does. */
    private class FakeLink(
        private val signIns: MutableList<String>,
    ) : PlayGamesRepository {
        var settled = false

        override fun isSettled(): Boolean = settled

        override suspend fun signIn(serverAuthCode: String): String {
            signIns += serverAuthCode
            settled = true
            return "p1"
        }
    }

    private class FakeSession : CurrentSession {
        val player = MutableStateFlow<String?>(null)

        override fun current(): String? = player.value

        override val sessions: Flow<String> = player.filterNotNull()
    }
}
