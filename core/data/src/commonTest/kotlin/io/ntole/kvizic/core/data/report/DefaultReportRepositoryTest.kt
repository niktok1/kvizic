package io.ntole.kvizic.core.data.report

import io.ntole.kvizic.core.data.BASE_URL
import io.ntole.kvizic.core.data.FakeServer
import io.ntole.kvizic.core.data.session
import io.ntole.kvizic.core.data.session.DefaultSessionRepository
import io.ntole.kvizic.core.data.storeHolding
import io.ntole.kvizic.core.domain.error.GameError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.report.QuestionReportReason
import io.ntole.kvizic.core.domain.report.ReportQuestion
import io.ntole.kvizic.core.network.KvizicHttpClient
import io.ntole.kvizic.core.network.api.ReportApi
import io.ntole.kvizic.core.report.ReportQuestionRequest
import io.ntole.kvizic.core.report.ReportReason
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** A question reported through the real client, against [FakeServer]. */
class DefaultReportRepositoryTest {
    private val server = FakeServer()

    @Test
    fun `a report names the question and the reason with the player's session`() =
        runTest {
            val report = reportQuestion(storeHolding(null))

            report("q1", QuestionReportReason.WRONG_ANSWER)

            assertEquals(
                listOf<Pair<String?, ReportQuestionRequest>>(
                    "Bearer access-guest1" to ReportQuestionRequest("q1", ReportReason.WRONG_ANSWER),
                ),
                server.reportsSentAs,
            )
        }

    /** The server keeps the content language a report was read in: Serbian Latin reads the Serbian Cyrillic. */
    @Test
    fun `a report names the content language the player reads`() =
        runTest {
            val reads =
                mapOf("hr" to "hr", "sr-Latn" to "sr-Cyrl", "sr-Cyrl" to "sr-Cyrl", "mk" to "mk", "en" to "sr-Cyrl")
            reads.forEach { (shown, read) ->
                reportQuestion(storeHolding(null), languageTag = { shown })("q1", QuestionReportReason.TYPO)
                assertEquals<String?>(read, sentLast().language, shown)
            }
            reportQuestion(storeHolding(null))("q1", QuestionReportReason.TYPO)
            assertNull(sentLast().language, "a client that knows none names none")
        }

    @Test
    fun `every reason goes as the wire's own`() =
        runTest {
            val report = reportQuestion(storeHolding(null))

            QuestionReportReason.entries.forEach { report("q1", it) }

            assertEquals(
                listOf(
                    ReportReason.WRONG_ANSWER,
                    ReportReason.TYPO,
                    ReportReason.AMBIGUOUS,
                    ReportReason.OFFENSIVE,
                    ReportReason.OTHER,
                ),
                server.reportsSentAs.map { it.second.reason },
            )
        }

    @Test
    fun `a dead session is replaced once and the report sent as the fresh guest`() =
        runTest {
            val report = reportQuestion(storeHolding(session("dead")))

            report("q1", QuestionReportReason.TYPO)

            assertEquals(listOf("Bearer access-dead", "Bearer access-guest1"), server.reportsSentAs.map { it.first })
        }

    @Test
    fun `a question the server has no more is QUESTION_NOT_FOUND`() =
        runTest {
            val report = reportQuestion(storeHolding(null))

            val refused = assertFailsWith<KvizicException> { report("gone", QuestionReportReason.OTHER) }

            assertEquals(GameError.QUESTION_NOT_FOUND, refused.error)
        }

    private fun sentLast(): ReportQuestionRequest = server.reportsSentAs.last().second

    private fun reportQuestion(
        store: io.ntole.kvizic.core.network.SessionStore,
        languageTag: () -> String? = { null },
    ): ReportQuestion {
        val client = KvizicHttpClient.create(BASE_URL, store, server.engine)
        val sessions =
            DefaultSessionRepository(
                io.ntole.kvizic.core.network.api
                    .AuthApi(client),
                store,
            )
        return ReportQuestion(DefaultReportRepository(ReportApi(client), sessions, languageTag), sessions)
    }
}
