package io.ntole.kvizic.core.domain.player

import io.ntole.kvizic.core.domain.session.SessionRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Reading the profile, picking an avatar and the shop's calls each ensure a session before they ask the server. */
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

    @Test
    fun `the shop is read and an item bought and worn once a session is ensured`() =
        runTest {
            val shopping = Shopping(players, session)
            shopping.items()
            shopping.buy("frame.bulbs")
            val profile = shopping.wear("frame.bulbs", null)

            assertEquals(
                listOf("ensure", "shop", "ensure", "buy frame.bulbs", "ensure", "wear frame.bulbs null"),
                calls,
            )
            assertEquals("frame.bulbs", profile.frameId)
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

        override suspend fun shop(): List<ShopItem> {
            calls += "shop"
            return emptyList()
        }

        override suspend fun buy(itemId: String): Profile {
            calls += "buy $itemId"
            return PROFILE.copy(items = listOf(itemId))
        }

        override suspend fun wear(
            frameId: String?,
            titleId: String?,
        ): Profile {
            calls += "wear $frameId $titleId"
            return PROFILE.copy(frameId = frameId, titleId = titleId)
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
