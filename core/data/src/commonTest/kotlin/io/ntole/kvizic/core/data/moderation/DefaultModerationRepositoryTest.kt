package io.ntole.kvizic.core.data.moderation

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.data.BASE_URL
import io.ntole.kvizic.core.data.respondErrorDto
import io.ntole.kvizic.core.data.respondJson
import io.ntole.kvizic.core.data.storeHolding
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.GameError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.moderation.AccountFilter
import io.ntole.kvizic.core.domain.moderation.AccountOrder
import io.ntole.kvizic.core.domain.moderation.AccountTopicStat
import io.ntole.kvizic.core.domain.moderation.AdminToken
import io.ntole.kvizic.core.domain.moderation.BankStatus
import io.ntole.kvizic.core.domain.moderation.QuestionDifficulty
import io.ntole.kvizic.core.domain.moderation.QuestionEdit
import io.ntole.kvizic.core.domain.moderation.QuestionFilter
import io.ntole.kvizic.core.domain.moderation.ReportOutcome
import io.ntole.kvizic.core.domain.report.QuestionReportReason
import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.network.KvizicHttpClient
import io.ntole.kvizic.core.network.KvizicJson
import io.ntole.kvizic.core.network.api.ModerationApi
import io.ntole.kvizic.core.player.AdminAccountDetailDto
import io.ntole.kvizic.core.player.AdminAccountDto
import io.ntole.kvizic.core.player.AdminAccountPageDto
import io.ntole.kvizic.core.player.AdminGameDto
import io.ntole.kvizic.core.player.AdminTopicStatDto
import io.ntole.kvizic.core.player.DeleteAccountRequest
import io.ntole.kvizic.core.player.PlayerStatsDto
import io.ntole.kvizic.core.question.AdminOverviewDto
import io.ntole.kvizic.core.question.AdminQuestionDto
import io.ntole.kvizic.core.question.AdminQuestionPageDto
import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.EditQuestionRequest
import io.ntole.kvizic.core.question.QuestionDecisionRequest
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.core.question.StatusCountDto
import io.ntole.kvizic.core.report.AdminReportDto
import io.ntole.kvizic.core.report.AdminReportListDto
import io.ntole.kvizic.core.report.ReasonCountDto
import io.ntole.kvizic.core.report.ReportReason
import io.ntole.kvizic.core.report.ResolveReportsRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/** The moderator's calls through the real client, against a [MockEngine] that answers as the server. */
class DefaultModerationRepositoryTest {
    private val token = AdminToken.of("  admin-secret  ").let { requireNotNull(it) }
    private val sent = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()
    private var answer: (HttpRequestData) -> Answer = { Answer.Json(KvizicJson.encodeToString(QUESTION)) }

    private sealed interface Answer {
        data class Json(
            val body: String,
        ) : Answer

        data class Error(
            val status: HttpStatusCode,
            val code: ErrorCode,
        ) : Answer

        data class Limited(
            val seconds: Int,
        ) : Answer

        data object NoContent : Answer
    }

    private val repository =
        DefaultModerationRepository(
            ModerationApi(
                KvizicHttpClient.create(
                    BASE_URL,
                    storeHolding(null),
                    engine =
                        MockEngine { request ->
                            sent += request
                            bodies += request.body.toByteArray().decodeToString()
                            when (val reply = answer(request)) {
                                is Answer.Json -> {
                                    respondJson(reply.body)
                                }

                                is Answer.Error -> {
                                    respondErrorDto(reply.status, reply.code)
                                }

                                is Answer.Limited -> {
                                    respond(
                                        "",
                                        HttpStatusCode.TooManyRequests,
                                        headersOf(HttpHeaders.RetryAfter, reply.seconds.toString()),
                                    )
                                }

                                Answer.NoContent -> {
                                    respond("", HttpStatusCode.NoContent)
                                }
                            }
                        },
                ),
            ),
        )

