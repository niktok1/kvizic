package io.ntole.kvizic.e2e

import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.LobbyExit
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Whole games between real clients and the real server: each player's lobby session over CIO's
 * WebSockets and a proxy of their own, the server on Netty. What only this can show: that the two sides
 * agree on the protocol end to end, and that a player the network fails gets back into the game.
 */
class RealtimeE2eTest {
    private val closing = mutableListOf<AutoCloseable>()

    private fun <T : AutoCloseable> T.closedAfter(): T = also { closing += it }

    @AfterTest
    fun closeAll() {
        closing.asReversed().forEach { runCatching { it.close() } }
    }

    private fun server() = E2eServer().closedAfter()

    private fun player(
        name: String,
        server: E2eServer,
    ) = E2ePlayer(name, server).closedAfter()

    private suspend fun lobbyOf(
        host: E2ePlayer,
        vararg guests: E2ePlayer,
        settings: LobbySettings = LobbySettings(questionCount = 5, secondsPerQuestion = 10),
    ): String {
        host.lobby.create(settings)
        val code =
            (
                host.awaitState(what = "the new lobby") {
                    it is LobbySessionState.InLobby
                } as LobbySessionState.InLobby
            ).lobby.code
        guests.forEach { guest ->
            guest.lobby.join(code)
            guest.awaitState(what = "the lobby joined") { it is LobbySessionState.InLobby }
        }
        host.awaitState(what = "everyone in") { state ->
            state is LobbySessionState.InLobby && state.lobby.members.size == guests.size + 1 &&
                state.lobby.members.all { it.connected }
        }
        return code
    }

    @Test
    fun `three players play a whole game and see the same reveals and results`(): Unit =
        runBlocking {
            val server = server()
            val ana = player("ana", server)
            val boris = player("boris", server)
            val ceca = player("ceca", server)
            lobbyOf(ana, boris, ceca)

            ana.lobby.start()
            repeat(5) { index ->
                val phases = listOf(ana, boris, ceca).map { it.awaitAnswering(index) }
                assertEquals(1, phases.map { it.options }.toSet().size, "the same answers, in the same order")
                ana.lobby.answer(rightOf(phases[0]))
                boris.lobby.answer(wrongOf(phases[1]))
                ceca.lobby.answer(rightOf(phases[2]))
                val reveals = listOf(ana, boris, ceca).map { it.awaitReveal(index) }
                assertEquals(1, reveals.map { it.reveal }.toSet().size, "the same reveal for everyone")
                val points = reveals[0].reveal.results.associate { it.playerId to it.points }
                assertTrue(points.getValue(ana.id) > 0)
                assertTrue(points.getValue(boris.id) < 0, "a wrong answer costs")
                assertTrue(points.getValue(ceca.id) > 0)
            }

            val results =
                listOf(ana, boris, ceca).map { player ->
                    val over =
                        player.awaitPhase(what = "the results") {
                            it is GamePhase.Waiting &&
                                it.lastResults != null
                        }
                    assertNotNull((over as GamePhase.Waiting).lastResults)
                }
            assertEquals(1, results.toSet().size)
            assertEquals(
                boris.id,
                results
                    .first()
                    .standings
                    .last()
                    .playerId,
            )
            assertEquals(5, results.first().questionCount)

            // Nobody goes back by themselves; each does, by hand.
            listOf(ana, boris, ceca).forEach { it.lobby.backToLobby() }
            ana.awaitState(what = "everyone back") { state ->
                state is LobbySessionState.InLobby && state.lobby.members.none { it.onResults }
            }
            waitFor("the game recorded") { server.records.size == 1 }
        }

