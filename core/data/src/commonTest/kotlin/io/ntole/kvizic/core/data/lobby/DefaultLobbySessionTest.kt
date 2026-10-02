package io.ntole.kvizic.core.data.lobby

import io.ktor.http.HttpStatusCode
import io.ntole.kvizic.core.data.BASE_URL
import io.ntole.kvizic.core.data.FakeServer
import io.ntole.kvizic.core.data.session.DefaultSessionRepository
import io.ntole.kvizic.core.data.storeHolding
import io.ntole.kvizic.core.domain.error.GameError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.LobbyCommandKind
import io.ntole.kvizic.core.domain.lobby.LobbyEvent
import io.ntole.kvizic.core.domain.lobby.LobbyExit
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.domain.lobby.NoticeKind
import io.ntole.kvizic.core.domain.lobby.RefusalReason
import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.network.KvizicHttpClient
import io.ntole.kvizic.core.network.UpgradeSignal
import io.ntole.kvizic.core.network.api.AuthApi
import io.ntole.kvizic.core.network.api.LobbyApi
import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.CloseCodes
import io.ntole.kvizic.core.protocol.CloseReason
import io.ntole.kvizic.core.protocol.PhaseView
import io.ntole.kvizic.core.protocol.PickView
import io.ntole.kvizic.core.protocol.RejectCode
import io.ntole.kvizic.core.protocol.ServerMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import io.ntole.kvizic.core.protocol.NoticeKind as WireNoticeKind

