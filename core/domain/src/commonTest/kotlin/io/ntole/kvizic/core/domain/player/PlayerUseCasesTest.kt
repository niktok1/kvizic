package io.ntole.kvizic.core.domain.player

import io.ntole.kvizic.core.domain.session.SessionRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Reading the profile and picking an avatar each ensure a session before they ask the server. */
class PlayerUseCasesTest {
    private val calls = mutableListOf<String>()
    private val players = FakePlayers(calls)
    private val session = FakeSession(calls)

    @Test
    fun `the profile is read once a session is ensured`() =
        runTest {
            val profile = GetProfile(players, session)()

            assertEquals(listOf("ensure", "profile"), calls)
            assertEquals(PROFILE, profile)
        }

    @Test
    fun `an avatar is picked once a session is ensured and the profile after it is returned`() =
        runTest {
            val profile = SetAvatar(players, session)("owl")

            assertEquals(listOf("ensure", "setAvatar owl"), calls)
            assertEquals("owl", profile.avatarId)
        }

    private class FakePlayers(
        private val calls: MutableList<String>,
    ) : PlayerRepository {
        override suspend fun profile(): Profile {
            calls += "profile"
            return PROFILE
        }

        override suspend fun setAvatar(avatarId: String): Profile {
            calls += "setAvatar $avatarId"
            return PROFILE.copy(avatarId = avatarId)
        }
    }

    private class FakeSession(
        private val calls: MutableList<String>,
    ) : SessionRepository {
        override suspend fun ensure(): String {
            calls += "ensure"
            return "p1"
        }
    }

    private companion object {
        val PROFILE =
            Profile(
                playerId = "p1",
                displayName = "Брзи Јеж",
                nameSource = NameSource.GENERATED,
                avatarId = "hedgehog",
                playGamesLinked = false,
                stats = PlayerStats(gamesPlayed = 3),
            )
    }
}
