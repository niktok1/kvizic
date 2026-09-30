package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.QuestionKind

/**
 * A question as a game asks it, loaded before the game starts so nothing reads the database while it
 * runs. [options] are in the order they are stored in; the game shuffles them.
 */
data class GameQuestion(
    val questionId: String,
    val text: String,
    val options: List<String>,
    val correct: Int,
    val topicId: String,
    val kind: QuestionKind = QuestionKind.CHOICE,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val explanation: String? = null,
) {
    init {
        require(options.size >= 2) { "a question has 2 answers at least" }
        require(correct in options.indices) { "the right answer is one of the answers" }
    }
}

/** What a game asks for: [count] questions in [topics], none for every topic, best unseen by [players]. */
data class PickRequest(
    val count: Int,
    val topics: List<String>,
    val players: Set<String>,
    /** The player of a solo run, whose personal best the result carries, or null. */
    val soloPlayer: String? = null,
)

/**
 * The questions picked, and whether the picker had to reach past the topics asked for ([toppedUp]) or
 * could not fill the game ([shortened]). [soloBestBefore] is the solo player's personal best until now.
 */
data class PickResult(
    val questions: List<GameQuestion>,
    val toppedUp: Boolean = false,
    val shortened: Boolean = false,
    val soloBestBefore: Int? = null,
)

/** Picks a game's questions: the bank in production, a fixed list in tests. Called off the lobby's loop. */
fun interface QuestionSource {
    suspend fun pick(request: PickRequest): PickResult
}
