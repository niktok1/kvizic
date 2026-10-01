package io.ntole.kvizic.core.data.lobby

import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.LobbyDifficulty
import io.ntole.kvizic.core.domain.lobby.LobbyKind
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.lobby.Visibility
import io.ntole.kvizic.core.protocol.PhaseView
import io.ntole.kvizic.core.protocol.PickView
import io.ntole.kvizic.core.protocol.ServerMessage
import io.ntole.kvizic.core.question.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** The lobby's state from its snapshot and the changes after it, one version at a time. */
class LobbyReducerTest {
    private val clock = TestTimeSource()

    private fun LobbyModel.then(change: ServerMessage.StateChange): LobbyModel =
        assertIs<Step.Changed>(LobbyReducer.apply(this, change, clock.markNow())).model

    private fun start(phase: PhaseView = PhaseView.Waiting()) =
        LobbyReducer.snapshot(snapshot(v = 10, phase = phase), clock.markNow())

    @Test
    fun `a snapshot is the lobby as it stands and its members in seat order`() {
        val model =
            LobbyReducer.snapshot(
                snapshot(v = 7, lobby = lobbyView("guest1", member("guest2", 3), member("guest1", 0))),
                clock.markNow(),
            )
        assertEquals(7, model.version)
        assertEquals(listOf("guest1", "guest2"), model.lobby.members.map { it.playerId })
        assertEquals("guest1", model.lobby.host)
        assertEquals(GamePhase.Waiting(null), model.phase)
    }

    @Test
    fun `members come and change and go`() {
        val model =
            start()
                .then(ServerMessage.MemberJoined(11, member("guest2", 1)))
                .then(ServerMessage.MemberUpdated(12, member("guest2", 1, connected = false)))
                .then(ServerMessage.HostChanged(13, "guest2"))
        assertEquals(false, model.lobby.member("guest2")?.connected)
        assertEquals("guest2", model.lobby.host)
        val after = model.then(ServerMessage.MemberLeft(14, "guest2"))
        assertNull(after.lobby.member("guest2"))
        assertEquals(14, after.version)
    }

    @Test
    fun `a version skipped asks for a snapshot and one seen already changes nothing`() {
        val model = start()
        assertEquals(Step.OutOfOrder, LobbyReducer.apply(model, ServerMessage.HostChanged(12, "x"), clock.markNow()))
        assertEquals(Step.Stale, LobbyReducer.apply(model, ServerMessage.HostChanged(10, "x"), clock.markNow()))
    }

    @Test
    fun `a change that needs a phase not seen begin asks for a snapshot`() {
        val model = start()
        val opened =
            ServerMessage.AnswersOpened(
                11,
                index = 0,
                options = OPTIONS,
                remainingMs = 15_000,
                durationMs = 15_000,
            )
        assertEquals(Step.OutOfOrder, LobbyReducer.apply(model, opened, clock.markNow()), "no question was shown")
    }

    @Test
    fun `a game goes from the countdown through a question to its reveal and results`() {
        var model = start().then(ServerMessage.MemberJoined(11, member("guest2", 1)))
        model = model.then(ServerMessage.CountdownStarted(12, remainingMs = 3_000))
        val countdown = assertIs<GamePhase.Countdown>(model.phase)
        clock += 1.seconds
        assertEquals(2.seconds, countdown.deadline.remaining(), "anchored when it came")

        model = model.then(ServerMessage.GameStarted(13, "game-1", listOf("guest1", "guest2"), questionCount = 5))
        assertTrue(model.lobby.members.all { it.playing })
        model = model.then(ServerMessage.QuestionShown(14, question(0), readMs = 2_000))
        assertEquals(0, assertIs<GamePhase.Reading>(model.phase).question.index)

        model =
            model.then(
                ServerMessage.AnswersOpened(
                    15,
                    index = 0,
                    options = OPTIONS,
                    remainingMs = 15_000,
                    durationMs = 15_000,
                ),
            )
        val answering = assertIs<GamePhase.Answering>(model.phase)
        assertEquals(OPTIONS, answering.options)
        assertEquals(15.seconds, answering.deadline.total)

        model = model.then(ServerMessage.Progress(16, index = 0, answered = listOf("guest2")))
        assertEquals(setOf("guest2"), assertIs<GamePhase.Answering>(model.phase).answered)
        model =
            model.then(ServerMessage.Picks(17, index = 0, picks = listOf(PickView("guest1", 0), PickView("guest2", 2))))
        val picked = assertIs<GamePhase.Answering>(model.phase)
        assertEquals(0, picked.myPick)
        assertEquals(mapOf("guest1" to 0, "guest2" to 2), picked.picks)

        model = model.then(ServerMessage.Revealed(18, reveal(0)))
        val revealing = assertIs<GamePhase.Revealing>(model.phase)
        assertEquals(
            94,
            revealing.reveal.standings
                .first()
                .score,
        )
        assertEquals(5.seconds, revealing.next.remaining())

        model = model.then(ServerMessage.QuestionShown(19, question(1), readMs = 2_000))
        assertEquals(94, assertIs<GamePhase.Reading>(model.phase).standings.first().score, "the standings carry over")

        model = model.then(ServerMessage.GameOver(20, results()))
        val over = assertIs<GamePhase.Waiting>(model.phase)
        assertEquals(
            "guest1",
            over.lastResults
                ?.standings
                ?.first()
                ?.playerId,
        )
        assertTrue(model.lobby.members.all { it.onResults && !it.playing }, "everyone looks at the results")
        assertNull(model.game)
    }

