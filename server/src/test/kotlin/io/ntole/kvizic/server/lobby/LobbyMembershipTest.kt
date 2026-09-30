package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.lobby.Visibility
import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.CloseCodes
import io.ntole.kvizic.core.protocol.CloseReason
import io.ntole.kvizic.core.protocol.LeaveReason
import io.ntole.kvizic.core.protocol.RejectCode
import io.ntole.kvizic.core.protocol.ServerMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Who is in a lobby, as members come, drop, come back, are kicked and leave. */
class LobbyMembershipTest {
    @Test
    fun `the first seat is the host, and a joiner is announced only once their socket comes`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()

            val boris = lobby.player("boris")
            assertEquals(ReserveResult.Reserved, boris.reserve())
            assertTrue(ana.all<ServerMessage.MemberJoined>().isEmpty(), "a held seat is nobody yet")
            assertEquals(2, lobby.summary().seatsTaken)

            boris.attach()
            assertEquals("boris", ana.last<ServerMessage.MemberJoined>().member.player)
            assertTrue(boris.all<ServerMessage.MemberJoined>().isEmpty(), "his own join comes in his snapshot")
            assertTrue(boris.received.first() is ServerMessage.Welcome)
            val snapshot = checkNotNull(boris.lastSnapshot())
            assertEquals("ana", snapshot.lobby.host)
            assertEquals(listOf("ana", "boris"), snapshot.lobby.members.map { it.player })
            assertEquals(listOf(0, 1), snapshot.lobby.members.map { it.seat }, "each seat its own colour")
            lobby.assertInvariants()
        }

    @Test
    fun `a full lobby refuses another seat, and a rejoin takes none`() =
        runTest {
            val lobby = LobbyScenario(this, LobbySettingsDto(questionCount = 3, maxPlayers = 2))
            val ana = lobby.player("ana").join()
            lobby.player("boris").join()

            assertEquals(ReserveResult.Full, lobby.player("ceca").reserve())
            assertEquals(ReserveResult.Reserved, ana.reserve(), "a member rejoining keeps their seat")
            assertEquals(2, lobby.summary().seatsTaken)
        }

    @Test
    fun `a held seat nobody comes for goes when the ticket would have expired`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            lobby.player("boris").reserve()

            lobby.wait(lobby.timings.ticketTtl + 6.seconds)

            assertEquals(listOf("boris"), lobby.gone)
            assertTrue(ana.all<ServerMessage.MemberLeft>().isEmpty(), "never announced, so never announced gone")
            assertEquals(1, lobby.summary().seatsTaken)
        }

    @Test
    fun `a member who drops out of a waiting lobby keeps their seat through the grace, then loses it`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()

            boris.drop()
            assertEquals(false, ana.last<ServerMessage.MemberUpdated>().member.connected)

            lobby.wait(lobby.timings.lobbyGrace - 2.seconds)
            assertTrue(ana.all<ServerMessage.MemberLeft>().isEmpty())
            lobby.wait(3.seconds)
            val left = ana.last<ServerMessage.MemberLeft>()
            assertEquals("boris" to LeaveReason.TIMED_OUT, left.player to left.reason)
            lobby.assertInvariants()
        }

    @Test
    fun `a player who drops out mid-game keeps their place to its end, and loses it after`() =
        runTest {
            val lobby = LobbyScenario(this, LobbySettingsDto(questionCount = 10, secondsPerQuestion = 30))
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            ana.start()
            lobby.wait(lobby.timings.countdown)
            boris.drop()

            lobby.wait(lobby.timings.lobbyGrace + 1.minutes)
            assertTrue(
                ana.all<ServerMessage.MemberLeft>().isEmpty(),
                "still in the game, past the waiting lobby's grace",
            )
            assertEquals(2, lobby.summary().seatsTaken)

            lobby.wait(6.minutes)
            assertEquals(1, ana.all<ServerMessage.GameOver>().size)
            assertEquals(
                LeaveReason.TIMED_OUT,
                ana.last<ServerMessage.MemberLeft>().reason,
                "gone once the game is over",
            )
            lobby.assertInvariants()
        }

    @Test
    fun `a member coming back gets a fresh snapshot and everyone sees them connected again`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            boris.drop()
            ana.clear()

            boris.attach()

            assertEquals(true, ana.last<ServerMessage.MemberUpdated>().member.connected)
            assertTrue(ana.all<ServerMessage.MemberJoined>().isEmpty(), "announced once")
            assertEquals(2, checkNotNull(boris.lastSnapshot()).lobby.members.size)
            lobby.assertInvariants()
        }

    @Test
    fun `a second socket of the same player replaces the first`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val first = checkNotNull(ana.connection)

            ana.attach()

            assertEquals(CloseCodes.REPLACED, first.closedWith)
            assertEquals(ServerMessage.Closing(CloseReason.REPLACED), first.messages.last())
            // The replaced socket closing afterwards changes nothing.
            lobby.lobby.send(LobbyCommand.Detach(first))
            lobby.settle()
            assertEquals(setOf("ana"), lobby.summary().connected)
        }

    @Test
    fun `the host leaving hands the lobby to the member there longest`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            val ceca = lobby.player("ceca").join()

            ana.leave()

            assertEquals(CloseCodes.NORMAL, ana.closedCode())
            assertEquals("boris", ceca.last<ServerMessage.HostChanged>().host)
            assertEquals(LeaveReason.LEFT, boris.last<ServerMessage.MemberLeft>().reason)
            assertNull(boris.answerTo(boris.start()), "boris may start now")
            lobby.assertInvariants()
        }

    @Test
    fun `the host may hand hosting over and nobody else may`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()

            assertEquals(RejectCode.NOT_HOST, boris.answerTo(boris.handTo("boris")))
            assertEquals(RejectCode.NO_SUCH_PLAYER, ana.answerTo(ana.handTo("nobody")))
            assertNull(ana.answerTo(ana.handTo("boris")))
            assertEquals("boris", boris.last<ServerMessage.HostChanged>().host)
        }

    @Test
    fun `a kicked member is told, disconnected and may not come back while the lobby lasts`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            val ceca = lobby.player("ceca").join()

            assertEquals(RejectCode.NOT_HOST, boris.answerTo(boris.kick(ceca)))
            assertEquals(RejectCode.NO_SUCH_PLAYER, ana.answerTo(ana.kick(ana)), "not herself")
            assertNull(ana.answerTo(ana.kick(boris)))

            assertEquals(CloseCodes.KICKED, boris.closedCode())
            assertEquals(ServerMessage.Closing(CloseReason.KICKED), boris.history.last())
            assertEquals(LeaveReason.KICKED, ceca.last<ServerMessage.MemberLeft>().reason)
            assertEquals(ReserveResult.Banned, boris.reserve())
            lobby.assertInvariants()
        }

    @Test
    fun `a socket that cannot keep up is dropped and its member resyncs on coming back`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            checkNotNull(boris.connection).accepting = false

            lobby.player("ceca").join()

            assertEquals(CloseCodes.TOO_MUCH, boris.closedCode())
            assertFalse("boris" in lobby.summary().connected)
            assertEquals(false, ana.last<ServerMessage.MemberUpdated>().member.connected)

            boris.attach()
            assertEquals(3, checkNotNull(boris.lastSnapshot()).lobby.members.size, "the snapshot has what he missed")
            lobby.assertInvariants()
        }

    @Test
    fun `a public lobby's host who is gone or idle hands over, and a private one's waits`() =
        runTest {
            val public =
                LobbyScenario(
                    this,
                    LobbySettingsDto(questionCount = 3, visibility = Visibility.PUBLIC),
                    kind = LobbyKind.PUBLIC,
                )
            val ana = public.player("ana").join()
            val boris = public.player("boris").join()
            ana.drop()
            public.wait(public.timings.absentPublicHost + 1.seconds)
            assertEquals("boris", boris.last<ServerMessage.HostChanged>().host, "strangers are not left waiting")

            val idle =
                LobbyScenario(
                    this,
                    LobbySettingsDto(questionCount = 3, visibility = Visibility.PUBLIC),
                    kind = LobbyKind.PUBLIC,
                )
            idle.player("dora").join()
            val ema = idle.player("ema").join()
            idle.wait(idle.timings.afkHost - 10.seconds)
            ema.say(ClientMessage.React("bravo"))
            idle.wait(11.seconds)
            assertEquals("ema", ema.last<ServerMessage.HostChanged>().host, "an idle host hands over to one who is not")
            idle.wait(idle.timings.afkHost * 2)
            assertEquals(1, ema.all<ServerMessage.HostChanged>().size, "and it never comes back to someone as idle")

            val private = LobbyScenario(this)
            val filip = private.player("filip").join()
            val goran = private.player("goran").join()
            filip.drop()
            private.wait(private.timings.absentPublicHost + 1.seconds)
            assertTrue(goran.all<ServerMessage.HostChanged>().isEmpty(), "friends wait for each other")
            private.wait(private.timings.lobbyGrace)
            assertEquals("goran", goran.last<ServerMessage.HostChanged>().host, "until the grace is over")
        }

    @Test
    fun `a lobby everyone left closes`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()

            boris.leave()
            assertFalse(lobby.closed)
            ana.leave()

            assertTrue(lobby.closed)
            assertEquals(ReserveResult.Closed, lobby.lobby.reserve(Seat("ceca", "s", "Ceca", "fox")))
        }

    @Test
    fun `a game every player left ends there and its results are kept`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            ana.start()
            lobby.wait(lobby.timings.countdown)
            val ceca = lobby.player("ceca").join()

            ana.leave()
            assertTrue(ceca.all<ServerMessage.GameOver>().isEmpty(), "boris still plays")
            boris.leave()

            val results = ceca.last<ServerMessage.GameOver>().results
            assertTrue(results.endedEarly)
            assertEquals(
                listOf("ana" to false, "boris" to false),
                results.standings
                    .map {
                        it.player to it.finished
                    }.sortedBy { it.first },
            )
            ceca.leave()
            assertEquals(1, lobby.records.size, "the game is recorded once")
            assertTrue(lobby.closed)
        }

    private fun TestPlayer.handTo(player: String): Int = nextId().also { say(ClientMessage.TransferHost(it, player)) }
}
