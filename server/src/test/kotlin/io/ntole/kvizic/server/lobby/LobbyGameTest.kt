package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.protocol.PhaseView
import io.ntole.kvizic.core.protocol.RejectCode
import io.ntole.kvizic.core.protocol.ServerMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** A game from the host's start to the results, as the lobby's loop plays it, on virtual time. */
class LobbyGameTest {
    private fun LobbyScenario.readTime(index: Int) = timings.readTime(questions[index].text)

    /** Starts the game and runs its countdown, which shows the first question. */
    private fun LobbyScenario.startGame(host: TestPlayer) {
        assertNull(host.answerTo(host.start()), "the start is taken")
        wait(timings.countdown)
    }

    /** From a question shown to its answers open. */
    private fun LobbyScenario.openAnswers(index: Int) = wait(readTime(index))

    @Test
    fun `three players play a whole game and the results are what they scored`() =
        runTest {
            val lobby = LobbyScenario(this, LobbySettingsDto(questionCount = 3))
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            val ceca = lobby.player("ceca").join()

            lobby.startGame(ana)
            val started = ana.last<ServerMessage.GameStarted>()
            assertEquals(listOf("ana", "boris", "ceca"), started.players)
            assertEquals(3, started.questionCount)

            repeat(3) { index ->
                assertEquals(index, ana.currentQuestion())
                lobby.openAnswers(index)
                ana.answerRight()
                boris.answerWrong()
                // Ceca never answers, so the question runs its whole time, and the grace.
                lobby.wait(15.seconds + lobby.timings.answerGrace)

                val reveal = ceca.last<ServerMessage.Revealed>().reveal
                assertEquals(index, reveal.index)
                assertEquals(
                    listOf(100, -40, 0),
                    reveal.results.map { it.points },
                    "right at once first, wrong at once",
                )
                assertEquals(listOf(1, null, null), reveal.results.map { it.order })
                assertEquals(index == 2, reveal.last)
                lobby.wait(reveal.nextInMs.milliseconds)
            }

            val results = ceca.last<ServerMessage.GameOver>().results
            assertEquals(
                listOf("ana" to 300, "ceca" to 0, "boris" to -120),
                results.standings.map {
                    it.player to
                        it.score
                },
            )
            assertEquals(listOf(1, 2, 3), results.standings.map { it.rank })
            assertEquals(false, results.endedEarly)

            val record = lobby.records.single()
            assertEquals(listOf(true, false, false), record.players.map { it.won })
            assertEquals(3, record.questions.size)
            assertEquals(
                mapOf("ana" to true, "boris" to false),
                record.questions
                    .first()
                    .answers
                    .mapValues { it.value.correct },
            )
            lobby.assertInvariants()
        }

    @Test
    fun `a question ends as soon as every connected player has locked in`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            lobby.startGame(ana)
            lobby.openAnswers(0)

            ana.answerRight()
            assertTrue(ana.all<ServerMessage.Revealed>().isEmpty(), "one answer left")
            boris.answerRight()

