package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.protocol.ServerMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** A member's level, shown on their seat, and what a game finished in the room adds to it. */
class LobbyLevelsTest {
    @Test
    fun `a seat shows the level its experience makes, and a new player starts at the first`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").also { it.xp = 150 }.join()
            val boris = lobby.player("boris").join()

            val seen =
                boris
                    .last<ServerMessage.Snapshot>()
                    .lobby.members
                    .associate { it.player to it.level }
            assertEquals(mapOf("ana" to 4, "boris" to 1), seen)
            assertEquals(
                4,
                ana
                    .last<ServerMessage.Snapshot>()
                    .lobby.members
                    .first { it.player == "ana" }
                    .level,
            )
        }

    @Test
    fun `sitting down again with more experience tells the room the level is up`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            boris.clear()

            ana.xp = 40
            ana.reserve()

            assertEquals(3, boris.last<ServerMessage.MemberUpdated>().member.level)
        }

    @Test
    fun `a game finished adds to the level of those who stayed, in the results and on their seats`() =
        runTest {
            val lobby = LobbyScenario(this, LobbySettingsDto(questionCount = 3))
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            val ceca = lobby.player("ceca").join()
            assertTrue(ana.start().let { ana.answerTo(it) == null }, "the start is taken")
            lobby.wait(lobby.timings.countdown)

            repeat(3) { index ->
                lobby.wait(lobby.timings.readTime(lobby.questions[index].text))
                ana.answerRight()
                boris.answerWrong()
                lobby.wait(15.seconds + lobby.timings.answerGrace)
                lobby.wait(
                    ceca
                        .last<ServerMessage.Revealed>()
                        .reveal.nextInMs.milliseconds,
                )
            }

            // Ana won with three right: 20 + 3 + 20 = 43, the third level. The other two earned 20 for staying: the second.
            val results = ceca.last<ServerMessage.GameOver>().results
            assertEquals(
                mapOf("ana" to 3, "boris" to 2, "ceca" to 2),
                results.standings.associate {
                    it.player to
                        it.level
                },
            )
            val told = ceca.all<ServerMessage.MemberUpdated>().associate { it.member.player to it.member.level }
            assertEquals(mapOf("ana" to 3, "boris" to 2, "ceca" to 2), told)
            lobby.assertInvariants()
        }
}
