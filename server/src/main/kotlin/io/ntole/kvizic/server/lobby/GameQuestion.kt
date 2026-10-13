package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.QuestionKind
import io.ntole.kvizic.core.region.ContentLanguages
import io.ntole.kvizic.core.region.Country

/**
 * A question as a game asks it, loaded before the game starts so nothing reads the database while it
 * runs. [options] are in the order they are stored in; the game shuffles them. [difficulty] is the level
 * it plays at (`MeasuredDifficulty`). [text], [options] and [explanation] are in [language], the source's;
 * [texts] holds its translations by content tag, each with its answers in [options]' order, which a reader
 * gets through [textIn].
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
    val language: String = ContentLanguages.SERBIAN,
    val texts: Map<String, QuestionText> = emptyMap(),
) {
    init {
        require(options.size >= 2) { "a question has 2 answers at least" }
        require(correct in options.indices) { "the right answer is one of the answers" }
    }
}

/** A question's words in one language: its text, its answers in the source's order, and its explanation. */
data class QuestionText(
    val text: String,
    val options: List<String>,
    val explanation: String?,
)

/**
 * The question as a reader of [lang] reads it: the first of the source and its translations that
 * `ContentLanguages.chain` finds, else the source. A translation with another number of answers than the
 * source's is passed over, since its answers could not stand for the same slots: an edit of the answers
 * leaves one behind until it is sent again.
 */
fun GameQuestion.textIn(lang: String): QuestionText {
    val source = QuestionText(text, options, explanation)
    return ContentLanguages
        .chain(lang)
        .firstNotNullOfOrNull { tag ->
            if (tag == language) source else texts[tag]?.takeIf { it.options.size == options.size }
        } ?: source
}

/**
 * What a game asks for: [count] questions in [topics], none for every topic, best unseen by [players],
 * most at [difficulty] and the rest beside it. [countries] are those the players play from, each once,
 * and [languages] the content languages they read: a question scoped to countries is asked only when all
 * of [countries] are among its own, so a room of mixed countries gets the common questions alone.
 */
data class PickRequest(
    val count: Int,
    val topics: List<String>,
    val players: Set<String>,
    /** The player of a solo run, whose personal best the result carries, or null. */
    val soloPlayer: String? = null,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val countries: Set<Country> = setOf(Country.RS),
    val languages: Set<String> = setOf(ContentLanguages.SERBIAN),
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
