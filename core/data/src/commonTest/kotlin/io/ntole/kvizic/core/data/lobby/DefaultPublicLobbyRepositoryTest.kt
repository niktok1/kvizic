package io.ntole.kvizic.core.data.lobby

import io.ntole.kvizic.core.data.BASE_URL
import io.ntole.kvizic.core.data.FakeServer
import io.ntole.kvizic.core.data.session
import io.ntole.kvizic.core.data.session.DefaultSessionRepository
import io.ntole.kvizic.core.data.storeHolding
import io.ntole.kvizic.core.domain.error.GameError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.domain.lobby.PublicLobby
import io.ntole.kvizic.core.domain.lobby.PublicLobbyRepository
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.lobby.PublicLobbyDto
import io.ntole.kvizic.core.network.KvizicHttpClient
import io.ntole.kvizic.core.network.SessionStore
import io.ntole.kvizic.core.network.api.AuthApi
import io.ntole.kvizic.core.network.api.LobbyApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A room previewed by its code, through the real client against [FakeServer]. */
class DefaultPublicLobbyRepositoryTest {
    private val server = FakeServer()

    @Test
    fun `a room is previewed with its name and settings as the player's session reads it`() =
        runTest {
            server.previewable["482915"] =
                PublicLobbyDto(
                    code = "482915",
                    hostName = "Нина",
                    hostAvatar = "fox",
                    players = 3,
                    maxPlayers = 6,
                    inGame = false,
                    settings = LobbySettingsDto(questionCount = 15, maxPlayers = 6, name = "Петак увече"),
                )

            val room = repository(storeHolding(null)).preview("482915")

            assertEquals(
                PublicLobby(
                    "482915",
                    "Нина",
                    "fox",
                    3,
                    6,
                    false,
                    LobbySettings(questionCount = 15, maxPlayers = 6, name = "Петак увече"),
                ),
                room,
            )
            assertEquals(listOf<Pair<String?, String>>("Bearer access-guest1" to "482915"), server.previewsSentAs)
        }

    @Test
    fun `a code naming no room is LOBBY_NOT_FOUND and a dead session is replaced once`() =
        runTest {
            val repository = repository(storeHolding(session("dead")))

            val refused = assertFailsWith<KvizicException> { repository.preview("111111") }

            assertEquals(GameError.LOBBY_NOT_FOUND, refused.error)
            assertEquals(listOf("Bearer access-dead", "Bearer access-guest1"), server.previewsSentAs.map { it.first })
        }

    private fun repository(store: SessionStore): PublicLobbyRepository {
        val client = KvizicHttpClient.create(BASE_URL, store, server.engine)
        return DefaultPublicLobbyRepository(LobbyApi(client), DefaultSessionRepository(AuthApi(client), store))
    }
}
