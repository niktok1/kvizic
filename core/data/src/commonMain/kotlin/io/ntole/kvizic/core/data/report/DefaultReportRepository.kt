package io.ntole.kvizic.core.data.report

import io.ntole.kvizic.core.data.session.DefaultSessionRepository
import io.ntole.kvizic.core.data.session.withSessionRecovery
import io.ntole.kvizic.core.domain.report.QuestionReportReason
import io.ntole.kvizic.core.domain.report.ReportRepository
import io.ntole.kvizic.core.network.api.ReportApi
import io.ntole.kvizic.core.region.ContentLanguages
import io.ntole.kvizic.core.report.ReportQuestionRequest
import io.ntole.kvizic.core.report.ReportReason

/**
 * Reports a question, recovering once from a session the server stopped accepting: a report sent again is
 * taken as the first, so it is safe to resend.
 *
 * A report names the content language the reporter reads, which the server keeps beside it: the one the game's
 * language ([languageTag], a UI tag as `PlayerRegion` holds it) reads ([ContentLanguages.of]), so a typo reported
 * in Croatian is looked for in the Croatian text. The language in the room's hello is the same, since the
 * Settings screen that changes it is reached from Home alone. None, from a client that knows none, names none.
 */
public class DefaultReportRepository(
    private val api: ReportApi,
    private val session: DefaultSessionRepository,
    private val languageTag: () -> String? = { null },
) : ReportRepository {
    override suspend fun report(
        questionId: String,
        reason: QuestionReportReason,
    ) {
        val language = languageTag()?.let(ContentLanguages::of)
        session.withSessionRecovery { api.report(ReportQuestionRequest(questionId, reason.toWire(), language)) }
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
