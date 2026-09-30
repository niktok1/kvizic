package io.ntole.kvizic.server.question

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.error.ErrorDto
import io.ntole.kvizic.core.question.AdminQuestionDto
import io.ntole.kvizic.core.question.AdminQuestionPageDto
import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.EditQuestionRequest
import io.ntole.kvizic.core.question.ImportQuestionsRequest
import io.ntole.kvizic.core.question.ImportResultDto
import io.ntole.kvizic.core.question.QuestionDecisionRequest
import io.ntole.kvizic.core.question.QuestionDraftDto
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.server.FLOW_ADMIN_TOKEN
import io.ntole.kvizic.server.runTestServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The moderator's work on the question bank, over HTTP. */
class QuestionAdminFlowTest {
    private fun draft(
        key: String,
        text: String = "Питање $key?",
        topics: List<String> = listOf("GEOGRAPHY"),
    ) = QuestionDraftDto(
        key = key,
        text = text,
        options = listOf("Тачан $key", "Други $key", "Трећи $key", "Четврти $key"),
        correct = 0,
        topics = topics,
        difficulty = Difficulty.EASY,
        source = "https://sr.wikipedia.org/wiki/$key",
        author = "claude",
    )

    private suspend fun HttpClient.admin(
        path: String,
        body: Any,
    ): HttpResponse =
        post(path) {
            header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpClient.list(vararg query: Pair<String, String>): AdminQuestionPageDto =
        get(KvizicApi.Paths.ADMIN_QUESTIONS) {
            header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN)
            query.forEach { (name, value) -> parameter(name, value) }
        }.body()

    @Test
    fun `an import comes in as drafts, and again is nothing new`() =
        runTestServer("bank-import") { client, _ ->
            val request = ImportQuestionsRequest("batch-01", listOf(draft("a"), draft("b"), draft("bad", text = " ")))
            val first = client.admin(KvizicApi.Paths.ADMIN_QUESTION_IMPORTS, request).body<ImportResultDto>()
            assertEquals(listOf("a", "b"), first.created)
            assertEquals(listOf("bad"), first.refused.map { it.key })

            val again = client.admin(KvizicApi.Paths.ADMIN_QUESTION_IMPORTS, request).body<ImportResultDto>()
            assertEquals(emptyList(), again.created)
            assertEquals(listOf("a", "b"), again.duplicates)

            val drafts = client.list(KvizicApi.Query.STATUS to "DRAFT").questions
            assertEquals(setOf("a", "b"), drafts.map { it.importKey }.toSet())
            val a = drafts.first { it.importKey == "a" }
            assertEquals("Тачан a", a.options[a.correct])
            assertEquals("https://sr.wikipedia.org/wiki/a", a.source)
            assertEquals(1, a.revision)
        }

    @Test
    fun `an import too big, or of no batch, is refused whole`() =
        runTestServer("bank-import-limits") { client, _ ->
            val tooMany = ImportQuestionsRequest("big", (1..26).map { draft("k$it") })
            assertEquals(
                HttpStatusCode.BadRequest,
                client.admin(KvizicApi.Paths.ADMIN_QUESTION_IMPORTS, tooMany).status,
            )
            val noBatch = ImportQuestionsRequest(" ", listOf(draft("x")))
            assertEquals(
                HttpStatusCode.BadRequest,
                client.admin(KvizicApi.Paths.ADMIN_QUESTION_IMPORTS, noBatch).status,
            )
            assertTrue(client.list().questions.isEmpty())
        }

    @Test
    fun `a draft is approved or rejected once, and an approved one retired and restored`() =
        runTestServer("bank-moves") { client, _ ->
            client.admin(
                KvizicApi.Paths.ADMIN_QUESTION_IMPORTS,
                ImportQuestionsRequest("b", listOf(draft("a"), draft("b"))),
            )
            val (a, b) = client.list().questions.sortedBy { it.importKey }

            val approved =
                client
                    .admin(
                        KvizicApi.Paths.ADMIN_QUESTION_APPROVALS,
                        QuestionDecisionRequest(a.id),
                    ).body<AdminQuestionDto>()
            assertEquals(QuestionStatus.APPROVED, approved.status)
            val noReason = client.admin(KvizicApi.Paths.ADMIN_QUESTION_REJECTIONS, QuestionDecisionRequest(b.id))
            assertEquals(HttpStatusCode.BadRequest, noReason.status)
            val rejected =
                client
                    .admin(
                        KvizicApi.Paths.ADMIN_QUESTION_REJECTIONS,
                        QuestionDecisionRequest(b.id, "Нејасно"),
                    ).body<AdminQuestionDto>()
            assertEquals("Нејасно", rejected.rejectionReason)

            val twice = client.admin(KvizicApi.Paths.ADMIN_QUESTION_REJECTIONS, QuestionDecisionRequest(a.id, "касно"))
            assertEquals(HttpStatusCode.Conflict, twice.status)
            assertEquals(ErrorCode.WRONG_STATUS, twice.body<ErrorDto>().code)

            val retired =
                client
                    .admin(
                        KvizicApi.Paths.ADMIN_QUESTION_RETIREMENTS,
                        QuestionDecisionRequest(a.id),
                    ).body<AdminQuestionDto>()
            assertEquals(QuestionStatus.RETIRED, retired.status)
            val restored =
                client
                    .admin(
                        KvizicApi.Paths.ADMIN_QUESTION_RESTORATIONS,
                        QuestionDecisionRequest(a.id),
                    ).body<AdminQuestionDto>()
            assertEquals(QuestionStatus.APPROVED, restored.status)
            assertNull(restored.rejectionReason)

            val missing = client.admin(KvizicApi.Paths.ADMIN_QUESTION_APPROVALS, QuestionDecisionRequest("nope"))
            assertEquals(HttpStatusCode.NotFound, missing.status)
        }

