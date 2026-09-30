package io.ntole.kvizic.server.lobby

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.error.ErrorDto
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.lobby.TicketDto
import io.ntole.kvizic.core.lobby.Visibility
import io.ntole.kvizic.core.protocol.ServerMessage
import io.ntole.kvizic.server.realtime.bodyOrFail
import io.ntole.kvizic.server.realtime.createLobby
import io.ntole.kvizic.server.realtime.joinLobby
import io.ntole.kvizic.server.realtime.publicLobbies
import io.ntole.kvizic.server.realtime.quickPlay
import io.ntole.kvizic.server.realtime.runGameServer
import io.ntole.kvizic.server.realtime.soloRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Lobbies over REST: opening, joining by code, Quick play, solo runs and the public list. */
class LobbyRoutesTest {
    @Test
    fun `a lobby opens with the host's settings, within the rules`() =
        runGameServer("lobby-create") { server ->
            val ana = server.guest()

            suspend fun refusal(settings: LobbySettingsDto): ErrorCode =
                server.client.createLobby(ana, settings).let { response ->
                    assertEquals(HttpStatusCode.BadRequest, response.status)
                    response.body<ErrorDto>().code
                }

            assertEquals(ErrorCode.INVALID_SETTINGS, refusal(LobbySettingsDto(questionCount = 7)))
            assertEquals(ErrorCode.INVALID_SETTINGS, refusal(LobbySettingsDto(secondsPerQuestion = 11)))
            assertEquals(ErrorCode.INVALID_SETTINGS, refusal(LobbySettingsDto(topics = listOf("COOKING"))))
            assertEquals(ErrorCode.INVALID_SETTINGS, refusal(LobbySettingsDto(maxPlayers = 1)))
            assertEquals(ErrorCode.INVALID_SETTINGS, refusal(LobbySettingsDto(maxPlayers = 9)))

            val created = server.client.createLobby(ana, LobbySettingsDto(topics = listOf("GEOGRAPHY", "SPORT")))
            assertEquals(HttpStatusCode.Created, created.status)
            val ticket = created.body<TicketDto>()
            assertTrue(ticket.code.length == 6 && ticket.code.all { it in '0'..'9' })
            assertEquals(GameTimingsForTests.ticketTtlMs, ticket.ticketExpiresInMs)
            assertTrue(!ticket.toString().contains(ticket.ticket), "the ticket is never printed")
        }

    @Test
    fun `a code joins its lobby, however it is typed, until the lobby is full`() =
        runGameServer("lobby-join") { server ->
            val ana = server.guest()
            val ticket = server.create(ana, LobbySettingsDto(maxPlayers = 2))
            val spaced = ticket.code.chunked(3).joinToString(" ")

            val boris = server.guest()
            val joined = server.client.joinLobby(boris, spaced).bodyOrFail<TicketDto>()
            assertEquals(ticket.lobbyId, joined.lobbyId)
            assertEquals(ticket.code, joined.code)

            val full = server.client.joinLobby(server.guest(), ticket.code)
            assertEquals(HttpStatusCode.Conflict, full.status)
            assertEquals(ErrorCode.LOBBY_FULL, full.body<ErrorDto>().code)
            assertEquals(
                HttpStatusCode.OK,
                server.client.joinLobby(boris, ticket.code).status,
                "a rejoin takes no seat",
            )

            assertEquals(HttpStatusCode.NotFound, server.client.joinLobby(boris, "12").status)
            assertEquals(HttpStatusCode.NotFound, server.client.joinLobby(boris, "٣٣٣٣٣٣").status)
        }

    @Test
    fun `quick play seats strangers together in a public lobby, which the list shows`() =
        runGameServer("lobby-quick-play") { server ->
            val ana = server.guest()
            val first = server.client.quickPlay(ana).bodyOrFail<TicketDto>()
            server.connect(first).await<ServerMessage.Snapshot>()
            assertEquals(
                first.lobbyId,
                server.client
                    .quickPlay(ana)
                    .bodyOrFail<TicketDto>()
                    .lobbyId,
                "hers again",
            )

            val boris = server.guest()
            val second = server.client.quickPlay(boris).bodyOrFail<TicketDto>()
            assertEquals(first.lobbyId, second.lobbyId, "the one waiting with a seat free")
            server.connect(second).await<ServerMessage.Snapshot>()

            val private = server.create(server.guest())
            val list = server.client.publicLobbies(boris)
            assertEquals(listOf(first.code), list.lobbies.map { it.code }, "private lobbies are never listed")
            assertEquals(2, list.lobbies.single().players)
            assertTrue(list.online >= 2)
            assertNotEquals(private.code, first.code)
        }

    @Test
    fun `quick play opens a public lobby when none waits`() =
        runGameServer("lobby-quick-play-new") { server ->
            val ticket = server.client.quickPlay(server.guest()).bodyOrFail<TicketDto>()
            val snapshot = server.connect(ticket).await<ServerMessage.Snapshot>()
            assertEquals(Visibility.PUBLIC, snapshot.lobby.settings.visibility)
            assertEquals(LobbySettingsDto.QUICK_PLAY, snapshot.lobby.settings)
        }

    @Test
    fun `a solo run is a lobby of one that nobody else can find`() =
        runGameServer("lobby-solo") { server ->
            val ana = server.guest()
            val solo = server.client.soloRun(ana).bodyOrFail<TicketDto>()
            val snapshot = server.connect(solo).await<ServerMessage.Snapshot>()
            assertEquals(LobbySettingsDto.SOLO, snapshot.lobby.settings)

            assertEquals(HttpStatusCode.NotFound, server.client.joinLobby(server.guest(), solo.code).status)
            assertEquals(HttpStatusCode.OK, server.client.joinLobby(ana, solo.code).status, "but its player may rejoin")
            assertTrue(
                server.client
                    .publicLobbies(ana)
                    .lobbies
                    .none { it.code == solo.code },
            )
        }

    @Test
    fun `every lobby route needs a session`() =
        runGameServer("lobby-auth") { server ->
            assertEquals(HttpStatusCode.Unauthorized, server.client.get(KvizicApi.Paths.LOBBIES).status)
            assertEquals(HttpStatusCode.Unauthorized, server.client.post(KvizicApi.Paths.LOBBIES).status)
            assertEquals(HttpStatusCode.Unauthorized, server.client.post(KvizicApi.Paths.LOBBY_JOINS).status)
            assertEquals(HttpStatusCode.Unauthorized, server.client.post(KvizicApi.Paths.QUICK_PLAY).status)
            assertEquals(HttpStatusCode.Unauthorized, server.client.post(KvizicApi.Paths.SOLO_RUNS).status)
        }

    private object GameTimingsForTests {
        val ticketTtlMs = GameTimings.FAST.ticketTtl.inWholeMilliseconds
    }
}