    @Test
    fun `an aborted start goes back to waiting with the last results kept`() {
        val model =
            start(PhaseView.Waiting(results()))
                .then(ServerMessage.CountdownStarted(11, remainingMs = 3_000))
                .then(ServerMessage.GameAborted(12))
        assertEquals("game-1", assertIs<GamePhase.Waiting>(model.phase).lastResults?.gameId)
    }

    @Test
    fun `a snapshot mid-question has the answers and the picks and the clock`() {
        val model = start(answering(index = 2, remainingMs = 4_000, yourPick = 1))
        val phase = assertIs<GamePhase.Answering>(model.phase)
        assertEquals(2, phase.question.index)
        assertEquals(1, phase.myPick)
        clock += 1_500.milliseconds
        assertEquals(2_500.milliseconds, phase.deadline.remaining())
        assertEquals("game-1", model.game?.gameId)
    }

    @Test
    fun `a question keeps its game's time to answer through a change of the settings`() {
        val model =
            start()
                .then(ServerMessage.CountdownStarted(11, remainingMs = 3_000))
                .then(ServerMessage.GameStarted(12, "game-1", listOf("guest1"), questionCount = 5))
                .then(ServerMessage.SettingsChanged(13, LobbySettingsDto(secondsPerQuestion = 30)))
                .then(ServerMessage.QuestionShown(14, question(0).copy(answerMs = 20_000), readMs = 2_000))
        assertEquals(20.seconds, assertIs<GamePhase.Reading>(model.phase).question.answerTime)
        assertEquals(30, model.lobby.settings.secondsPerQuestion, "the next game's")

        val joined = start(PhaseView.Reading("game-1", listOf("guest1"), question(1).copy(answerMs = 20_000), 1_000))
        assertEquals(20.seconds, assertIs<GamePhase.Reading>(joined.phase).question.answerTime, "from a snapshot")
    }

    @Test
    fun `a question from a server that does not say its time to answer takes the room's`() {
        val model = start(PhaseView.Reading("game-1", listOf("guest1"), question(0), remainingMs = 1_000))
        assertEquals(
            LobbySettingsDto().secondsPerQuestion.seconds,
            assertIs<GamePhase.Reading>(model.phase).question.answerTime,
        )
    }

    @Test
    fun `settings made public list the lobby and a solo run stays one`() {
        val public = start().then(ServerMessage.SettingsChanged(11, LobbySettingsDto(visibility = Visibility.PUBLIC)))
        assertEquals(LobbyKind.PUBLIC, public.lobby.kind)
        val solo =
            start()
                .let {
                    it.copy(lobby = it.lobby.copy(kind = LobbyKind.SOLO))
                }.then(ServerMessage.SettingsChanged(11, LobbySettingsDto(visibility = Visibility.PUBLIC)))
        assertEquals(LobbyKind.SOLO, solo.lobby.kind)
    }

    @Test
    fun `a room's difficulty comes through and one this build cannot name is medium`() {
        val hard = start().then(ServerMessage.SettingsChanged(11, LobbySettingsDto(difficulty = Difficulty.HARD)))
        assertEquals(LobbyDifficulty.HARD, hard.lobby.settings.difficulty)
        val unknown = start().then(ServerMessage.SettingsChanged(11, LobbySettingsDto(difficulty = Difficulty.UNKNOWN)))
        assertEquals(LobbyDifficulty.MEDIUM, unknown.lobby.settings.difficulty)
    }
}