/**
 * The lobby session over a fake socket and the fake REST server, on the test's virtual clock: joining,
 * the state it keeps, answering, and every way a connection ends, those it comes back from included.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLobbySessionTest {
    private class Harness(
        val test: TestScope,
    ) {
        val server = FakeServer()
        val transport = FakePlayTransport()
        val upgrade = UpgradeSignal()
        private val dispatcher = StandardTestDispatcher(test.testScheduler)
        private val store = storeHolding(null)
        private val client = KvizicHttpClient.create(BASE_URL, store, server.engineOn(dispatcher))
        val sessions = DefaultSessionRepository(AuthApi(client), store)
        val events = mutableListOf<LobbyEvent>()
        val lobby =
            DefaultLobbySession(
                api = LobbyApi(client),
                transport = transport,
                session = sessions,
                scope = test.backgroundScope,
                upgrade = upgrade,
                timeSource = test.testScheduler.timeSource,
                random = Random(1),
                confined = dispatcher,
            )

        init {
            test.backgroundScope.launch(dispatcher) { lobby.events.collect { events += it } }
        }

        fun settle() = test.runCurrent()

        fun wait(duration: Duration) {
            test.advanceTimeBy(duration)
            test.runCurrent()
        }

        /** Joins the lobby and lets its socket say hello with a snapshot of [phase]. */
        suspend fun inLobby(phase: PhaseView = PhaseView.Waiting()): FakeConnection {
            lobby.join(CODE)
            settle()
            val socket = transport.last()
            socket.push(
                welcome(),
                snapshot(v = 1, lobby = lobbyView("guest1", member("guest1", 0), member("guest2", 1)), phase = phase),
            )
            settle()
            return socket
        }

        fun state(): LobbySessionState = lobby.state.value

        fun inLobbyState(): LobbySessionState.InLobby = assertIs<LobbySessionState.InLobby>(state())
    }

    @Test
    fun `joining takes a seat over REST and opens the socket with its ticket and shows the lobby`() =
        runTest {
            val harness = Harness(this)
            harness.lobby.join(CODE)
            assertEquals(LobbySessionState.Joining(CODE), harness.state())
            harness.settle()
            val socket = harness.transport.last()
            assertEquals("ticket-1", socket.ticket)

            socket.push(welcome(), snapshot())
            harness.settle()
            val shown = harness.inLobbyState()
            assertEquals("guest1", shown.you)
            assertEquals(CODE, shown.lobby.code)
            assertEquals(false, shown.reconnecting)
            assertEquals(listOf<String?>("Bearer access-guest1"), harness.server.ticketsSentTo)
        }

    @Test
    fun `a refused seat says why and leaves the device in no lobby`() =
        runTest {
            val harness = Harness(this)
            harness.server.refuseJoinsWith = HttpStatusCode.NotFound to ErrorCode.LOBBY_NOT_FOUND
            val refused = assertFailsWith<KvizicException> { harness.lobby.join("000000") }
            assertEquals(GameError.LOBBY_NOT_FOUND, refused.error)
            assertEquals(LobbySessionState.Idle, harness.state())
            assertTrue(harness.transport.opened.isEmpty())
        }

    @Test
    fun `every ping is answered with its pong`() =
        runTest {
            val harness = Harness(this)
            val socket = harness.inLobby()
            socket.push(ServerMessage.Ping(seq = 4, rttMs = 30), ServerMessage.Ping(seq = 5, rttMs = 31))
            harness.settle()
            assertEquals(listOf(4L, 5L), socket.sentOf<ClientMessage.Pong>().map { it.seq })
        }

    @Test
    fun `a missing version asks for a snapshot and which puts the state right`() =
        runTest {
            val harness = Harness(this)
            val socket = harness.inLobby()
            socket.push(ServerMessage.HostChanged(v = 3, host = "guest2"))
            harness.settle()
            assertEquals(1, socket.sentOf<ClientMessage.Resync>().size)
            assertEquals("guest1", harness.inLobbyState().lobby.host, "nothing out of order is applied")

            socket.push(snapshot(v = 3, lobby = lobbyView("guest2", member("guest1", 0), member("guest2", 1))))
            harness.settle()
            assertEquals("guest2", harness.inLobbyState().lobby.host)
        }

    @Test
    fun `an answer shows at once and is sent and a refusal takes it back`() =
        runTest {
            val harness = Harness(this)
            val socket = harness.inLobby(answering(index = 0))

            harness.lobby.answer(2)
            harness.settle()
            assertEquals(2, assertIs<GamePhase.Answering>(harness.inLobbyState().phase).myPick)
            val sent = socket.sentOf<ClientMessage.Answer>().single()
            assertEquals(0 to 2, sent.question to sent.option)

            socket.push(ServerMessage.Rejected(sent.id, RejectCode.TOO_LATE))
            harness.settle()
            assertEquals(null, assertIs<GamePhase.Answering>(harness.inLobbyState().phase).myPick)
            assertEquals(LobbyEvent.Refused(LobbyCommandKind.ANSWER, RefusalReason.TOO_LATE), harness.events.last())
        }

    @Test
    fun `a second answer to the same question is not sent`() =
        runTest {
            val harness = Harness(this)
            val socket = harness.inLobby(answering(index = 0))
            harness.lobby.answer(1)
            harness.lobby.answer(3)
            harness.settle()
            assertEquals(listOf(1), socket.sentOf<ClientMessage.Answer>().map { it.option })
        }

    @Test
    fun `a connection lost comes back with a new ticket and the answer the server never got is sent again`() =
        runTest {
            val harness = Harness(this)
            val first = harness.inLobby(answering(index = 0))
            harness.lobby.answer(1)
            harness.settle()
            first.drop()
            harness.settle()
            assertTrue(harness.inLobbyState().reconnecting)
            assertEquals(1, assertIs<GamePhase.Answering>(harness.inLobbyState().phase).myPick, "still shown")

            harness.wait(1.seconds)
            val second = harness.transport.last()
            assertEquals("ticket-2", second.ticket, "a ticket works once")
            second.push(welcome(), snapshot(v = 5, phase = answering(index = 0, remainingMs = 9_000)))
            harness.settle()

            assertEquals(false, harness.inLobbyState().reconnecting)
            val again = second.sentOf<ClientMessage.Answer>().single()
            assertEquals(0 to 1, again.question to again.option)
        }

    @Test
    fun `a transport that throws as its socket breaks is a connection lost, not a crash`() =
        runTest {
            val harness = Harness(this)
            val first = harness.inLobby(answering(index = 0))
            first.breakWith(IllegalStateException("socket closed"))
            harness.settle()
            assertTrue(harness.inLobbyState().reconnecting)

            harness.wait(1.seconds)
            val second = harness.transport.last()
            second.push(welcome(), snapshot(v = 5, phase = answering(index = 0, remainingMs = 9_000)))
            harness.settle()
            assertEquals(false, harness.inLobbyState().reconnecting)
        }

    @Test
    fun `an answer the server got before the drop is not sent twice`() =
        runTest {
            val harness = Harness(this)
            val first = harness.inLobby(answering(index = 0))
            harness.lobby.answer(1)
            harness.settle()
            first.drop()
            harness.wait(1.seconds)
            val second = harness.transport.last()
            second.push(welcome(), snapshot(v = 5, phase = answering(index = 0, yourPick = 1)))
            harness.settle()
            assertTrue(second.sentOf<ClientMessage.Answer>().isEmpty())
        }

    @Test
    fun `an answer to a question that has moved on is let go`() =
        runTest {
            val harness = Harness(this)
            val first = harness.inLobby(answering(index = 0))
            harness.lobby.answer(1)
            harness.settle()
            first.drop()
            harness.wait(1.seconds)
            val second = harness.transport.last()
            second.push(welcome(), snapshot(v = 9, phase = answering(index = 1)))
            harness.settle()
            assertTrue(second.sentOf<ClientMessage.Answer>().isEmpty())
            assertEquals(null, assertIs<GamePhase.Answering>(harness.inLobbyState().phase).myPick)
        }

    @Test
    fun `each close that ends a stay says why`() =
        runTest {
            val ending =
                listOf(
                    CloseCodes.KICKED to LobbyExit.KICKED,
                    CloseCodes.LOBBY_GONE to LobbyExit.LOBBY_GONE,
                    CloseCodes.REPLACED to LobbyExit.REPLACED,
                    CloseCodes.SESSION_ENDED to LobbyExit.SESSION_ENDED,
                    CloseCodes.SERVER_RESTARTING to LobbyExit.SERVER_RESTARTING,
                    CloseCodes.UPGRADE_REQUIRED to LobbyExit.UPGRADE_REQUIRED,
                )
            ending.forEach { (code, exit) ->
                val harness = Harness(this)
                val socket = harness.inLobby()
                socket.closeWith(code)
                harness.settle()
                assertEquals(LobbySessionState.Ended(exit, CODE), harness.state(), "close $code")
                harness.wait(1.minutes)
                assertEquals(1, harness.transport.opened.size, "no reconnect after $code")
            }
        }

    @Test
    fun `a normal close says why in its closing message`() =
        runTest {
            val harness = Harness(this)
            harness.inLobby().closeWith(CloseCodes.NORMAL, CloseReason.LOBBY_CLOSED)
            harness.settle()
            assertEquals(LobbySessionState.Ended(LobbyExit.LOBBY_CLOSED, CODE), harness.state())
        }

    @Test
    fun `a vote out and a game started without the player each end the stay and say why`() =
        runTest {
            val ending =
                listOf(
                    Triple(CloseCodes.KICKED, CloseReason.VOTED_OUT, LobbyExit.VOTED_OUT),
                    Triple(CloseCodes.KICKED, CloseReason.KICKED, LobbyExit.KICKED),
                    Triple(CloseCodes.NORMAL, CloseReason.NOT_BACK, LobbyExit.NOT_BACK),
                )
            ending.forEach { (code, reason, exit) ->
                val harness = Harness(this)
                harness.inLobby().closeWith(code, reason)
                harness.settle()
                assertEquals(LobbySessionState.Ended(exit, CODE), harness.state(), "close $code for $reason")
                harness.wait(1.minutes)
                assertEquals(1, harness.transport.opened.size, "no reconnect after $reason")
            }
        }

    @Test
    fun `a vote goes with an id and one cast too soon is refused as such`() =
        runTest {
            val harness = Harness(this)
            val socket = harness.inLobby()
            harness.lobby.voteKick("guest2")
            harness.lobby.voteKick(null)
            harness.settle()
            val (cast, takenBack) = socket.sentOf<ClientMessage.VoteKick>()
            assertEquals("guest2" to null, cast.player to takenBack.player)
            socket.push(ServerMessage.Rejected(cast.id, RejectCode.TOO_SOON))
            harness.settle()
            assertEquals(
                LobbyEvent.Refused(LobbyCommandKind.VOTE_KICK, RefusalReason.TOO_SOON),
                harness.events.last(),
            )
        }

    @Test
    fun `an old build is told to update`() =
        runTest {
            val harness = Harness(this)
            harness.inLobby().closeWith(CloseCodes.UPGRADE_REQUIRED)
            harness.settle()
            assertTrue(harness.upgrade.required.first())
        }

    @Test
    fun `a ticket refused and a timeout or a slow socket come back again`() =
        runTest {
            listOf(CloseCodes.TICKET_INVALID, CloseCodes.TIMEOUT, CloseCodes.TOO_MUCH).forEach { code ->
                val harness = Harness(this)
                harness.inLobby().closeWith(code)
                harness.settle()
                assertTrue(harness.inLobbyState().reconnecting, "after $code")
                harness.wait(1.seconds)
                assertEquals(2, harness.transport.opened.size, "reconnected after $code")
            }
        }

    @Test
    fun `a rejoin the lobby refuses ends the stay`() =
        runTest {
            val harness = Harness(this)
            harness.inLobby().drop()
            harness.server.refuseJoinsWith = HttpStatusCode.Forbidden to ErrorCode.LOBBY_BANNED
            harness.wait(1.seconds)
            assertEquals(LobbySessionState.Ended(LobbyExit.KICKED, CODE), harness.state())
        }

    @Test
    fun `a socket that hears nothing for three pings is made again`() =
        runTest {
            val harness = Harness(this)
            val first = harness.inLobby()
            harness.wait(16.seconds)
            assertTrue(first.closedByClient, "given up on")
            harness.wait(1.seconds)
            assertEquals(2, harness.transport.opened.size)
        }

    @Test
    fun `the waits between tries grow and a player with no connection for long is told`() =
        runTest {
            val harness = Harness(this)
            harness.inLobby().drop()
            harness.transport.refuse = true
            harness.wait(10.minutes)
            assertEquals(LobbySessionState.Ended(LobbyExit.CONNECTION_LOST, CODE), harness.state())
            assertTrue(
                harness.server.ticketsSentTo.size in 10..20,
                "one ticket a try: ${harness.server.ticketsSentTo.size}",
            )
        }

    @Test
    fun `waking cuts a wait short`() =
        runTest {
            val harness = Harness(this)
            harness.inLobby().drop()
            harness.transport.refuse = true
            harness.wait(30.seconds)
            val tries = harness.server.ticketsSentTo.size
            harness.transport.refuse = false
            harness.lobby.wake()
            harness.settle()
            assertEquals(tries + 1, harness.server.ticketsSentTo.size, "tried at once")
            assertEquals(2, harness.transport.opened.size, "and connected")
        }

    @Test
    fun `leaving goes home at once and says so to the lobby`() =
        runTest {
            val harness = Harness(this)
            val socket = harness.inLobby()
            harness.lobby.leave()
            harness.settle()
            assertEquals(LobbySessionState.Idle, harness.state())
            assertEquals(1, socket.sentOf<ClientMessage.Leave>().size)
            socket.closeWith(CloseCodes.NORMAL, CloseReason.LEFT)
            harness.wait(1.minutes)
            assertEquals(LobbySessionState.Idle, harness.state())
            assertEquals(1, harness.transport.opened.size)
        }

    @Test
    fun `taking another seat lets the first lobby go`() =
        runTest {
            val harness = Harness(this)
            val first = harness.inLobby()
            harness.lobby.create(LobbySettings())
            harness.settle()
            assertTrue(first.isClosed)
            assertEquals(2, harness.transport.opened.size)
            assertEquals(LobbySessionState.Joining(null), harness.state())
        }

    @Test
    fun `commands go with ids and a refusal says which`() =
        runTest {
            val harness = Harness(this)
            val socket = harness.inLobby()
            harness.lobby.start()
            harness.lobby.kick("guest2")
            harness.settle()
            val start = socket.sentOf<ClientMessage.Start>().single()
            val kick = socket.sentOf<ClientMessage.Kick>().single()
            assertTrue(start.id != kick.id)
            socket.push(ServerMessage.Ack(start.id), ServerMessage.Rejected(kick.id, RejectCode.NOT_HOST))
            harness.settle()
            assertEquals(LobbyEvent.Refused(LobbyCommandKind.KICK, RefusalReason.NOT_HOST), harness.events.last())
        }

    @Test
    fun `reactions and presence pass as events`() =
        runTest {
            val harness = Harness(this)
            val socket = harness.inLobby()
            socket.push(ServerMessage.Reacted("guest2", "bravo"), ServerMessage.Presence(12, 3))
            harness.settle()
            assertEquals(
                listOf(LobbyEvent.Reacted("guest2", "bravo"), LobbyEvent.Presence(12, 3)),
                harness.events.takeLast(2),
            )
        }

    @Test
    fun `an idle notice says how long is left and staying answers it`() =
        runTest {
            val harness = Harness(this)
            val socket = harness.inLobby()
            socket.push(
                ServerMessage.Notice(WireNoticeKind.HOST_IDLE, 30_000),
                ServerMessage.Notice(WireNoticeKind.ROOM_IDLE, 60_000),
            )
            harness.settle()
            assertEquals(
                listOf(
                    LobbyEvent.Notice(NoticeKind.HOST_IDLE, 30_000),
                    LobbyEvent.Notice(NoticeKind.ROOM_IDLE, 60_000),
                ),
                harness.events.takeLast(2),
            )
            harness.lobby.stay()
            harness.settle()
            assertEquals(1, socket.sentOf<ClientMessage.Stay>().size)
        }

    @Test
    fun `others' picks show once this player has answered`() =
        runTest {
            val harness = Harness(this)
            val socket = harness.inLobby(answering(index = 0))
            harness.lobby.answer(0)
            harness.settle()
            socket.push(ServerMessage.Picks(2, 0, listOf(PickView("guest1", 0), PickView("guest2", 3))))
            harness.settle()
            val phase = assertIs<GamePhase.Answering>(harness.inLobbyState().phase)
            assertEquals(mapOf("guest1" to 0, "guest2" to 3), phase.picks)
            assertEquals(0, phase.myPick)
        }
}
