package io.ntole.kvizic.server.realtime

import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.PhaseView
import io.ntole.kvizic.core.protocol.ServerMessage
import io.ntole.kvizic.server.lobby.sampleQuestions
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Whole games over real sockets, players dropping out and coming back included. */
class SocketGameTest {
    @Test
    fun `three players play a whole game over sockets and go back to the lobby by hand`() =
        runGameServer("socket-game") { server ->
            val ana = server.guest()
            val ticket = server.create(ana, LobbySettingsDto(questionCount = 5, secondsPerQuestion = 10))
            val host = server.connect(ticket)
            host.await<ServerMessage.Snapshot>()
            val (boris, borisSocket) = server.joinWithSocket(ticket.code)
            val (ceca, cecaSocket) = server.joinWithSocket(ticket.code)
            val sockets = listOf(host, borisSocket, cecaSocket)

            host.send(ClientMessage.Start(host.nextId()))
            val started = host.await<ServerMessage.GameStarted>()
            assertEquals(setOf(ana.playerId, boris.playerId, ceca.playerId), started.players.toSet())

            repeat(5) { index ->
                val opened = sockets.map { socket -> socket.await<ServerMessage.AnswersOpened> { it.index == index } }
                assertEquals(1, opened.map { it.options }.toSet().size, "everyone sees the same answers")
                val right = rightOptionOf(opened.first())
                val wrong = (right + 1) % opened.first().options.size
                host.send(ClientMessage.Answer(host.nextId(), index, right))
                host.await<ServerMessage.Ack>()
                borisSocket.send(ClientMessage.Answer(borisSocket.nextId(), index, wrong))
                cecaSocket.send(ClientMessage.Answer(cecaSocket.nextId(), index, right))

                val reveals =
                    sockets.map { socket ->
                        socket.await<ServerMessage.Revealed> { it.reveal.index == index }
                    }
                val reveal = reveals.first().reveal
                assertEquals(right, reveal.correct)
                assertTrue(reveals.all { it.reveal == reveal }, "everyone sees the same reveal")
                val points = reveal.results.associate { it.player to it.points }
                assertTrue(points.getValue(ana.playerId) > 0)
                assertTrue(points.getValue(boris.playerId) < 0, "a wrong answer costs")
                assertTrue(points.getValue(ceca.playerId) > 0)
            }

            val over = sockets.map { it.await<ServerMessage.GameOver>() }
            val results = over.first().results
            assertTrue(over.all { it.results == results })
            assertEquals(boris.playerId, results.standings.last().player)
            assertTrue(results.standings.all { it.finished })
            eventually(what = "the game recorded") { server.records.size == 1 }
            assertEquals(
                5,
                server.records
                    .single()
                    .questions.size,
            )

            // Nobody goes back by themselves.
            assertTrue(host.staysOpenFor(1.seconds))
            assertTrue(host.all<ServerMessage.MemberUpdated>().none { !it.member.onResults && it.v > over.first().v })
            sockets.forEach { socket -> socket.send(ClientMessage.BackToLobby(socket.nextId())) }
            eventually(what = "everyone back") {
                host.all<ServerMessage.MemberUpdated>().count { it.v > over.first().v && !it.member.onResults } >= 3
            }
            sockets.forEach { it.assertVersionsInOrder() }
        }

    @Test
    fun `a player who drops out mid-game comes back with a new ticket into the game as it stands`() =
        runGameServer("socket-rejoin", questions = sampleQuestions(10)) { server ->
            val ana = server.guest()
            val ticket = server.create(ana, LobbySettingsDto(questionCount = 5, secondsPerQuestion = 10))
            val host = server.connect(ticket)
            host.await<ServerMessage.Snapshot>()
            val (boris, borisSocket) = server.joinWithSocket(ticket.code)

            host.send(ClientMessage.Start(host.nextId()))
            val opened = host.await<ServerMessage.AnswersOpened> { it.index == 0 }
            borisSocket.session.cancel()
            host.await<ServerMessage.MemberUpdated> { it.member.player == boris.playerId && !it.member.connected }

            val back = server.connect(server.join(boris, ticket.code))
            val snapshot = back.await<ServerMessage.Snapshot>()
            val phase = assertIs<PhaseView.Answering>(snapshot.phase)
            assertEquals(0, phase.question.index)
            assertEquals(opened.options, phase.options, "the same answers, in the same order")
            assertTrue(boris.playerId in phase.players, "still a player of the game")
            assertNull(phase.yourPick)
            host.await<ServerMessage.MemberUpdated> { it.member.player == boris.playerId && it.member.connected }

            back.send(ClientMessage.Answer(back.nextId(), 0, rightOptionOf(opened)))
            back.await<ServerMessage.Ack>()
            host.send(ClientMessage.Answer(host.nextId(), 0, rightOptionOf(opened)))
            val reveal = host.await<ServerMessage.Revealed>().reveal
            assertTrue(reveal.results.first { it.player == boris.playerId }.points > 0, "his answer counted")
            back.assertVersionsInOrder()
        }

    @Test
    fun `a late joiner watches the game under way and plays the next`() =
        runGameServer("socket-late-joiner") { server ->
            val ana = server.guest()
            val ticket = server.create(ana, LobbySettingsDto(questionCount = 5, secondsPerQuestion = 10))
            val host = server.connect(ticket)
            host.await<ServerMessage.Snapshot>()
            host.send(ClientMessage.Start(host.nextId()))
            val first = host.await<ServerMessage.AnswersOpened>()

            val (ceca, late) = server.joinWithSocket(ticket.code)
            val snapshot = late.history.filterIsInstance<ServerMessage.Snapshot>().last()
            val players =
                when (val phase = snapshot.phase) {
                    is PhaseView.Reading -> phase.players
                    is PhaseView.Answering -> phase.players
                    is PhaseView.Revealing -> phase.players
                    else -> error("no game in $phase")
                }
            assertTrue(ceca.playerId !in players, "watching, not playing")
            host.send(ClientMessage.Answer(host.nextId(), first.index, rightOptionOf(first)))
            late.await<ServerMessage.Revealed>()
            val opened = late.await<ServerMessage.AnswersOpened>()
            late.send(ClientMessage.Answer(late.nextId(), opened.index, 0))
            late.await<ServerMessage.Rejected>()
        }
}
