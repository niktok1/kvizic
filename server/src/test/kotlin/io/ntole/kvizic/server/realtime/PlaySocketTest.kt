package io.ntole.kvizic.server.realtime

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.CloseCodes
import io.ntole.kvizic.core.protocol.CloseReason
import io.ntole.kvizic.core.protocol.LeaveReason
import io.ntole.kvizic.core.protocol.PhaseView
import io.ntole.kvizic.core.protocol.Protocol
import io.ntole.kvizic.core.protocol.ServerMessage
import io.ntole.kvizic.server.config.GameConfig
import io.ntole.kvizic.server.lobby.GameTimings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The socket's handshake and every way the server closes one, over real WebSockets. */
class PlaySocketTest {
    @Test
    fun `a socket with a ticket is welcomed, then given the lobby as it stands`() =
        runGameServer("socket-welcome") { server ->
            val ana = server.guest()
            val ticket = server.create(ana)
            val socket = server.connect(ticket)

            val welcome = socket.await<ServerMessage.Welcome>()
            assertEquals(Protocol.VERSION, welcome.protocol)
            assertEquals(ana.playerId, welcome.you)
            assertEquals(GameTimings.FAST.pingEvery.inWholeMilliseconds, welcome.pingEveryMs)
            val snapshot = socket.await<ServerMessage.Snapshot>()
            assertEquals(ticket.code, snapshot.lobby.code)
            assertEquals(ana.playerId, snapshot.lobby.host)
            assertEquals(LobbyKind.PRIVATE, snapshot.lobby.kind)
            assertIs<PhaseView.Waiting>(snapshot.phase)
            assertTrue(socket.history.first() is ServerMessage.Welcome, "the welcome comes first")
        }

    @Test
    fun `a socket that never says hello is closed as timed out`() =
        runGameServer(
            "socket-no-hello",
            configure = { it.withTimings { copy(helloTimeout = 500.milliseconds) } },
        ) { server ->
            val socket = server.open()
            assertEquals(CloseCodes.TIMEOUT, socket.closeCode(timeout = 5.seconds))
        }

    @Test
    fun `a first frame that is no hello, or no frame of the protocol, closes the socket`() =
        runGameServer("socket-bad-hello") { server ->
            val pong = server.open()
            pong.send(ClientMessage.Pong(1))
            assertEquals(CloseCodes.PROTOCOL_ERROR, pong.closeCode())

            val garbage = server.open()
            garbage.session.send(Frame.Text("{not json"))
            assertEquals(CloseCodes.PROTOCOL_ERROR, garbage.closeCode())

            val binary = server.open()
            binary.session.send(Frame.Binary(true, byteArrayOf(1, 2, 3)))
            assertEquals(CloseCodes.PROTOCOL_ERROR, binary.closeCode())
        }

    @Test
    fun `a ticket works once, and an unknown one never`() =
        runGameServer("socket-ticket-once") { server ->
            val ticket = server.create(server.guest())
            val first = server.connect(ticket)
            first.await<ServerMessage.Snapshot>()

            val again = server.connect(ticket)
            assertEquals(CloseCodes.TICKET_INVALID, again.closeCode())
            val forged = server.connect(ticket.copy(ticket = "not-a-ticket"))
            assertEquals(CloseCodes.TICKET_INVALID, forged.closeCode())
            assertTrue(first.staysOpenFor(300.milliseconds), "the first socket is left alone")
        }

    @Test
    fun `an expired ticket is refused, and a new one from a rejoin works`() =
        runGameServer(
            "socket-ticket-expired",
            configure = { it.copy(game = GameConfig(timings = GameTimings.FAST.copy(ticketTtl = 300.milliseconds))) },
        ) { server ->
            val ana = server.guest()
            val ticket = server.create(ana)
            kotlinx.coroutines.delay(500.milliseconds)
            assertEquals(CloseCodes.TICKET_INVALID, server.connect(ticket).closeCode())

            val fresh = server.join(ana, ticket.code)
            val socket = server.connect(fresh)
            assertEquals(ticket.lobbyId, fresh.lobbyId)
            socket.await<ServerMessage.Snapshot>()
        }

    @Test
    fun `a client of a protocol or a build the server no longer serves is told to update`() =
        runGameServer(
            "socket-upgrade",
            configure = { it.copy(minClientVersions = mapOf("android" to 50)) },
        ) { server ->
            val ana = server.guest()
            val code = server.create(ana).code
            assertEquals(
                CloseCodes.UPGRADE_REQUIRED,
                server.connect(server.join(ana, code), protocol = Protocol.MIN_SUPPORTED - 1).closeCode(),
            )
            assertEquals(CloseCodes.UPGRADE_REQUIRED, server.connect(server.join(ana, code), build = 49).closeCode())

            val current = server.connect(server.join(ana, code), build = 50)
            current.await<ServerMessage.Snapshot>()
            val otherPlatform = server.connect(server.join(ana, code), platform = "desktop", build = 1)
            otherPlatform.await<ServerMessage.Snapshot>()
        }