    @Test
    fun `every call carries the admin token trimmed and never a bearer`() =
        runTest {
            repository.approve(token, "q1")
            repository.reject(token, "q1", "  Two answers are right.  ")
            repository.retire(token, "q1")
            repository.restore(token, "q1")

            assertEquals(
                listOf(
                    KvizicApi.Paths.ADMIN_QUESTION_APPROVALS,
                    KvizicApi.Paths.ADMIN_QUESTION_REJECTIONS,
                    KvizicApi.Paths.ADMIN_QUESTION_RETIREMENTS,
                    KvizicApi.Paths.ADMIN_QUESTION_RESTORATIONS,
                ),
                sent.map { it.url.encodedPath },
            )
            sent.forEach {
                assertEquals("admin-secret", it.headers[KvizicApi.Headers.ADMIN_TOKEN])
                assertNull(it.headers[HttpHeaders.Authorization])
            }
            assertEquals(
                listOf(
                    QuestionDecisionRequest("q1"),
                    QuestionDecisionRequest("q1", "Two answers are right."),
                    QuestionDecisionRequest("q1"),
                    QuestionDecisionRequest("q1"),
                ),
                bodies.map { KvizicJson.decodeFromString<QuestionDecisionRequest>(it) },
            )
        }

    @Test
    fun `a page asks for its filter and cursor at the most the server lists`() =
        runTest {
            answer = {
                Answer.Json(KvizicJson.encodeToString(AdminQuestionPageDto(listOf(QUESTION), nextCursor = "next")))
            }

            val page =
                repository.questions(
                    token,
                    QuestionFilter(
                        statuses = setOf(BankStatus.SUSPENDED, BankStatus.DRAFT, BankStatus.OTHER),
                        topics = setOf("SPORT", "GEOGRAPHY"),
                        search = "  Дунав ",
                    ),
                    cursor = "c1",
                )

            val parameters = sent.single().url.parameters
            assertEquals(listOf("DRAFT", "SUSPENDED"), parameters.getAll(KvizicApi.Query.STATUS))
            assertEquals(listOf("GEOGRAPHY", "SPORT"), parameters.getAll(KvizicApi.Query.TOPIC))
            assertEquals("Дунав", parameters[KvizicApi.Query.SEARCH])
            assertEquals("c1", parameters[KvizicApi.Query.CURSOR])
            assertEquals(KvizicApi.Limits.MAX_PAGE_SIZE.toString(), parameters[KvizicApi.Query.LIMIT])
            assertEquals("next", page.next)
            assertEquals("q1", page.questions.single().id)
        }

    @Test
    fun `a page with no filter asks for every status and topic`() =
        runTest {
            answer = { Answer.Json(KvizicJson.encodeToString(AdminQuestionPageDto())) }

            repository.questions(token, QuestionFilter(search = "   "))

            val parameters = sent.single().url.parameters
            assertEquals(setOf(KvizicApi.Query.LIMIT), parameters.names())
        }

    @Test
    fun `a question is read whole and a status or difficulty this build cannot name as other`() =
        runTest {
            answer = {
                Answer.Json(
                    KvizicJson.encodeToString(
                        AdminQuestionPageDto(
                            listOf(QUESTION.copy(status = QuestionStatus.UNKNOWN, difficulty = Difficulty.UNKNOWN)),
                        ),
                    ),
                )
            }

            val question = repository.questions(token, QuestionFilter()).questions.single()

            assertEquals(BankStatus.OTHER, question.status)
            assertEquals(QuestionDifficulty.OTHER, question.difficulty)
            assertEquals(listOf("Дунав", "Сава", "Тиса", "Морава"), question.options)
            assertEquals(2, question.correct)
            assertEquals(7, question.revision)
            assertEquals(75, question.play.correctPercent)
        }

