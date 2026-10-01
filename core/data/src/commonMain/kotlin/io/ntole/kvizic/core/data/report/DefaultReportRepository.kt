package io.ntole.kvizic.core.data.report

import io.ntole.kvizic.core.data.session.DefaultSessionRepository
import io.ntole.kvizic.core.data.session.withSessionRecovery
import io.ntole.kvizic.core.domain.report.QuestionReportReason
import io.ntole.kvizic.core.domain.report.ReportRepository
import io.ntole.kvizic.core.network.api.ReportApi
import io.ntole.kvizic.core.report.ReportQuestionRequest
import io.ntole.kvizic.core.report.ReportReason

/**
 * Reports a question, recovering once from a session the server stopped accepting: a report sent again is
 * taken as the first, so it is safe to resend.
 */
public class DefaultReportRepository(
    private val api: ReportApi,
    private val session: DefaultSessionRepository,
) : ReportRepository {
    override suspend fun report(
        questionId: String,
        reason: QuestionReportReason,
    ) {
        session.withSessionRecovery { api.report(ReportQuestionRequest(questionId, reason.toWire())) }
    }
}

internal fun QuestionReportReason.toWire(): ReportReason =
    when (this) {
        QuestionReportReason.WRONG_ANSWER -> ReportReason.WRONG_ANSWER
        QuestionReportReason.TYPO -> ReportReason.TYPO
        QuestionReportReason.AMBIGUOUS -> ReportReason.AMBIGUOUS
        QuestionReportReason.OFFENSIVE -> ReportReason.OFFENSIVE
        QuestionReportReason.OTHER -> ReportReason.OTHER
    }
