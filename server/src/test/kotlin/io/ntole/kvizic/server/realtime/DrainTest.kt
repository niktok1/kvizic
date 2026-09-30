package io.ntole.kvizic.server.realtime

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.post
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.auth.SessionDto
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.lobby.TicketDto
import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.CloseCodes
import io.ntole.kvizic.core.protocol.CloseReason
import io.ntole.kvizic.core.protocol.NoticeKind
import io.ntole.kvizic.core.protocol.Protocol
import io.ntole.kvizic.core.protocol.ServerMessage
import io.ntole.kvizic.server.GameParts
import io.ntole.kvizic.server.kvizicModule
import io.ntole.kvizic.server.lobby.PickResult
import io.ntole.kvizic.server.lobby.QuestionSource
import io.ntole.kvizic.server.lobby.sampleQuestions
import io.ntole.kvizic.server.testDatabaseFor
import io.ntole.kvizic.server.testServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * A server stopping, as Render stops one, on the real engine: Netty keeps its sockets through the drain,
 * so a waiting lobby is told and closed with 4503, and a game ends at its next reveal.
 */
class DrainTest {
    @Test
    fun `a stopping server drains its lobbies before its sockets go`() =
        runBlocking(Dispatchers.IO) {
            val config = testServerConfig(testDatabaseFor("drain"), adminToken = null).copy(drainSeconds = 20)
            val parts = GameParts(questions = QuestionSource { PickResult(sampleQuestions(10).take(it.count)) })
            val server = embeddedServer(Netty, port = 0, host = "127.0.0.1") { kvizicModule(config, parts = parts) }
            server.start(wait = false)
            val port =
                server.engine
                    .resolvedConnectors()
                    .first()
                    .port
            val client =
                HttpClient(CIO) {
                    engine { requestTimeout = 60_000 }
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                    install(WebSockets)
                    defaultRequest { url("http://127.0.0.1:$port") }
                }
            try {
                coroutineScope {
                    val waitingHost: SessionDto = client.post(KvizicApi.Paths.AUTH_GUEST).body()
                    val waitingTicket = client.createLobby(waitingHost, LobbySettingsDto()).bodyOrFail<TicketDto>()
                    val waiting =
                        TestSocket(
                            client.webSocketSession("ws://127.0.0.1:$port${KvizicApi.Paths.PLAY}"),
                            this,
                            autoPong = true,
                        )
                    waiting.send(ClientMessage.Hello(waitingTicket.ticket, Protocol.VERSION))
                    waiting.await<ServerMessage.Snapshot>()

                    // Three play a game that does not fit the drain: each must get its results and a clean close,
                    // however the engine races the last frames.
                    val playingHost: SessionDto = client.post(KvizicApi.Paths.AUTH_GUEST).body()
                    val playingTicket =
                        client
                            .createLobby(playingHost, LobbySettingsDto(questionCount = 20, secondsPerQuestion = 30))
                            .bodyOrFail<TicketDto>()
                    val players =
                        listOf(playingHost) + List(2) { client.post(KvizicApi.Paths.AUTH_GUEST).body<SessionDto>() }
                    val sockets =
                        players.map { player ->
                            val ticket =
                                if (player == playingHost) {
                                    playingTicket
                                } else {
                                    client.joinLobby(player, playingTicket.code).bodyOrFail<TicketDto>()
                                }
                            TestSocket(
                                client.webSocketSession("ws://127.0.0.1:$port${KvizicApi.Paths.PLAY}"),
                                this,
                                autoPong = true,
                            ).also { socket ->
                                socket.send(ClientMessage.Hello(ticket.ticket, Protocol.VERSION))
                                socket.await<ServerMessage.Snapshot>()
                            }
                        }
                    val host = sockets.first()
                    host.send(ClientMessage.Start(host.nextId()))
                    val opened = sockets.map { it.await<ServerMessage.AnswersOpened>() }.first()

                    val began = TimeSource.Monotonic.markNow()
                    val stopping = thread(name = "stopping") { server.stop(1_000, 5_000) }

                    assertEquals(NoticeKind.SERVER_RESTARTING, waiting.await<ServerMessage.Notice>().kind)
                    assertEquals(
                        ServerMessage.Closing(CloseReason.SERVER_RESTARTING),
                        waiting.await<ServerMessage.Closing>(),
                    )
                    assertEquals(CloseCodes.SERVER_RESTARTING, waiting.closeCode())

                    // The game does not fit 20 seconds, so it ends at the reveal of the question it is on.
                    sockets.forEach {
                        it.await<ServerMessage.Notice> { notice ->
                            notice.kind ==
                                NoticeKind.SERVER_RESTARTING
                        }
                    }
                    sockets.forEach { it.send(ClientMessage.Answer(it.nextId(), opened.index, rightOptionOf(opened))) }
                    sockets.forEach { socket ->
                        assertTrue(socket.await<ServerMessage.Revealed>().reveal.last)
                        assertTrue(socket.await<ServerMessage.GameOver>(timeout = 10.seconds).results.endedEarly)
                        assertEquals(
                            ServerMessage.Closing(CloseReason.SERVER_RESTARTING),
                            socket.await<ServerMessage.Closing>(),
                        )
                        assertEquals(CloseCodes.SERVER_RESTARTING, socket.closeCode(timeout = 10.seconds))
                    }

                    launch { stopping.join() }.join()
                    assertTrue(
                        TimeSource.Monotonic.markNow() - began < 20.seconds,
                        "the stop waited only as long as the lobbies",
                    )
                }
            } finally {
                client.close()
                server.stop(0, 0)
            }
        }
}
