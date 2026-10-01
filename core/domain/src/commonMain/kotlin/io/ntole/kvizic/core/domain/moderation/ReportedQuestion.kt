package io.ntole.kvizic.core.domain.moderation

import io.ntole.kvizic.core.domain.report.QuestionReportReason

/**
 * A question players reported: [open] reports of its current revision, counted by reason most given first
 * ([reasons]; a reason this build cannot name counts under `null`), and when it was [lastReportedAt],
 * epoch milliseconds.
 */
public data class ReportedQuestion(
    public val question: ModeratedQuestion,
    public val open: Int,
    public val reasons: List<Pair<QuestionReportReason?, Int>>,
    public val lastReportedAt: Long,
)

/**
 * How the moderator dealt with a question's open reports. [FIXED] and [DISMISSED] put a question the
 * reports suspended back in play; [RETIRED] takes it out, approved or suspended.
 */
public enum class ReportOutcome {
    /** The question was edited, so the reports are answered. */
    FIXED,

    /** Nothing was wrong with it. */
    DISMISSED,

    /** It goes out of play. */
    RETIRED,
}

/** The bank and the server at a glance. */
public data class BankOverview(
    public val byStatus: Map<BankStatus, Int>,
    public val approvedByTopic: Map<String, Int>,
    public val liveLobbies: Int,
    public val liveGames: Int,
    public val connectedPlayers: Int,
    public val gamesToday: Int,
)
