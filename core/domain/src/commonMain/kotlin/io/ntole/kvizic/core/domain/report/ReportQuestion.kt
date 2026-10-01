package io.ntole.kvizic.core.domain.report

import io.ntole.kvizic.core.domain.session.SessionRepository

/** Why a player reports a question they were asked. */
public enum class QuestionReportReason {
    /** The answer marked right is not. */
    WRONG_ANSWER,
    TYPO,

    /** More than one answer could be right, or none clearly is. */
    AMBIGUOUS,
    OFFENSIVE,
    OTHER,
}

/** Reports questions to the moderator. Implemented in `:core:data`. */
public interface ReportRepository {
    /**
     * Reports question [questionId], as a reveal names it, for [reason]. One report a player and revision
     * of the question; a second is taken as the first.
     *
     * @throws io.ntole.kvizic.core.domain.error.KvizicException naming why it could not.
     */
    public suspend fun report(
        questionId: String,
        reason: QuestionReportReason,
    )
}

/** Reports a question, the player's session ensured first. */
public class ReportQuestion(
    private val reports: ReportRepository,
    private val session: SessionRepository,
) {
    /** @throws io.ntole.kvizic.core.domain.error.KvizicException as [ReportRepository.report] does. */
    public suspend operator fun invoke(
        questionId: String,
        reason: QuestionReportReason,
    ) {
        session.ensure()
        reports.report(questionId, reason)
    }
}