    @Test
    fun `an edit is made from the revision read, and a stale one is refused`() =
        runTestServer("bank-edit") { client, _ ->
            client.admin(KvizicApi.Paths.ADMIN_QUESTION_IMPORTS, ImportQuestionsRequest("b", listOf(draft("a"))))
            val a = client.list().questions.single()
            val edit =
                EditQuestionRequest(
                    id = a.id,
                    revision = a.revision,
                    text = "Ново питање?",
                    options = listOf("Један", "Два", "Три"),
                    correct = 1,
                    topics = listOf("SPORT", "GEOGRAPHY"),
                    difficulty = Difficulty.HARD,
                )
            val edited = client.admin(KvizicApi.Paths.ADMIN_QUESTION_EDITS, edit).body<AdminQuestionDto>()
            assertEquals(2, edited.revision)
            assertEquals(listOf("Један", "Два", "Три"), edited.options)
            assertEquals("Два", edited.options[edited.correct])
            assertEquals(listOf("GEOGRAPHY", "SPORT"), edited.topics, "in the order of topics")

            val stale = client.admin(KvizicApi.Paths.ADMIN_QUESTION_EDITS, edit.copy(text = "Друго?"))
            assertEquals(HttpStatusCode.Conflict, stale.status)
            assertEquals(ErrorCode.STALE_REVISION, stale.body<ErrorDto>().code)

            val refused = client.admin(KvizicApi.Paths.ADMIN_QUESTION_EDITS, edit.copy(revision = 2, correct = 7))
            assertEquals(HttpStatusCode.UnprocessableEntity, refused.status)
            assertEquals(ErrorCode.INVALID_QUESTION, refused.body<ErrorDto>().code)
        }

    @Test
    fun `the list pages newest first, and filters by status, topic and text`() =
        runTestServer("bank-list") { client, _ ->
            val drafts =
                (1..5).map {
                    draft(
                        "k$it",
                        text = "Питање о броју $it?",
                        topics =
                            if (it % 2 ==
                                0
                            ) {
                                listOf("SPORT")
                            } else {
                                listOf("GEOGRAPHY")
                            },
                    )
                }
            client.admin(KvizicApi.Paths.ADMIN_QUESTION_IMPORTS, ImportQuestionsRequest("b", drafts))

            val first = client.list(KvizicApi.Query.LIMIT to "2")
            assertEquals(2, first.questions.size)
            val second =
                client.list(
                    KvizicApi.Query.LIMIT to "2",
                    KvizicApi.Query.CURSOR to checkNotNull(first.nextCursor),
                )
            val third =
                client.list(
                    KvizicApi.Query.LIMIT to "2",
                    KvizicApi.Query.CURSOR to checkNotNull(second.nextCursor),
                )
            assertNull(third.nextCursor)
            assertEquals(
                5,
                (first.questions + second.questions + third.questions).map { it.id }.toSet().size,
                "each once",
            )

            assertEquals(2, client.list(KvizicApi.Query.TOPIC to "SPORT").questions.size)
            assertEquals(listOf("k3"), client.list(KvizicApi.Query.SEARCH to "БРОЈУ 3").questions.map { it.importKey })
            assertEquals(
                listOf("k4"),
                client.list(KvizicApi.Query.SEARCH to "четврти k4").questions.map {
                    it.importKey
                },
                "the answers too",
            )
            assertTrue(client.list(KvizicApi.Query.SEARCH to "100%").questions.isEmpty(), "a wildcard is a character")

            val badStatus =
                client.get(KvizicApi.Paths.ADMIN_QUESTIONS) {
                    header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN)
                    parameter(KvizicApi.Query.STATUS, "LOST")
                }
            assertEquals(HttpStatusCode.BadRequest, badStatus.status)
        }

    @Test
    fun `an export imports back as it is`() =
        runTestServer("bank-export") { client, _ ->
            client.admin(KvizicApi.Paths.ADMIN_QUESTION_IMPORTS, ImportQuestionsRequest("b", listOf(draft("a"))))
            val a = client.list().questions.single()
            client.admin(KvizicApi.Paths.ADMIN_QUESTION_APPROVALS, QuestionDecisionRequest(a.id))

            val export =
                client
                    .get(
                        KvizicApi.Paths.ADMIN_QUESTION_EXPORTS,
                    ) { header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN) }
                    .body<ImportQuestionsRequest>()
            assertEquals(listOf(draft("a").copy(language = "sr-Cyrl")), export.questions)
        }

    @Test
    fun `every bank route needs the admin token`() =
        runTestServer("bank-auth") { client, _ ->
            assertEquals(HttpStatusCode.Forbidden, client.get(KvizicApi.Paths.ADMIN_QUESTIONS).status)
            val noToken =
                client.post(KvizicApi.Paths.ADMIN_QUESTION_IMPORTS) {
                    contentType(ContentType.Application.Json)
                    setBody(ImportQuestionsRequest("b", listOf(draft("a"))))
                }
            assertEquals(HttpStatusCode.Forbidden, noToken.status)
        }
}
