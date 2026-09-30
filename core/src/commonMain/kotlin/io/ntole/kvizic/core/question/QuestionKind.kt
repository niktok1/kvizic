package io.ntole.kvizic.core.question

import kotlinx.serialization.Serializable

/**
 * What kind of question it is, so a client can dress it: every kind is answered by picking one of its
 * 2 to 4 answers, and none is hard-coded to 4.
 */
@Serializable
public enum class QuestionKind {
    /** Pick the right one of the answers. */
    CHOICE,

    /** Two answers, true and false. */
    TRUE_FALSE,

    UNKNOWN,
}
