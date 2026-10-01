package io.ntole.kvizic.core.network.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.player.DeleteAccountRequest
import io.ntole.kvizic.core.question.AdminOverviewDto
import io.ntole.kvizic.core.question.AdminQuestionDto
import io.ntole.kvizic.core.question.AdminQuestionPageDto
import io.ntole.kvizic.core.question.EditQuestionRequest
import io.ntole.kvizic.core.question.QuestionDecisionRequest
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.core.report.AdminReportListDto
import io.ntole.kvizic.core.report.ResolveReportsRequest

/**
 * The moderator's admin routes. Each call carries the admin [token] it is given in its header, and only
 * that call: nothing here keeps it. The client it runs on should be a moderation client's, which holds no
 * player session, so no bearer goes with it and a 403 is never taken for a dead session.
 */
public class ModerationApi(
    private val client: HttpClient,
) {
    public suspend fun overview(token: String): AdminOverviewDto =
        client.get(KvizicApi.Paths.ADMIN_OVERVIEW) { admin(token) }.body()

    /** A page of the bank, newest first: one `status` and `topic` parameter per value, none being all. */
    public suspend fun questions(
        token: String,
        statuses: List<QuestionStatus>,
        topics: List<String>,
        search: String?,
        cursor: String?,
        limit: Int,
    ): AdminQuestionPageDto =
        client
            .get(KvizicApi.Paths.ADMIN_QUESTIONS) {
                admin(token)
                statuses.forEach { parameter(KvizicApi.Query.STATUS, it.name) }
                topics.forEach { parameter(KvizicApi.Query.TOPIC, it) }
                search?.let { parameter(KvizicApi.Query.SEARCH, it) }
                cursor?.let { parameter(KvizicApi.Query.CURSOR, it) }
                parameter(KvizicApi.Query.LIMIT, limit)
            }.body()

    public suspend fun edit(
        token: String,
        request: EditQuestionRequest,
    ): AdminQuestionDto = post(KvizicApi.Paths.ADMIN_QUESTION_EDITS, token, request)

    public suspend fun approve(
        token: String,
        request: QuestionDecisionRequest,
    ): AdminQuestionDto = post(KvizicApi.Paths.ADMIN_QUESTION_APPROVALS, token, request)

    public suspend fun reject(
        token: String,
        request: QuestionDecisionRequest,
    ): AdminQuestionDto = post(KvizicApi.Paths.ADMIN_QUESTION_REJECTIONS, token, request)

    public suspend fun retire(
        token: String,
        request: QuestionDecisionRequest,
    ): AdminQuestionDto = post(KvizicApi.Paths.ADMIN_QUESTION_RETIREMENTS, token, request)

    public suspend fun restore(
        token: String,
        request: QuestionDecisionRequest,
    ): AdminQuestionDto = post(KvizicApi.Paths.ADMIN_QUESTION_RESTORATIONS, token, request)

    /** The questions with open reports, the most reported first, at most [limit]. */
    public suspend fun reports(
        token: String,
        limit: Int,
    ): AdminReportListDto =
        client
            .get(KvizicApi.Paths.ADMIN_REPORTS) {
                admin(token)
                parameter(KvizicApi.Query.LIMIT, limit)
            }.body()

    public suspend fun resolve(
        token: String,
        request: ResolveReportsRequest,
    ) {
        client.post(KvizicApi.Paths.ADMIN_REPORT_RESOLUTIONS) { json(token, request) }
    }

    public suspend fun deleteAccount(
        token: String,
        request: DeleteAccountRequest,
    ) {
        client.post(KvizicApi.Paths.ADMIN_ACCOUNT_DELETIONS) { json(token, request) }
    }

    private suspend inline fun <reified T> post(
        path: String,
        token: String,
        body: T,
    ): AdminQuestionDto = client.post(path) { json(token, body) }.body()

    private inline fun <reified T> HttpRequestBuilder.json(
        token: String,
        body: T,
    ) {
        admin(token)
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun HttpRequestBuilder.admin(token: String) = header(KvizicApi.Headers.ADMIN_TOKEN, token)
}
