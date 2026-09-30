package io.ntole.kvizic.server.question

import io.ktor.http.Parameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.question.EditQuestionRequest
import io.ntole.kvizic.core.question.ImportQuestionsRequest
import io.ntole.kvizic.core.question.QuestionDecisionRequest
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.server.admin.AdminToken
import io.ntole.kvizic.server.admin.logAdmin
import io.ntole.kvizic.server.admin.requireAdmin
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.plugins.ApiFailure
import io.ntole.kvizic.server.plugins.pageLimit
import io.ntole.kvizic.server.plugins.receiveOrReject
import io.ntole.kvizic.server.topic.TopicCatalog
import io.ntole.kvizic.server.topic.isOneLine

/**
 * The moderator's routes over the question bank, inside `adminRoutes`: imports come in as drafts, and
 * only an approved question is ever asked. None logs a question's text, an answer or a reason.
 */
fun Route.questionAdminRoutes(
    db: Db,
    adminToken: AdminToken,
    topics: TopicCatalog,
    clock: () -> Long = System::currentTimeMillis,
) {
    post(KvizicApi.Paths.ADMIN_QUESTION_IMPORTS) {
        call.requireAdmin(adminToken)
        val request = call.receiveOrReject<ImportQuestionsRequest>("import")
        val batch = request.batch.trim()
        if (batch.isEmpty() || batch.length > MAX_BATCH_LENGTH || !isOneLine(batch)) {
            throw ApiFailure.validation("a batch is named in one line of at most $MAX_BATCH_LENGTH characters")
        }
        if (request.questions.size > KvizicApi.Limits.MAX_IMPORT_BATCH) {
            throw ApiFailure.validation("an import holds at most ${KvizicApi.Limits.MAX_IMPORT_BATCH} questions")
        }
        val result =
            db.query {
                QuestionStore.import(
                    batch,
                    request.questions,
                    QuestionStatus.DRAFT,
                    topics.ids,
                    clock(),
                )
            }
        call.logAdmin(
            "imported ${result.created.size} new, ${result.duplicates.size} known and ${result.refused.size} refused of batch",
            batch,
        )
        call.respond(result)
    }

    get(KvizicApi.Paths.ADMIN_QUESTIONS) {
        call.requireAdmin(adminToken)
        val parameters = call.request.queryParameters
        val statuses = parameters.statuses()
        val wanted = parameters.getAll(KvizicApi.Query.TOPIC).orEmpty().toSet()
        val known = topics.ids
        wanted.firstOrNull { it !in known }?.let { throw ApiFailure.validation("no topic $it") }
        val cursor = parameters[KvizicApi.Query.CURSOR]?.let(QuestionCursor::parse)
        val search = parameters[KvizicApi.Query.SEARCH]?.take(MAX_SEARCH_LENGTH)
        call.respond(db.query { QuestionStore.page(statuses, wanted, search, cursor, parameters.pageLimit()) })
    }

    post(KvizicApi.Paths.ADMIN_QUESTION_EDITS) {
        call.requireAdmin(adminToken)
        val request = call.receiveOrReject<EditQuestionRequest>("edit")
        val edited = db.query { QuestionStore.edit(request, topics.ids, clock()) }
        call.logAdmin("edited question", edited.id)
        call.respond(edited)
    }

    suspend fun io.ktor.server.routing.RoutingContext.decide(
        what: String,
        from: Set<QuestionStatus>,
        to: QuestionStatus,
        needsReason: Boolean = false,
    ) {
        call.requireAdmin(adminToken)
        val request = call.receiveOrReject<QuestionDecisionRequest>(what)
        val reason =
            request.reason?.trim()?.takeIf { it.isNotEmpty() }?.also {
                if (it.length > KvizicApi.Limits.MAX_REASON_LENGTH || !isOneLine(it)) {
                    throw ApiFailure.validation(
                        "a reason is one line of at most ${KvizicApi.Limits.MAX_REASON_LENGTH} characters",
                    )
                }
            }
        if (needsReason && reason == null) throw ApiFailure.validation("a rejection needs a reason")
        val moved = db.query { QuestionStore.move(request.id, from, to, reason.takeIf { needsReason }, clock()) }
        call.logAdmin(what, moved.id)
        call.respond(moved)
    }

    post(KvizicApi.Paths.ADMIN_QUESTION_APPROVALS) {
        decide("approved question", setOf(QuestionStatus.DRAFT, QuestionStatus.REJECTED), QuestionStatus.APPROVED)
    }
    post(KvizicApi.Paths.ADMIN_QUESTION_REJECTIONS) {
        decide("rejected question", setOf(QuestionStatus.DRAFT), QuestionStatus.REJECTED, needsReason = true)
    }
    post(KvizicApi.Paths.ADMIN_QUESTION_RETIREMENTS) {
        decide("retired question", setOf(QuestionStatus.APPROVED, QuestionStatus.SUSPENDED), QuestionStatus.RETIRED)
    }
    post(KvizicApi.Paths.ADMIN_QUESTION_RESTORATIONS) {
        decide("restored question", setOf(QuestionStatus.RETIRED, QuestionStatus.SUSPENDED), QuestionStatus.APPROVED)
    }

    get(KvizicApi.Paths.ADMIN_QUESTION_EXPORTS) {
        call.requireAdmin(adminToken)
        val statuses =
            call.request.queryParameters
                .statuses()
                .ifEmpty { setOf(QuestionStatus.APPROVED) }
        val drafts = db.query { QuestionStore.export(statuses) }
        call.logAdmin("exported ${drafts.size} questions at", statuses.joinToString(","))
        call.respond(ImportQuestionsRequest(batch = "export", questions = drafts))
    }
}

/** The statuses asked for, by name, repeatable; none for all. A name that is no status is the client's mistake. */
private fun Parameters.statuses(): Set<QuestionStatus> =
    getAll(KvizicApi.Query.STATUS)
        .orEmpty()
        .map { name ->
            QuestionStatus.entries.firstOrNull { it.name == name && it != QuestionStatus.UNKNOWN }
                ?: throw ApiFailure.validation("no status $name")
        }.toSet()

private const val MAX_BATCH_LENGTH = 64
private const val MAX_SEARCH_LENGTH = 100
