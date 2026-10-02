package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.lobby.Visibility
import io.ntole.kvizic.core.protocol.AbortReason
import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.CloseCodes
import io.ntole.kvizic.core.protocol.CloseReason
import io.ntole.kvizic.core.protocol.NoticeKind
import io.ntole.kvizic.core.protocol.RejectCode
import io.ntole.kvizic.core.protocol.ServerMessage
import io.ntole.kvizic.core.question.Difficulty
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** A lobby's life around its games: settings, starts that fail, idleness, a restart's drain, solo runs. */
class LobbyLifecycleTest {
    @Test
    fun `the host changes the settings while waiting, within the rules, and everyone sees it`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            lobby.player("ceca").join()
            val wanted =
                LobbySettingsDto(
                    questionCount = 5,
                    secondsPerQuestion = 20,
                    topics = listOf("SPORT"),
                    visibility = Visibility.PUBLIC,
                )

            assertEquals(RejectCode.NOT_HOST, boris.answerTo(boris.settings(wanted)))
            assertEquals(RejectCode.INVALID_SETTINGS, ana.answerTo(ana.settings(wanted.copy(questionCount = 7))))
            assertEquals(
                RejectCode.INVALID_SETTINGS,
                ana.answerTo(ana.settings(wanted.copy(topics = listOf("COOKING")))),
            )
            assertEquals(
                RejectCode.INVALID_SETTINGS,
                ana.answerTo(ana.settings(wanted.copy(maxPlayers = 2))),
                "three are here",
            )
            assertNull(ana.answerTo(ana.settings(wanted)))

            assertEquals(wanted, boris.last<ServerMessage.SettingsChanged>().settings)
            assertEquals(LobbyKind.PUBLIC, lobby.summary().kind, "now listed publicly")

