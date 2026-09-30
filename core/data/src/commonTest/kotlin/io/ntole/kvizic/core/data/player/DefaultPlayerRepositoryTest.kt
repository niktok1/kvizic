package io.ntole.kvizic.core.data.player

import io.ntole.kvizic.core.data.BASE_URL
import io.ntole.kvizic.core.data.FakeServer
import io.ntole.kvizic.core.data.session
import io.ntole.kvizic.core.data.session.DefaultSessionRepository
import io.ntole.kvizic.core.data.storeHolding
import io.ntole.kvizic.core.domain.error.GameError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.player.NameSource
import io.ntole.kvizic.core.domain.player.PlayerStats
import io.ntole.kvizic.core.network.KvizicHttpClient
import io.ntole.kvizic.core.network.SessionStore
import io.ntole.kvizic.core.network.api.AuthApi
import io.ntole.kvizic.core.network.api.PlayerApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The player's profile and avatar through the real client, against [FakeServer]. */
class DefaultPlayerRepositoryTest {
    private val server = FakeServer()

    @Test
    fun `the profile is the session player's as the server knows them`() =
        runTest {
            val (players, sessions) = repositories(storeHolding(null))
            sessions.ensure()

            val profile = players.profile()

            assertEquals("guest1", profile.playerId)
            assertEquals("Брзи Јеж", profile.displayName)
            assertEquals(NameSource.GENERATED, profile.nameSource)
            assertEquals(
                PlayerStats(gamesPlayed = 7, gamesWon = 2, answersGiven = 70, answersCorrect = 41),
                profile.stats,
            )
        }

    /** A dev server's data reset: the session is dead, and the profile that comes back is the fresh guest's. */
    @Test
    fun `a dead session is replaced once and the fresh guest's profile read`() =
        runTest {
            val (players, _) = repositories(storeHolding(session("dead")))

            val profile = players.profile()

            assertEquals("guest1", profile.playerId)
            assertEquals(listOf<String?>("Bearer access-dead", "Bearer access-guest1"), server.profilesSentAs)
            assertEquals(1, server.guestsMinted)
        }

    @Test
    fun `an avatar picked is the profile's from then on`() =
        runTest {
            val (players, sessions) = repositories(storeHolding(null))
            sessions.ensure()

            assertEquals("owl", players.setAvatar("owl").avatarId)
            assertEquals("owl", players.profile().avatarId)
        }

    @Test
    fun `an avatar the server does not have is INVALID_AVATAR`() =
        runTest {
            val (players, sessions) = repositories(storeHolding(null))
            sessions.ensure()

            val refused = assertFailsWith<KvizicException> { players.setAvatar("dragon") }

            assertEquals(GameError.INVALID_AVATAR, refused.error)
        }

    private fun repositories(store: SessionStore): Pair<DefaultPlayerRepository, DefaultSessionRepository> {
        val client = KvizicHttpClient.create(BASE_URL, store, server.engine)
        val sessions = DefaultSessionRepository(AuthApi(client), store)
        return DefaultPlayerRepository(PlayerApi(client), sessions) to sessions
    }
}
