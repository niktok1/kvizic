package io.ntole.kvizic.howtoplay

import io.ntole.kvizic.core.auth.SessionDto
import io.ntole.kvizic.core.network.InMemoryTokenStorage
import io.ntole.kvizic.core.network.SessionStore
import io.ntole.kvizic.core.network.environment.KvizicEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class HowToPlayViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the cards are due on a new install only and once shown never again`() =
        runTest(dispatcher) {
            data class Install(
                val name: String,
                val player: Boolean,
                val shownBefore: Boolean,
                val due: Boolean,
            )
            listOf(
                Install("a new install", player = false, shownBefore = false, due = true),
                Install("an install from before the cards", player = true, shownBefore = false, due = false),
                Install("one that showed them", player = true, shownBefore = true, due = false),
                Install("one that showed them and deleted its player", player = false, shownBefore = true, due = false),
            ).forEach { install ->
                val storage = InMemoryTokenStorage()
                val sessions = SessionStore(storage, KvizicEnvironment.LOCAL)
                if (install.player) sessions.write(SESSION)
                if (install.shownBefore) {
                    HowToPlayViewModel(storage, sessions).shown()
                    testScheduler.advanceUntilIdle()
                }

                assertEquals(install.due, HowToPlayViewModel(storage, sessions).due, install.name)
            }
        }

    private companion object {
        val SESSION =
            SessionDto(
                playerId = "p1",
                accessToken = "access",
                refreshToken = "refresh",
                accessTokenExpiresInSeconds = 900,
            )
    }
}
