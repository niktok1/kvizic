package io.ntole.kvizic.core.domain.moderation

/** Where a question of the bank stands. Only [APPROVED] questions are asked. */
public enum class BankStatus {
    /** Imported or written, waiting for the moderator. */
    DRAFT,
    APPROVED,
    REJECTED,

    /** Taken out of play by the moderator. */
    RETIRED,

    /** Taken out of play by itself: enough players reported its answer wrong. */
    SUSPENDED,

    /** A status this build cannot name: shown, never filtered by or sent. */
    OTHER,
}

/** How hard a question is: its author's guess, until enough answers measure it. */
public enum class QuestionDifficulty {
    EASY,
    MEDIUM,
    HARD,

    /** One this build cannot name. */
    OTHER,
}

/**
 * How a question has played: how often asked, answered and answered right, and how fast when right; how
 * many it waited for gave no answer, and the level all that makes it play at, its author's at first.
 */
public data class QuestionPlay(
    public val shown: Int = 0,
    public val answered: Int = 0,
    public val correct: Int = 0,
    public val averageCorrectMillis: Long? = null,
    public val unanswered: Int = 0,
    public val playsAs: QuestionDifficulty = QuestionDifficulty.OTHER,
) {
    /** The share of answers that were right, 0 to 100, or `null` before any answer. */
    public val correctPercent: Int?
        get() = if (answered == 0) null else correct * PERCENT / answered

    private companion object {
        const val PERCENT = 100
    }
}

/**
 * A question as the moderator sees it: everything, the right answer and the source included.
 *
 * [revision] is what an edit is made from ([QuestionEdit]); the times are epoch milliseconds.
 */
public data class ModeratedQuestion(
    public val id: String,
    public val status: BankStatus,
    public val text: String,
    public val options: List<String>,
    public val correct: Int,
    public val topics: List<String>,
    public val difficulty: QuestionDifficulty,
    public val explanation: String?,
    public val source: String?,
    public val author: String,
    public val importBatch: String?,
    public val revision: Int,
    public val createdAt: Long,
    public val updatedAt: Long,
    public val reviewedAt: Long?,
    public val rejectionReason: String?,
    public val play: QuestionPlay,
    public val openReports: Int,
)

/** A page of the bank, and the cursor of the next, `null` on the last. */
public data class QuestionPage(
    public val questions: List<ModeratedQuestion>,
    public val next: String?,
)

/**
 * Which questions of the bank to list: those at any of [statuses] and filed under any of [topics], none of
 * either being all, and matching [search] in their text or answers when it is not blank.
 */
public data class QuestionFilter(
    public val statuses: Set<BankStatus> = emptySet(),
    public val topics: Set<String> = emptySet(),
    public val search: String = "",
)
