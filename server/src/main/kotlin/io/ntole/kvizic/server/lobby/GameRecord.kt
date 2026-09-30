package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.lobby.LobbySettingsDto

/**
 * A finished game, as the database keeps it: who played, how each did, what each question was and who
 * got it right. Written once, after the players have seen their results (`ResultWriter`).
 */
data class GameRecord(
    val gameId: String,
    val kind: LobbyKind,
    val settings: LobbySettingsDto,
    val startedAt: Long,
    val endedAt: Long,
    val endedEarly: Boolean,
    val players: List<PlayerRecord>,
    val questions: List<QuestionRecord>,
    /** Members who watched without playing: they saw every question too. */
    val spectators: List<String>,
)

data class PlayerRecord(
    val playerId: String,
    val score: Int,
    val correct: Int,
    val answered: Int,
    val rank: Int,
    val finished: Boolean,
    val won: Boolean,
)

/** One asked question, and each player's answer to it, by player id: none for a player who gave none. */
data class QuestionRecord(
    val questionId: String,
    val topicId: String,
    val answers: Map<String, AnswerRecord>,
)

data class AnswerRecord(
    val correct: Boolean,
    val timeMs: Long,
)

/** Where finished games go: never waited for by a lobby, whose players have their results already. */
fun interface ResultSink {
    fun submit(record: GameRecord)
}