    @Test
    fun `a second socket of a player replaces the first`() =
        runGameServer("socket-replaced") { server ->
            val ana = server.guest()
            val ticket = server.create(ana)
            val first = server.connect(ticket)
            first.await<ServerMessage.Snapshot>()

            val second = server.connect(server.join(ana, ticket.code))
            second.await<ServerMessage.Snapshot>()

            assertEquals(ServerMessage.Closing(CloseReason.REPLACED), first.await<ServerMessage.Closing>())
            assertEquals(CloseCodes.REPLACED, first.closeCode())
            assertTrue(second.staysOpenFor(300.milliseconds))
        }

    @Test
    fun `a kicked player is closed, and may not come back`() =
        runGameServer("socket-kicked") { server ->
            val ana = server.guest()
            val ticket = server.create(ana)
            val host = server.connect(ticket)
            host.await<ServerMessage.Snapshot>()
            val (boris, borisSocket) = server.joinWithSocket(ticket.code)

            host.send(ClientMessage.Kick(host.nextId(), boris.playerId))

            assertEquals(ServerMessage.Closing(CloseReason.KICKED), borisSocket.await<ServerMessage.Closing>())
            assertEquals(CloseCodes.KICKED, borisSocket.closeCode())
            val left = host.await<ServerMessage.MemberLeft>()
            assertEquals(boris.playerId to LeaveReason.KICKED, left.player to left.reason)
            val rejoin = server.client.joinLobby(boris, ticket.code)
            assertEquals(HttpStatusCode.Forbidden, rejoin.status)
        }

    @Test
    fun `a logout closes that session's socket, and a logout of another session leaves it be`() =
        runGameServer("socket-logout") { server ->
            val ana = server.guest()
            val ticket = server.create(ana)
            val socket = server.connect(ticket)
            socket.await<ServerMessage.Snapshot>()

            val other = server.guest()
            server.client.post(KvizicApi.Paths.AUTH_LOGOUT) { bearerAuth(other.accessToken) }
            assertTrue(socket.staysOpenFor(300.milliseconds), "another player's logout")

            server.client.post(KvizicApi.Paths.AUTH_LOGOUT) { bearerAuth(ana.accessToken) }
            assertEquals(ServerMessage.Closing(CloseReason.SESSION_ENDED), socket.await<ServerMessage.Closing>())
            assertEquals(CloseCodes.SESSION_ENDED, socket.closeCode())
        }

    @Test
    fun `deleting an account closes its socket and frees its seat`() =
        runGameServer("socket-deleted") { server ->
            val ana = server.guest()
            val ticket = server.create(ana)
            val host = server.connect(ticket)
            host.await<ServerMessage.Snapshot>()
            val (boris, borisSocket) = server.joinWithSocket(ticket.code)

            server.client.post(KvizicApi.Paths.ME_DELETION) { bearerAuth(boris.accessToken) }

            assertEquals(CloseCodes.SESSION_ENDED, borisSocket.closeCode())
            assertEquals(LeaveReason.SESSION_ENDED, host.await<ServerMessage.MemberLeft>().reason)
        }

    @Test
    fun `joining another lobby leaves the one before`() =
        runGameServer("socket-joined-another") { server ->
            val ana = server.guest()
            val first = server.create(ana)
            val firstSocket = server.connect(first)
            firstSocket.await<ServerMessage.Snapshot>()
            val (_, borisSocket) = server.joinWithSocket(first.code)

            val second = server.create(ana)
            server.connect(second).await<ServerMessage.Snapshot>()

            assertEquals(ServerMessage.Closing(CloseReason.LEFT), firstSocket.await<ServerMessage.Closing>())
            assertEquals(CloseCodes.NORMAL, firstSocket.closeCode())
            assertEquals(LeaveReason.JOINED_ANOTHER, borisSocket.await<ServerMessage.MemberLeft>().reason)
        }

    @Test
    fun `pings keep coming, and their pongs measure the round trip`() =
        runGameServer("socket-pings", configure = { it.withTimings { copy(silentAfter = 1.seconds) } }) { server ->
            val socket = server.connect(server.create(server.guest()))
            socket.await<ServerMessage.Snapshot>()

            val pings = (1..4).map { socket.await<ServerMessage.Ping>() }
            assertEquals(pings.map { it.seq }, pings.map { it.seq }.sorted(), "in order")
            assertTrue(pings.all { it.rttMs >= 0 })
            assertTrue(
                socket.staysOpenFor(1_500.milliseconds),
                "answered pings keep it open",
            )
        }

    @Test
    fun `a socket that goes silent is closed as timed out, and keeps its seat`() =
        runGameServer("socket-silent", configure = { it.withTimings { copy(silentAfter = 1.seconds) } }) { server ->
            val ana = server.guest()
            val ticket = server.create(ana)
            val host = server.connect(ticket)
            host.await<ServerMessage.Snapshot>()
            val silent = server.connect(server.join(server.guest(), ticket.code), autoPong = false)
            silent.await<ServerMessage.Snapshot>()

            assertEquals(CloseCodes.TIMEOUT, silent.closeCode(timeout = 5.seconds))
            val update = host.await<ServerMessage.MemberUpdated> { !it.member.connected }
            assertTrue(host.all<ServerMessage.MemberLeft>().isEmpty(), "still a member, through the grace")
            assertEquals(false, update.member.connected)
        }
}