    @Test
    fun `an edit goes trimmed and a blank explanation and source as none`() =
        runTest {
            repository.edit(
                token,
                QuestionEdit(
                    id = "q1",
                    revision = 7,
                    text = " Која река? ",
                    options = listOf(" Дунав", "Сава "),
                    correct = 1,
                    topics = listOf("GEOGRAPHY"),
                    difficulty = QuestionDifficulty.HARD,
                    explanation = "  ",
                    source = "",
                ),
            )

            assertEquals(KvizicApi.Paths.ADMIN_QUESTION_EDITS, sent.single().url.encodedPath)
            assertEquals(
                EditQuestionRequest(
                    id = "q1",
                    revision = 7,
                    text = "Која река?",
                    options = listOf("Дунав", "Сава"),
                    correct = 1,
                    topics = listOf("GEOGRAPHY"),
                    difficulty = Difficulty.HARD,
                    explanation = null,
                    source = null,
                ),
                KvizicJson.decodeFromString<EditQuestionRequest>(bodies.single()),
            )
        }

    @Test
    fun `reports are counted by reason most given first with an unknown one under none`() =
        runTest {
            answer = {
                Answer.Json(
                    KvizicJson.encodeToString(
                        AdminReportListDto(
                            listOf(
                                AdminReportDto(
                                    question = QUESTION,
                                    open = 6,
                                    reasons =
                                        listOf(
                                            ReasonCountDto(ReportReason.TYPO, 1),
                                            ReasonCountDto(ReportReason.WRONG_ANSWER, 3),
                                            ReasonCountDto(ReportReason.UNKNOWN, 2),
                                        ),
                                    lastReportedAt = 99,
                                ),
                            ),
                        ),
                    ),
                )
            }

            val reported = repository.reports(token).single()

            assertEquals(KvizicApi.Limits.MAX_PAGE_SIZE.toString(), sent.single().url.parameters[KvizicApi.Query.LIMIT])
            assertEquals(6, reported.open)
            assertEquals(
                listOf(QuestionReportReason.WRONG_ANSWER to 3, null to 2, QuestionReportReason.TYPO to 1),
                reported.reasons,
            )
        }

    @Test
    fun `a resolution and a deletion and the overview reach their routes`() =
        runTest {
            answer = { request ->
                if (request.url.encodedPath == KvizicApi.Paths.ADMIN_OVERVIEW) {
                    Answer.Json(
                        KvizicJson.encodeToString(
                            AdminOverviewDto(
                                byStatus =
                                    listOf(
                                        StatusCountDto(QuestionStatus.APPROVED, 40),
                                        StatusCountDto(QuestionStatus.UNKNOWN, 1),
                                    ),
                                liveGames = 2,
                            ),
                        ),
                    )
                } else {
                    Answer.NoContent
                }
            }

            repository.resolve(token, "q1", ReportOutcome.DISMISSED)
            repository.deleteAccount(token, " p1 ")
            val overview = repository.overview(token)

            assertEquals(ResolveReportsRequest("q1", io.ntole.kvizic.core.report.ReportResolution.DISMISSED), decode(0))
            assertEquals(DeleteAccountRequest("p1"), KvizicJson.decodeFromString<DeleteAccountRequest>(bodies[1]))
            assertEquals(mapOf(BankStatus.APPROVED to 40, BankStatus.OTHER to 1), overview.byStatus)
            assertEquals(2, overview.liveGames)
        }