    @Test
    fun `a player whose network drops mid-question comes back and their answer counts`(): Unit =
        runBlocking {
            val server = server()
            val ana = player("ana", server)
            val boris = player("boris", server)
            lobbyOf(ana, boris)
            ana.lobby.start()

            val asked = ana.awaitAnswering(0)
            val his = boris.awaitAnswering(0)
            ana.lobby.answer(rightOf(asked))
            // His network goes just as he taps: the answer shows at once and is sent again once he is back.
            boris.proxy.cutAll()
            boris.lobby.answer(rightOf(his))

            val reveal = boris.awaitReveal(0).reveal
            val him = reveal.results.first { it.playerId == boris.id }
            assertEquals(reveal.correct, him.option)
            assertTrue(him.points > 0, "his answer counted")
            assertEquals(2, reveal.results.count { it.option != null })
        }

    @Test
    fun `a player cut off for a while keeps their seat and finds the game where it is`(): Unit =
        runBlocking {
            val server = server()
            val ana = player("ana", server)
            val boris = player("boris", server)
            lobbyOf(ana, boris)
            ana.lobby.start()
            ana.awaitAnswering(0)

            boris.proxy.refusing = true
            boris.proxy.cutAll()
            boris.awaitState(what = "reconnecting") { it is LobbySessionState.InLobby && it.reconnecting }
            ana.awaitState(what = "boris shown gone") { state ->
                state is LobbySessionState.InLobby && state.lobby.members.any { it.playerId != ana.id && !it.connected }
            }
            // The question is over without him: ana answers, he cannot.
            ana.lobby.answer(rightOf(ana.awaitAnswering(0)))
            ana.awaitReveal(0)

            boris.proxy.refusing = false
            boris.lobby.wake()
            val back =
                boris.awaitPhase(what = "back in the game") {
                    it !is GamePhase.Waiting &&
                        it !is GamePhase.Countdown
                }
            assertTrue(
                when (back) {
                    is GamePhase.Reading -> boris.id in back.players
                    is GamePhase.Answering -> boris.id in back.players
                    is GamePhase.Revealing -> boris.id in back.players
                    else -> false
                },
                "still a player of the game",
            )
            ana.awaitState(what = "boris shown back") { state ->
                state is LobbySessionState.InLobby && state.lobby.members.all { it.connected }
            }
        }

    @Test
    fun `a slow connection does not cost its player the first answer`(): Unit =
        runBlocking {
            val server = server()
            val near = player("near", server)
            val far = player("far", server)
            lobbyOf(near, far)
            // 120 ms each way: the server measures the round trip from its pings and takes it off far's
            // answer times, up to its cap.
            far.proxy.latency = 120.milliseconds
            delay(2.seconds)

            near.lobby.start()
            val farPhase = far.awaitAnswering(0)
            far.lobby.answer(rightOf(farPhase))
            val nearPhase = near.awaitAnswering(0)
            delay(250.milliseconds)
            near.lobby.answer(rightOf(nearPhase))

            val reveal = near.awaitReveal(0).reveal
            val byPlayer = reveal.results.associateBy { it.playerId }
            assertEquals(1, byPlayer.getValue(far.id).order, "far answered first in fact")
            assertEquals(2, byPlayer.getValue(near.id).order)
            assertTrue(byPlayer.getValue(far.id).points > byPlayer.getValue(near.id).points)
        }

    @Test
    fun `a server restarting sends every player home saying so`(): Unit =
        runBlocking {
            val server = server()
            val ana = player("ana", server)
            val boris = player("boris", server)
            lobbyOf(ana, boris)

            val stopping = thread { server.stop() }
            listOf(ana, boris).forEach { player ->
                val ended =
                    player.awaitState(
                        timeout = 20.seconds,
                        what = "sent home",
                    ) { it is LobbySessionState.Ended }
                assertEquals(LobbyExit.SERVER_RESTARTING, assertIs<LobbySessionState.Ended>(ended).exit)
            }
            stopping.join()
        }

    private suspend fun waitFor(
        what: String,
        condition: () -> Boolean,
    ) {
        repeat(200) {
            if (condition()) return
            delay(50.milliseconds)
        }
        throw AssertionError("$what did not happen")
    }
}
