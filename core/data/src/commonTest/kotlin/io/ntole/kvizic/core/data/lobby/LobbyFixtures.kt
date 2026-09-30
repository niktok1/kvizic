package io.ntole.kvizic.core.data.lobby

import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.protocol.AnswerResultView
import io.ntole.kvizic.core.protocol.FinalStandingView
import io.ntole.kvizic.core.protocol.LobbyView
import io.ntole.kvizic.core.protocol.MemberView
import io.ntole.kvizic.core.protocol.PhaseView
import io.ntole.kvizic.core.protocol.Protocol
import io.ntole.kvizic.core.protocol.QuestionView
import io.ntole.kvizic.core.protocol.ResultsView
import io.ntole.kvizic.core.protocol.RevealView
import io.ntole.kvizic.core.protocol.ServerMessage
import io.ntole.kvizic.core.protocol.StandingView
import io.ntole.kvizic.core.question.QuestionKind

// Protocol messages as a server would send them, for the reducer's and the session's tests.

internal const val CODE = "482915"

internal fun member(
    player: String,
    seat: Int,
    connected: Boolean = true,
) = MemberView(player = player, name = "Играч $player", avatar = "fox", seat = seat, connected = connected)

internal fun lobbyView(
    host: String = "guest1",
    vararg members: MemberView = arrayOf(member("guest1", 0)),
) = LobbyView(
    id = "lobby-$CODE",
    code = CODE,
    kind = LobbyKind.PRIVATE,
    settings = LobbySettingsDto(questionCount = 5),
    host = host,
    members = members.toList(),
)

internal fun welcome(you: String = "guest1") = ServerMessage.Welcome(Protocol.VERSION, you, pingEveryMs = 5_000)

internal fun snapshot(
    v: Long = 1,
    you: String = "guest1",
    lobby: LobbyView = lobbyView(),
    phase: PhaseView = PhaseView.Waiting(),
) = ServerMessage.Snapshot(v, you, lobby, phase)

internal fun question(index: Int = 0) =
    QuestionView(
        index = index,
        count = 5,
        text = "Питање $index?",
        topic = "GEOGRAPHY",
        optionCount = 4,
        kind = QuestionKind.CHOICE,
    )

internal val OPTIONS = listOf("Канбера", "Сиднеј", "Мелбурн", "Перт")

internal fun answering(
    index: Int = 0,
    remainingMs: Long = 15_000,
    yourPick: Int? = null,
    players: List<String> = listOf("guest1", "guest2"),
) = PhaseView.Answering(
    gameId = "game-1",
    players = players,
    question = question(index),
    options = OPTIONS,
    remainingMs = remainingMs,
    durationMs = 15_000,
    yourPick = yourPick,
)

internal fun reveal(index: Int = 0) =
    RevealView(
        index = index,
        count = 5,
        questionId = "q$index",
        text = "Питање $index?",
        options = OPTIONS,
        correct = 0,
        results = listOf(AnswerResultView("guest1", option = 0, points = 94, timeMs = 1_200, order = 1)),
        standings = listOf(StandingView("guest1", score = 94, rank = 1, correct = 1), StandingView("guest2", 0, 2)),
        nextInMs = 5_000,
    )

internal fun results() =
    ResultsView(
        gameId = "game-1",
        questionCount = 5,
        standings =
            listOf(
                FinalStandingView("guest1", "Играч guest1", "fox", score = 380, correct = 4, rank = 1),
                FinalStandingView("guest2", "Играч guest2", "fox", score = 120, correct = 2, rank = 2),
            ),
    )
