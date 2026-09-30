package io.ntole.kvizic.server.report

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.auth.SessionDto
import io.ntole.kvizic.core.question.AdminQuestionPageDto
import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.ImportQuestionsRequest
import io.ntole.kvizic.core.question.QuestionDecisionRequest
import io.ntole.kvizic.core.question.QuestionDraftDto
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.core.report.AdminReportListDto
import io.ntole.kvizic.core.report.ReportQuestionRequest
import io.ntole.kvizic.core.report.ReportReason
import io.ntole.kvizic.core.report.ReportResolution
import io.ntole.kvizic.core.report.ResolveReportsRequest
import io.ntole.kvizic.server.FLOW_ADMIN_TOKEN
import io.ntole.kvizic.server.mintGuest
import io.ntole.kvizic.server.runTestServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Players reporting questions, the suspension three wrong-answer reports bring, and the moderator's answer. */
class ReportFlowTest {
    private suspend fun HttpClient.admin(
        path: String,
        body: Any,
    ): HttpResponse =
        post(path) {
            header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpClient.approvedQuestion(): String {
        val draft =
            QuestionDraftDto(
                key = "q",
                text = "Колико ногу има паук?",
                options = listOf("Осам", "Шест", "Десет", "Четири"),
                correct = 0,
                topics = listOf("SCIENCE"),
                difficulty = Difficulty.EASY,
            )
        admin(KvizicApi.Paths.ADMIN_QUESTION_IMPORTS, ImportQuestionsRequest("b", listOf(draft)))
        val id =
            get(KvizicApi.Paths.ADMIN_QUESTIONS) { header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN) }
                .body<AdminQuestionPageDto>()
                .questions
                .single()
                .id
        admin(KvizicApi.Paths.ADMIN_QUESTION_APPROVALS, QuestionDecisionRequest(id))
        return id
    }

    private suspend fun HttpClient.report(
        who: SessionDto,
        questionId: String,
        reason: ReportReason,
    ): HttpResponse =
        post(KvizicApi.Paths.REPORTS) {
            bearerAuth(who.accessToken)
            contentType(ContentType.Application.Json)
            setBody(ReportQuestionRequest(questionId, reason))
        }

    private suspend fun HttpClient.status(id: String): QuestionStatus =
        get(KvizicApi.Paths.ADMIN_QUESTIONS) { header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN) }
            .body<AdminQuestionPageDto>()
            .questions
            .single { it.id == id }
            .status

    @Test
    fun `three players reporting a wrong answer take the question out of play, and a dismissal puts it back`() =
        runTestServer("reports-suspend") { client, _ ->
            val id = client.approvedQuestion()
            val (ana, boris, ceca) = List(3) { client.mintGuest() }

            assertEquals(HttpStatusCode.NoContent, client.report(ana, id, ReportReason.WRONG_ANSWER).status)
            assertEquals(
                HttpStatusCode.NoContent,
                client.report(ana, id, ReportReason.WRONG_ANSWER).status,
                "again is nothing new",
            )
            client.report(boris, id, ReportReason.WRONG_ANSWER)
            assertEquals(QuestionStatus.APPROVED, client.status(id), "two players are not enough")
            client.report(ceca, id, ReportReason.WRONG_ANSWER)
            assertEquals(QuestionStatus.SUSPENDED, client.status(id))

            val open =
                client
                    .get(KvizicApi.Paths.ADMIN_REPORTS) {
                        header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN)
                    }.body<AdminReportListDto>()
            val report = open.reports.single()
            assertEquals(3, report.open)
            assertEquals(3, report.question.openReports)
            assertEquals(listOf(ReportReason.WRONG_ANSWER to 3), report.reasons.map { it.reason to it.count })

            val dismissed =
                client.admin(
                    KvizicApi.Paths.ADMIN_REPORT_RESOLUTIONS,
                    ResolveReportsRequest(id, ReportResolution.DISMISSED),
                )
            assertEquals(HttpStatusCode.NoContent, dismissed.status)
            assertEquals(QuestionStatus.APPROVED, client.status(id))
            val after =
                client
                    .get(KvizicApi.Paths.ADMIN_REPORTS) {
                        header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN)
                    }.body<AdminReportListDto>()
            assertTrue(after.reports.isEmpty())
        }

    @Test
    fun `other reasons never suspend, and a retirement takes the question out for good`() =
        runTestServer("reports-retire") { client, _ ->
            val id = client.approvedQuestion()
            repeat(4) { client.report(client.mintGuest(), id, ReportReason.TYPO) }
            assertEquals(QuestionStatus.APPROVED, client.status(id))

            client.admin(KvizicApi.Paths.ADMIN_REPORT_RESOLUTIONS, ResolveReportsRequest(id, ReportResolution.RETIRED))
            assertEquals(QuestionStatus.RETIRED, client.status(id))
        }

    @Test
    fun `a report names a question players are asked, with a reason`() =
        runTestServer("reports-refused") { client, _ ->
            val ana = client.mintGuest()
            assertEquals(HttpStatusCode.NotFound, client.report(ana, "no-such-question", ReportReason.TYPO).status)
            val id = client.approvedQuestion()
            assertEquals(HttpStatusCode.BadRequest, client.report(ana, id, ReportReason.UNKNOWN).status)
            val anonymous =
                client.post(KvizicApi.Paths.REPORTS) {
                    contentType(ContentType.Application.Json)
                    setBody(ReportQuestionRequest(id, ReportReason.TYPO))
                }
            assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        }
}
