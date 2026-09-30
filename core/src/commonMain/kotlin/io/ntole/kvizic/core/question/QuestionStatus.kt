package io.ntole.kvizic.core.question

import kotlinx.serialization.Serializable

/** Where a question of the bank stands. Only [APPROVED] questions are asked. */
@Serializable
public enum class QuestionStatus {
    /** Imported or written, waiting for the moderator. */
    DRAFT,
    APPROVED,
    REJECTED,

    /** Taken out of play by the moderator. */
    RETIRED,

    /** Taken out of play by itself: enough players reported its answer wrong. */
    SUSPENDED,
    UNKNOWN,
}

/** How hard a question is: the author's guess, until enough answers measure it. */
@Serializable
public enum class Difficulty {
    EASY,
    MEDIUM,
    HARD,
    UNKNOWN,
}
