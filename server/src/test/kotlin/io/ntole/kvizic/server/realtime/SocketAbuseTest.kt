package io.ntole.kvizic.server.realtime

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.CloseCodes
import io.ntole.kvizic.core.protocol.ServerMessage
import io.ntole.kvizic.server.NO_PRACTICAL_LIMIT
import io.ntole.kvizic.server.config.GameConfig
import io.ntole.kvizic.server.config.RequestBudget
import io.ntole.kvizic.server.lobby.GameTimings
import io.ntole.kvizic.server.lobby.LobbyLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** What the server does with clients that send too much, too big, too fast, or read too slowly. */
class SocketAbuseTest {
    @Test
    fun `a socket that floods the server with frames is closed`() =
        runGameServer("socket-flood") { server ->
            val socket = server.connect(server.create(server.guest()))
            socket.await<ServerMessage.Snapshot>()

            // Once the server has closed it, what is still sent fails on the client.
            runCatching { repeat(LobbyLimits.DEFAULT.frameBurst * 2) { socket.send(ClientMessage.Resync) } }

            assertEquals(CloseCodes.TOO_MUCH, socket.closeCode())
        }

    @Test
    fun `a frame past the size limit closes the socket`() =
        runGameServer("socket-oversize") { server ->
            val socket = server.connect(server.create(server.guest()))
            socket.await<ServerMessage.Snapshot>()

            socket.session.send(Frame.Text("{\"t\":\"react\",\"reaction\":\"" + "x".repeat(5_000) + "\"}"))

            assertEquals(CloseReason.Codes.TOO_BIG.code, socket.closeCode())
        }

    @Test
    fun `a socket that stops reading is dropped, and the others see its player gone`() =
        runGameServer(
            "socket-slow-reader",
            configure = {
                it.copy(
                    game =
                        GameConfig(
                            timings = GameTimings.FAST,
                            limits = LobbyLimits(framesPerSecond = 100_000, frameBurst = 100_000),
                        ),
                )
            },
        ) { server ->
            val ana = server.guest()
            val ticket = server.create(ana)
            val host = server.connect(ticket)
            host.await<ServerMessage.Snapshot>()
            val (boris, slow) = server.joinWithSocket(ticket.code)

            // His client stops reading; what he asks for piles up on the server until it gives up on him.
            slow.cancelReader()
            runCatching { repeat(6_000) { slow.send(ClientMessage.Resync) } }

            host.await<ServerMessage.MemberUpdated>(timeout = 20.seconds) {
                it.member.player == boris.playerId && !it.member.connected
            }
            assertTrue(host.staysOpenFor(300.milliseconds), "the others play on")
        }

    @Test
    fun `one address holds only so many sockets at once`() =
        runGameServer(
            "socket-per-address",
            configure = {
                it.copy(
                    game = GameConfig(timings = GameTimings.FAST, limits = LobbyLimits(maxSocketsPerAddress = 2)),
                )
            },
        ) { server ->
            val ticket = server.create(server.guest())
            val first = server.connect(ticket)
            first.await<ServerMessage.Snapshot>()
            val (_, second) = server.joinWithSocket(ticket.code)

            assertEquals(CloseCodes.TOO_MUCH, server.open().closeCode(), "a third at once")
            assertTrue(second.staysOpenFor(100.milliseconds))

            first.session.close(CloseReason(CloseReason.Codes.NORMAL, ""))
            first.closeCode()
            eventually(what = "a socket let in once one closed") {
                server.joinWithSocket(ticket.code).second.staysOpenFor(200.milliseconds)
            }
        }

    @Test
    fun `an address that names too many codes no lobby has is locked out, right codes too`() =
        runGameServer(
            "lobby-code-guessing",
            configure = {
                it.copy(
                    rateLimits = NO_PRACTICAL_LIMIT.copy(lobbyCodeFailures = RequestBudget(3, 1.minutes)),
                )
            },
        ) { server ->
            val ana = server.guest()
            val code = server.create(ana).code
            val guesser = server.guest()
            repeat(3) { attempt ->
                assertEquals(HttpStatusCode.NotFound, server.client.joinLobby(guesser, "00000$attempt").status)
            }

            val locked = server.client.joinLobby(guesser, code)
            assertEquals(HttpStatusCode.TooManyRequests, locked.status)
            assertTrue(locked.headers[HttpHeaders.RetryAfter]!!.toLong() in 1..60)
        }
}