            ana.start()
            assertEquals(RejectCode.WRONG_PHASE, ana.answerTo(ana.settings(wanted.copy(questionCount = 10))))
            assertEquals(listOf("SPORT"), lobby.asked.single().topics)
            lobby.assertInvariants()
        }

    @Test
    fun `a start whose questions fail to load, or find none, goes back to waiting`() =
        runTest {
            val failing = LobbyScenario(this)
            val ana = failing.player("ana").join()
            failing.failLoading = IllegalStateException("database down")
            ana.start()
            failing.settle()
            assertEquals(AbortReason.NO_QUESTIONS, ana.last<ServerMessage.GameAborted>().reason)
            assertFalse(failing.summary().inGame)

            val empty = LobbyScenario(this, questions = emptyList())
            val boris = empty.player("boris").join()
            boris.start()
            empty.settle()
            assertEquals(AbortReason.NO_QUESTIONS, boris.last<ServerMessage.GameAborted>().reason)
            assertNull(boris.answerTo(boris.start()), "and the host may try again")
        }

    @Test
    fun `a bank too small for the game shortens it and says so`() =
        runTest {
            val lobby = LobbyScenario(this, LobbySettingsDto(questionCount = 10), questions = sampleQuestions(4))
            val ana = lobby.player("ana").join()
            ana.start()
            lobby.wait(lobby.timings.countdown)

            assertEquals(4, ana.last<ServerMessage.GameStarted>().questionCount)
            assertTrue(ana.all<ServerMessage.Notice>().any { it.kind == NoticeKind.GAME_SHORTENED })
        }

    @Test
    fun `a host leaving during the countdown leaves the game to those still there`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            ana.start()
            lobby.settle()
            ana.leave()
            boris.say(ClientMessage.BackToLobby(boris.nextId()))
            // Boris is the host now and in the lobby, so he plays: the game goes ahead with him alone.
            lobby.wait(lobby.timings.countdown)
            assertEquals(listOf("boris"), boris.last<ServerMessage.GameStarted>().players)
        }

    @Test
    fun `a waiting lobby with no game for half an hour closes`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()

            lobby.wait(lobby.timings.idle - 1.minutes)
            assertFalse(lobby.closed)
            lobby.wait(2.minutes)

            assertTrue(lobby.closed)
            assertEquals(ServerMessage.Closing(CloseReason.IDLE), ana.history.last())
            assertEquals(CloseCodes.NORMAL, ana.closedCode())
        }

    @Test
    fun `a drain closes a waiting lobby after a moment and refuses new seats`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()

            lobby.lobby.send(LobbyCommand.Drain(lobby.timeSource.markNow() + 280.seconds))
            lobby.settle()
            assertEquals(NoticeKind.SERVER_RESTARTING, ana.last<ServerMessage.Notice>().kind)
            assertEquals(280_000L, ana.last<ServerMessage.Notice>().remainingMs)
            assertEquals(ReserveResult.Draining, lobby.player("boris").reserve())
            assertEquals(RejectCode.DRAINING, ana.answerTo(ana.start()))

            lobby.wait(lobby.timings.drainWaitingLobby + lobby.timings.tick)
            assertTrue(lobby.closed)
            assertEquals(CloseCodes.SERVER_RESTARTING, ana.closedCode())
        }

    @Test
    fun `a game that fits into the drain plays to its end, and one that does not ends at the next reveal`() =
        runTest {
            val fits = LobbyScenario(this, LobbySettingsDto(questionCount = 3))
            val ana = fits.player("ana").join()
            ana.start()
            fits.wait(fits.timings.countdown)
            fits.lobby.send(LobbyCommand.Drain(fits.timeSource.markNow() + 280.seconds))
            fits.settle()
            playAlone(fits, ana, count = 3)
            val finished = ana.last<ServerMessage.GameOver>().results
            assertFalse(finished.endedEarly)
            assertEquals(
                3,
                fits.records
                    .single()
                    .questions.size,
            )
            assertTrue(fits.closed, "closed once the game was over")

            val tooLong = LobbyScenario(this, LobbySettingsDto(questionCount = 20, secondsPerQuestion = 30))
            val boris = tooLong.player("boris").join()
            boris.start()
            tooLong.wait(tooLong.timings.countdown)
            tooLong.lobby.send(LobbyCommand.Drain(tooLong.timeSource.markNow() + 60.seconds))
            tooLong.settle()
            tooLong.wait(tooLong.timings.readMax)
            boris.answerRight()
            val reveal = boris.last<ServerMessage.Revealed>().reveal
            assertTrue(reveal.last, "the reveal says it is the last")
            tooLong.wait(reveal.nextInMs.milliseconds)
            assertTrue(boris.last<ServerMessage.GameOver>().results.endedEarly)
            assertEquals(
                1,
                tooLong.records
                    .single()
                    .questions.size,
                "the question played is kept",
            )
            assertTrue(tooLong.closed)
        }

    @Test
    fun `a solo run keeps its format but its level, and reports a new personal best`() =
        runTest {
            val solo = LobbyScenario(this, LobbySettingsDto.SOLO, kind = LobbyKind.SOLO)
            solo.soloBestBefore = 500
            val ana = solo.player("ana").join()
            assertEquals(ReserveResult.Full, solo.player("boris").reserve(), "one seat")
            assertEquals(
                RejectCode.INVALID_SETTINGS,
                ana.answerTo(ana.settings(LobbySettingsDto(questionCount = 5, maxPlayers = 1))),
            )
            assertNull(ana.answerTo(ana.settings(LobbySettingsDto.SOLO.copy(difficulty = Difficulty.HARD))))

            ana.start()
            solo.wait(solo.timings.countdown)
            assertEquals("ana", solo.asked.single().soloPlayer)
            assertEquals(Difficulty.HARD, solo.asked.single().difficulty, "its best is the hard one")
            playAlone(solo, ana, count = LobbySettingsDto.SOLO.questionCount)

            val best = checkNotNull(ana.last<ServerMessage.GameOver>().results.personalBest)
            assertEquals(1_000, best.score, "ten right at once")
            assertEquals(500, best.previous)
            assertTrue(best.isNew)
            assertFalse(
                solo.records
                    .single()
                    .players
                    .single()
                    .won,
                "a solo run is never a win",
            )
        }

    @Test
    fun `reactions come in bursts and are then held to a pace`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()

            repeat(5) { ana.say(ClientMessage.React("bravo")) }
            assertEquals(lobby.timings.reactionBurst, boris.all<ServerMessage.Reacted>().size)

            lobby.wait(lobby.timings.reactionEvery)
            ana.say(ClientMessage.React("fire"))
            assertEquals("fire", boris.last<ServerMessage.Reacted>().reaction)

            ana.say(ClientMessage.React("not-a-reaction"))
            assertEquals("fire", boris.last<ServerMessage.Reacted>().reaction, "only the fixed reactions")
        }

    @Test
    fun `waiting lobbies show how many are online and searching, once per change`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()

            lobby.lobby.send(LobbyCommand.Presence(online = 12, searching = 3))
            lobby.lobby.send(LobbyCommand.Presence(online = 12, searching = 3))
            lobby.settle()

            assertEquals(listOf(ServerMessage.Presence(12, 3)), ana.all<ServerMessage.Presence>())
            val late = lobby.player("boris").join()
            assertEquals(
                ServerMessage.Presence(12, 3),
                late.last<ServerMessage.Presence>(),
                "a newcomer gets the last count",
            )
        }

    @Test
    fun `a game whose record cannot be kept still ends for its players`() =
        runTest {
            val lobby = LobbyScenario(this, LobbySettingsDto(questionCount = 3))
            lobby.failRecording = IllegalStateException("the sink is down")
            val ana = lobby.player("ana").join()
            ana.start()
            lobby.wait(lobby.timings.countdown)
            playAlone(lobby, ana, count = 3)

            assertEquals(1, ana.all<ServerMessage.GameOver>().size)
            assertFalse(lobby.closed, "the lobby plays on")
            assertNull(ana.answerTo(ana.back()))
        }

    @Test
    fun `a host's new name for the room is cleaned and told to everyone as it is kept`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()

            ana.settings(LobbySettingsDto(name = "  Петак   увече  "))
            assertEquals("Петак увече", boris.last<ServerMessage.SettingsChanged>().settings.name)

            ana.settings(LobbySettingsDto(name = " \u200B "))
            assertNull(boris.last<ServerMessage.SettingsChanged>().settings.name, "a name with nothing left is none")
        }

    @Test
    fun `a lobby that fails closes its sockets, and leaves the registry`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            lobby.failTopics = IllegalStateException("no topics")

            ana.settings(LobbySettingsDto(questionCount = 5))

            assertTrue(lobby.closed)
            assertEquals(ServerMessage.Closing(CloseReason.LOBBY_CLOSED), boris.history.last())
            assertEquals(CloseCodes.INTERNAL, ana.closedCode())
            assertEquals(CloseCodes.INTERNAL, boris.closedCode())
            assertEquals(ReserveResult.Closed, lobby.lobby.reserve(Seat("ceca", "s", "Ceca", "fox")))
        }

    @Test
    fun `a lobby that fails, and fails again closing, still lets go of every socket`() =
        runTest {
            val lobby = LobbyScenario(this, LobbySettingsDto(questionCount = 3))
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            ana.start()
            lobby.wait(lobby.timings.countdown)
            // The game's end reads the wall clock, and so does the close after the failure.
            lobby.failWallClock = IllegalStateException("no clock")
            repeat(3) {
                lobby.wait(ana.last<ServerMessage.QuestionShown>().readMs.milliseconds)
                ana.answerRight()
                boris.answerRight()
                if (!lobby.closed) {
                    lobby.wait(
                        ana
                            .last<ServerMessage.Revealed>()
                            .reveal.nextInMs.milliseconds,
                    )
                }
            }

            assertTrue(lobby.closed, "the registry is told")
            assertEquals(CloseCodes.INTERNAL, ana.closedCode())
            assertEquals(CloseCodes.INTERNAL, boris.closedCode())
            assertEquals(ReserveResult.Closed, lobby.lobby.reserve(Seat("ceca", "s", "Ceca", "fox")))
            lobby.assertInvariants()
        }

    private fun TestPlayer.settings(settings: LobbySettingsDto): Int =
        nextId().also {
            say(ClientMessage.UpdateSettings(it, settings))
        }

    /** Plays [count] questions with [player] alone answering each right at once. */
    private fun playAlone(
        lobby: LobbyScenario,
        player: TestPlayer,
        count: Int,
    ) {
        repeat(count) {
            lobby.wait(player.last<ServerMessage.QuestionShown>().readMs.milliseconds)
            player.answerRight()
            lobby.wait(
                player
                    .last<ServerMessage.Revealed>()
                    .reveal.nextInMs.milliseconds,
            )
        }
    }
}
