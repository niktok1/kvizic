package io.ntole.kvizic.services

import io.ntole.kvizic.core.domain.analytics.Analytics
import io.ntole.kvizic.core.domain.playgames.LinkPlayGames
import io.ntole.kvizic.core.domain.playgames.PlayGames
import io.ntole.kvizic.core.domain.playgames.PlayGamesRepository
import io.ntole.kvizic.core.domain.session.CurrentSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** What the app does by itself as it comes to the foreground: the launch's Play Games sign-in. */
class AppServicesTest {
    private val signIns = mutableListOf<String>()
    private val session = FakeSession()

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

    private fun TestScope.services(): AppServices {
        val linking = LinkPlayGames(SignedInPlayGames, FakeLink(signIns), session, Analytics.None)
        return AppServices(linking, scope = backgroundScope)
    }

    /** Play Games with a player signed in, who always has a code. */
    private object SignedInPlayGames : PlayGames {
        override val available: Boolean = true

        override suspend fun isAuthenticated(): Boolean = true

        override suspend fun signIn(): Boolean = true

        override suspend fun serverAuthCode(): String = "code"
    }

    /** A link that records every code it signs in with, and settles the device, as the real one does. */
    private class FakeLink(
        private val signIns: MutableList<String>,
    ) : PlayGamesRepository {
        private var settled = false

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
