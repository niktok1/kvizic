package io.ntole.kvizic.room

import io.ntole.kvizic.core.domain.lobby.AnswerResult
import io.ntole.kvizic.core.domain.lobby.AskedQuestion
import io.ntole.kvizic.core.domain.lobby.Deadline
import io.ntole.kvizic.core.domain.lobby.FinalStanding
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.GameResults
import io.ntole.kvizic.core.domain.lobby.Lobby
import io.ntole.kvizic.core.domain.lobby.LobbyKind
import io.ntole.kvizic.core.domain.lobby.LobbyMember
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.domain.lobby.QuestionKind
import io.ntole.kvizic.core.domain.lobby.Reveal
import io.ntole.kvizic.core.domain.lobby.Standing
import io.ntole.kvizic.core.domain.topic.Topic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

// A room of five, Нина hosting, the player Марко, and its game at each step: the screens' tests' rooms.

internal const val YOU = "marko"

internal fun member(
    id: String,
    name: String,
    avatar: String,
    seat: Int,
    connected: Boolean = true,
    onResults: Boolean = false,
    playing: Boolean = false,
) = LobbyMember(id, name, avatar, seat, connected, onResults, playing)

internal val MEMBERS =
    listOf(
        member("nina", "Нина", "fox", 0),
        member("sova", "Мудра Сова", "owl", 1),
        member(YOU, "Марко", "hedgehog", 2),
        member("bojan", "Бојан", "bear", 3),
        member("roda", "Тиха Рода", "stork", 4, connected = false),
    )

internal val TOPICS =
    listOf(
        Topic("GEOGRAPHY", "Географија", "Geography", 40, groupId = "KNOWLEDGE"),
        Topic("HISTORY", "Историја", "History", 30, groupId = "KNOWLEDGE"),
        // In no group the picker knows: listed under the rest.
        Topic("FOOD", "Храна и пиће", "Food & drink", 4),
    )

internal fun lobby(
    members: List<LobbyMember> = MEMBERS,
    host: String = "nina",
    kind: LobbyKind = LobbyKind.PRIVATE,
    settings: LobbySettings = LobbySettings(),
) = Lobby("lobby-1", "482915", kind, settings, host, members)

internal fun deadline(
    left: Duration,
    total: Duration = left,
) = Deadline(TimeSource.Monotonic.markNow() + left, total)

internal fun inLobby(
    phase: GamePhase,
    lobby: Lobby = lobby(),
    reconnecting: Boolean = false,
) = LobbySessionState.InLobby(YOU, lobby, phase, reconnecting)

internal val PLAYERS = MEMBERS.map { it.playerId }

internal val STANDINGS =
    listOf(
        Standing("nina", 1328, 1, 3),
        Standing(YOU, 1210, 2, 3),
        Standing("bojan", 1045, 3, 2),
        Standing("sova", 980, 4, 2),
        Standing("roda", 655, 5, 1),
    )

internal val QUESTION =
    AskedQuestion(2, 10, "Која река протиче кроз Нови Сад?", "GEOGRAPHY", 4, QuestionKind.CHOICE, 15.seconds)

internal val OPTIONS = listOf("Дунав", "Сава", "Тиса", "Морава")

internal fun reading() = GamePhase.Reading("game-1", PLAYERS, QUESTION, deadline(2.seconds, 2.4.seconds), STANDINGS)

internal fun answering(
    answered: Set<String> = setOf("sova", "bojan"),
    myPick: Int? = null,
    picks: Map<String, Int> = emptyMap(),
    players: List<String> = PLAYERS,
) = GamePhase.Answering(
    "game-1",
    players,
    QUESTION,
    OPTIONS,
    deadline(11.seconds, 15.seconds),
    answered,
    myPick,
    picks,
    STANDINGS,
)

internal fun revealing(last: Boolean = false) =
    GamePhase.Revealing(
        "game-1",
        PLAYERS,
        Reveal(
            index = 2,
            count = 10,
            questionId = "q1",
            text = QUESTION.text,
            options = OPTIONS,
            correct = 0,
            results =
                listOf(
                    AnswerResult("nina", 0, 88, 2_100, 1),
                    AnswerResult(YOU, 1, -25, 1_800, null),
                    AnswerResult("bojan", 0, 76, 4_000, 2),
                    AnswerResult("sova", 2, -12, 6_000, null),
                    AnswerResult("roda", null, 0, null, null),
                ),
            standings = STANDINGS,
            explanation = null,
            last = last,
        ),
        deadline(4.seconds, 5.seconds),
    )

internal val RESULTS =
    GameResults(
        gameId = "game-1",
        questionCount = 10,
        standings =
            listOf(
                FinalStanding("nina", "Нина", "fox", 1612, 8, 1, true),
                FinalStanding(YOU, "Марко", "hedgehog", 1488, 7, 2, true),
                FinalStanding("bojan", "Бојан", "bear", 1210, 6, 3, true),
                FinalStanding("sova", "Мудра Сова", "owl", 980, 5, 4, true),
                FinalStanding("roda", "Тиха Рода", "stork", 655, 3, 5, false),
            ),
        endedEarly = false,
        personalBest = null,
    )