    @Test
    fun `accounts ask for their order and search and cursor and come back as the moderator reads them`() =
        runTest {
            answer = {
                Answer.Json(
                    KvizicJson.encodeToString(
                        AdminAccountPageDto(
                            accounts =
                                listOf(
                                    AdminAccountDto(
                                        playerId = "p1",
                                        displayName = "Лукави Лисац",
                                        avatarId = "fox",
                                        level = 3,
                                        xp = 45,
                                        stats = PlayerStatsDto(gamesPlayed = 4, answersGiven = 20, answersCorrect = 15),
                                        createdAt = 1_000L,
                                    ),
                                ),
                            nextCursor = "50",
                            total = 51,
                        ),
                    ),
                )
            }

            val page =
                repository.accounts(
                    token,
                    AccountOrder.LEVEL,
                    AccountFilter(search = "  лис ", answeredOnly = true),
                    cursor = "0",
                )

            val parameters = sent.single().url.parameters
            assertEquals(KvizicApi.Paths.ADMIN_ACCOUNTS, sent.single().url.encodedPath)
            assertEquals("LEVEL", parameters[KvizicApi.Query.SORT])
            assertEquals("лис", parameters[KvizicApi.Query.SEARCH])
            assertEquals("true", parameters[KvizicApi.Query.ANSWERED])
            assertNull(parameters[KvizicApi.Query.PLAY_GAMES])
            assertEquals("0", parameters[KvizicApi.Query.CURSOR])
            assertEquals(KvizicApi.Limits.MAX_PAGE_SIZE.toString(), parameters[KvizicApi.Query.LIMIT])
            assertEquals("50", page.next)
            assertEquals(51, page.total)
            val account = page.accounts.single()
            assertEquals("p1", account.id)
            assertEquals(75, account.accuracyPercent)
            assertEquals(1_000L, account.lastActiveAt)
        }

    @Test
    fun `one account is read by its trimmed id and its games and topics come with it`() =
        runTest {
            answer = {
                Answer.Json(
                    KvizicJson.encodeToString(
                        AdminAccountDetailDto(
                            account =
                                AdminAccountDto(
                                    playerId = "p1",
                                    displayName = "A",
                                    avatarId = "fox",
                                    createdAt = 1L,
                                ),
                            topics = listOf(AdminTopicStatDto("SPORT", answered = 4, correct = 3)),
                            recentGames = listOf(AdminGameDto(endedAt = 9L, standing = 2, participants = 4)),
                        ),
                    ),
                )
            }

            val detail = repository.account(token, " p1 ")

            assertEquals("/v1/admin/accounts/p1", sent.single().url.encodedPath)
            assertEquals(AccountTopicStat("SPORT", 4, 3), detail.topics.single())
            assertEquals(2, detail.recentGames.single().standing)
        }

    @Test
    fun `a wrong token and a limit and a stale edit are each named`() =
        runTest {
            answer = { Answer.Error(HttpStatusCode.Forbidden, ErrorCode.FORBIDDEN) }
            assertEquals(CoreError.FORBIDDEN, assertFailsWith<KvizicException> { repository.overview(token) }.error)

            answer = { Answer.Limited(seconds = 42) }
            val limited = assertFailsWith<KvizicException> { repository.approve(token, "q1") }
            assertEquals(CoreError.RATE_LIMITED, limited.error)
            assertEquals(42.seconds, limited.retryAfter)

            answer = { Answer.Error(HttpStatusCode.Conflict, ErrorCode.STALE_REVISION) }
            assertEquals(
                GameError.STALE_REVISION,
                assertFailsWith<KvizicException> { repository.edit(token, QuestionEdit.of(QUESTION.toDomain())) }.error,
            )
            assertEquals(3, sent.size, "something was sent again")
        }

    private fun decode(index: Int): ResolveReportsRequest = KvizicJson.decodeFromString(bodies[index])

    private companion object {
        val QUESTION =
            AdminQuestionDto(
                id = "q1",
                status = QuestionStatus.DRAFT,
                text = "Која река протиче кроз Нови Сад?",
                options = listOf("Дунав", "Сава", "Тиса", "Морава"),
                correct = 2,
                topics = listOf("GEOGRAPHY"),
                difficulty = Difficulty.EASY,
                revision = 7,
                createdAt = 1,
                updatedAt = 2,
                stats =
                    io.ntole.kvizic.core.question
                        .QuestionStatsDto(shown = 10, answered = 8, correct = 6),
            )
    }
}