            assertEquals(0, ana.last<ServerMessage.Revealed>().reveal.index, "revealed without waiting on the clock")
            lobby.assertInvariants()
        }

    @Test
    fun `speed and the order of right answers both count`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            val ceca = lobby.player("ceca").join()
            lobby.startGame(ana)
            lobby.openAnswers(0)

            ana.answerRight()
            lobby.wait(1.seconds)
            ceca.answerRight()
            lobby.wait(1.seconds)
            boris.answerRight()

            val results =
                ana
                    .last<ServerMessage.Revealed>()
                    .reveal.results
                    .associateBy { it.player }
            assertEquals(100, results.getValue("ana").points, "50 + 40 + first's 10")
            assertEquals(87 + 5, results.getValue("ceca").points, "50 + 40 · 14/15, rounded, + second's 5")
            assertEquals(85 + 2, results.getValue("boris").points, "50 + 40 · 13/15, rounded, + third's 2")
            assertEquals(listOf(1, 2, 3), listOf("ana", "ceca", "boris").map { results.getValue(it).order })
            assertEquals(1_000L, results.getValue("ceca").timeMs)
        }

    @Test
    fun `a wrong answer costs more the faster it was, and nothing with the minus off`() =
        runTest {
            val withMinus = LobbyScenario(this)
            val ana = withMinus.player("ana").join()
            val boris = withMinus.player("boris").join()
            withMinus.startGame(ana)
            withMinus.openAnswers(0)
            ana.answerWrong()
            withMinus.wait(7.5.seconds)
            boris.answerWrong()

            val points =
                ana
                    .last<ServerMessage.Revealed>()
                    .reveal.results
                    .associate { it.player to it.points }
            assertEquals(-40, points.getValue("ana"), "at once")
            assertEquals(-14, points.getValue("boris"), "halfway: 5 + 35 · 0.25, rounded")

            val withoutMinus = LobbyScenario(this, LobbySettingsDto(questionCount = 3, wrongAnswerPenalty = false))
            val dora = withoutMinus.player("dora").join()
            withoutMinus.startGame(dora)
            withoutMinus.openAnswers(0)
            dora.answerWrong()
            assertEquals(
                0,
                dora
                    .last<ServerMessage.Revealed>()
                    .reveal.results
                    .single()
                    .points,
            )
        }

    @Test
    fun `the round trip comes off an answer's time, but no more than the cap`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            lobby.startGame(ana)
            lobby.openAnswers(0)

            lobby.wait(1.seconds)
            ana.answerRight(rtt = 200.milliseconds)
            boris.answerRight(rtt = 5.seconds)

            val times =
                ana
                    .last<ServerMessage.Revealed>()
                    .reveal.results
                    .associate { it.player to it.timeMs }
            assertEquals(800L, times.getValue("ana"))
            assertEquals(700L, times.getValue("boris"), "a round trip past the cap counts as the cap")
        }

    @Test
    fun `an answer is locked in once given`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            lobby.player("boris").join()
            lobby.startGame(ana)
            lobby.openAnswers(0)

            assertNull(ana.answerTo(ana.answer(1)))
            assertEquals(RejectCode.ALREADY_ANSWERED, ana.answerTo(ana.answer(2)))
            assertNull(ana.answerTo(ana.answer(1)), "the same answer again is taken as a resend")
        }

    @Test
    fun `answers are taken only while the answers are open, to the question asked, from its players`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            assertEquals(RejectCode.WRONG_PHASE, ana.answerTo(ana.answer(0, question = 0)), "no game yet")

            lobby.startGame(ana)
            assertEquals(RejectCode.TOO_EARLY, ana.answerTo(ana.answer(0)), "the question is still being read")

            lobby.openAnswers(0)
            assertEquals(RejectCode.WRONG_QUESTION, ana.answerTo(ana.answer(0, question = 1)))
            assertEquals(RejectCode.INVALID_OPTION, ana.answerTo(ana.answer(4)))
            assertEquals(RejectCode.INVALID_OPTION, ana.answerTo(ana.answer(-1)))

            val late = lobby.player("dora").join()
            assertEquals(
                RejectCode.NOT_PLAYING,
                late.answerTo(late.answer(0, question = 0)),
                "she joined after the start",
            )

            lobby.wait(15.seconds + lobby.timings.answerGrace)
            assertEquals(RejectCode.TOO_LATE, boris.answerTo(boris.answer(0, question = 0)))
        }

    @Test
    fun `a player who locked in sees everyone's picks, and one who did not sees only who answered`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            val ceca = lobby.player("ceca").join()
            lobby.startGame(ana)
            lobby.openAnswers(0)
            ana.clear()
            boris.clear()
            ceca.clear()

            ana.answer(2)
            assertEquals(listOf("ana" to 2), ana.last<ServerMessage.Picks>().picks.map { it.player to it.option })
            assertEquals(listOf("ana"), boris.last<ServerMessage.Progress>().answered)
            assertTrue(boris.all<ServerMessage.Picks>().isEmpty(), "boris has not answered")

            boris.answer(1)
            assertEquals(
                listOf("ana" to 2, "boris" to 1),
                ana.last<ServerMessage.Picks>().picks.map { it.player to it.option },
            )
            assertEquals(
                listOf("ana" to 2, "boris" to 1),
                boris.last<ServerMessage.Picks>().picks.map {
                    it.player to
                        it.option
                },
            )
            assertEquals(listOf("ana", "boris"), ceca.last<ServerMessage.Progress>().answered)
            assertTrue(ceca.all<ServerMessage.Picks>().isEmpty(), "ceca sees who, never what")
            lobby.assertInvariants()
        }

    @Test
    fun `the reveal names the right answer where this game put it`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            lobby.startGame(ana)
            lobby.openAnswers(0)
            val options = ana.optionsNow()
            ana.answer(0)

            val reveal = ana.last<ServerMessage.Revealed>().reveal
            assertEquals(options, reveal.options)
            assertEquals(
                lobby.questions
                    .first()
                    .options
                    .first(),
                reveal.options[reveal.correct],
                "the source's right answer",
            )
            assertEquals(lobby.questions.first().questionId, reveal.questionId, "what a report names")
        }

    @Test
    fun `a question of two or three answers plays like one of four`() =
        runTest {
            listOf(2, 3).forEach { count ->
                val lobby = LobbyScenario(this, questions = sampleQuestions(5, options = count))
                val ana = lobby.player("ana").join()
                lobby.startGame(ana)
                assertEquals(
                    count,
                    ana.last<ServerMessage.QuestionShown>().question.optionCount,
                    "tiles laid out before the answers",
                )
                lobby.openAnswers(0)
                assertEquals(count, ana.optionsNow().size)
                assertEquals(RejectCode.INVALID_OPTION, ana.answerTo(ana.answer(count)))

                ana.answerWrong()
                val points =
                    ana
                        .last<ServerMessage.Revealed>()
                        .reveal.results
                        .single()
                        .points
                assertEquals(if (count == 2) -120 else -60, points, "a blind hit is likelier, so the minus grows")
            }
        }

    @Test
    fun `a snapshot in the middle of a question keeps the clock and the player's pick`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            lobby.player("boris").join()
            lobby.startGame(ana)
            lobby.openAnswers(0)
            lobby.wait(3.seconds)
            ana.answer(1)
            ana.drop()
            lobby.wait(4.seconds)

            ana.attach()

            val phase = assertNotNull(ana.lastSnapshot()?.phase as? PhaseView.Answering)
            assertEquals(8_000L, phase.remainingMs, "the clock never restarts for a reconnect")
            assertEquals(1, phase.yourPick)
            assertEquals(listOf("ana"), phase.answered)
            assertEquals(1, phase.picks.single().option, "she answered, so she sees the picks")
            lobby.assertInvariants()
        }

    @Test
    fun `a player who dropped out does not hold the question up`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            val ceca = lobby.player("ceca").join()
            lobby.startGame(ana)
            lobby.openAnswers(0)
            ceca.drop()

            ana.answerRight()
            boris.answerRight()

            assertEquals(0, ana.last<ServerMessage.Revealed>().reveal.index)
            assertEquals(
                0,
                ana
                    .last<ServerMessage.Revealed>()
                    .reveal.results
                    .first { it.player == "ceca" }
                    .points,
            )
        }

    @Test
    fun `a player back in time can still answer a question they dropped out of`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            lobby.startGame(ana)
            lobby.openAnswers(0)
            boris.drop()
            lobby.wait(2.seconds)
            boris.attach()

            assertNull(boris.answerTo(boris.answerRight()))
        }

    @Test
    fun `a member who joins mid-game watches it and plays the next`() =
        runTest {
            val lobby = LobbyScenario(this, LobbySettingsDto(questionCount = 5))
            val ana = lobby.player("ana").join()
            lobby.startGame(ana)
            lobby.openAnswers(0)

            val dora = lobby.player("dora").join()
            val watching = assertNotNull(dora.lastSnapshot()?.phase as? PhaseView.Answering)
            assertEquals(listOf("ana"), watching.players)
            assertEquals(
                false,
                dora
                    .lastSnapshot()
                    ?.lobby
                    ?.members
                    ?.first { it.player == "dora" }
                    ?.playing,
            )

            ana.answer(0)
            assertEquals(listOf("ana"), dora.last<ServerMessage.Progress>().answered, "watchers never see picks")

            playOut(lobby, ana, from = 0, count = 5)
            assertEquals(
                listOf("ana"),
                ana
                    .last<ServerMessage.GameOver>()
                    .results.standings
                    .map { it.player },
            )
            val doraNow =
                dora
                    .lastSnapshotAfterResync(lobby)
                    .lobby.members
                    .first { it.player == "dora" }
            assertEquals(false, doraNow.onResults, "she watched, so she has no results to leave")

            ana.back()
            lobby.startGame(ana)
            assertEquals(listOf("ana", "dora"), ana.last<ServerMessage.GameStarted>().players)
            assertEquals(listOf("dora"), lobby.records.single().spectators)
            lobby.assertInvariants()
        }

    @Test
    fun `players go back to the lobby by hand, and a start waits a little for those still on the results`() =
        runTest {
            val lobby = LobbyScenario(this, LobbySettingsDto(questionCount = 3))
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            val ceca = lobby.player("ceca").join()
            lobby.startGame(ana)
            playOut(lobby, ana, from = 0, count = 3)
            val waiting = assertNotNull(ceca.lastSnapshotAfterResync(lobby).phase as? PhaseView.Waiting)
            assertNotNull(waiting.lastResults, "the results stay there for whoever still looks at them")
            assertTrue(checkNotNull(ceca.lastSnapshot()).lobby.members.all { it.onResults }, "nobody was moved")

            assertEquals(RejectCode.WRONG_PHASE, ana.answerTo(ana.start()), "the host starts from the lobby")
            ana.back()
            assertEquals(false, boris.last<ServerMessage.MemberUpdated>().member.onResults)

            ana.start()
            assertEquals(
                lobby.timings.countdownWithStragglers.inWholeMilliseconds,
                boris.last<ServerMessage.CountdownStarted>().remainingMs,
            )
            boris.back()
            lobby.wait(lobby.timings.countdownWithStragglers)

            assertEquals(
                listOf("ana", "boris"),
                ana.last<ServerMessage.GameStarted>().players,
                "ceca stayed on the results",
            )
            assertTrue(
                ceca
                    .lastSnapshotAfterResync(lobby)
                    .lobby.members
                    .first { it.player == "ceca" }
                    .onResults,
            )
            lobby.assertInvariants()
        }

    @Test
    fun `nothing a player receives before the reveal says which answer is right`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            lobby.player("ceca").join()
            lobby.startGame(ana)
            lobby.openAnswers(0)
            ana.answer(0)
            boris.drop()
            boris.attach()

            val beforeReveal = boris.history + ana.history
            assertTrue(beforeReveal.none { it is ServerMessage.Revealed }, "ceca has not answered")
            // Only a reveal names the answer; the snapshot of an open question carries picks, never the key.
            val answering = assertNotNull(boris.lastSnapshot()?.phase as? PhaseView.Answering)
            assertEquals(
                lobby.questions
                    .first()
                    .options
                    .toSet(),
                answering.options.toSet(),
            )
        }

    /** Plays questions [from] until [count] with [answerer] answering each right, through to the results. */
    private fun playOut(
        lobby: LobbyScenario,
        answerer: TestPlayer,
        from: Int,
        count: Int,
    ) {
        for (index in from until count) {
            if (answerer.all<ServerMessage.AnswersOpened>().none { it.index == index }) lobby.openAnswers(index)
            if (answerer.all<ServerMessage.Revealed>().none { it.reveal.index == index }) answerer.answerRight()
            if (answerer.all<ServerMessage.Revealed>().none { it.reveal.index == index }) {
                lobby.wait(15.seconds + lobby.timings.answerGrace)
            }
            lobby.wait(
                answerer
                    .last<ServerMessage.Revealed>()
                    .reveal.nextInMs.milliseconds,
            )
        }
    }

    private fun TestPlayer.lastSnapshotAfterResync(lobby: LobbyScenario): ServerMessage.Snapshot {
        say(io.ntole.kvizic.core.protocol.ClientMessage.Resync)
        lobby.settle()
        return checkNotNull(lastSnapshot())
    }
}
