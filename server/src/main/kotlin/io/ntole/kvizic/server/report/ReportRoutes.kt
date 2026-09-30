package io.ntole.kvizic.server.report

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.report.AdminReportListDto
import io.ntole.kvizic.core.report.ReportQuestionRequest
import io.ntole.kvizic.core.report.ReportReason
import io.ntole.kvizic.core.report.ReportResolution
import io.ntole.kvizic.core.report.ResolveReportsRequest
import io.ntole.kvizic.server.admin.AdminToken
import io.ntole.kvizic.server.admin.logAdmin
import io.ntole.kvizic.server.admin.requireAdmin
import io.ntole.kvizic.server.auth.JWT_AUTH
import io.ntole.kvizic.server.auth.authenticatedPlayerId
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.plugins.ApiFailure
import io.ntole.kvizic.server.plugins.RouteLimit
import io.ntole.kvizic.server.plugins.pageLimit
import io.ntole.kvizic.server.plugins.rateLimit
import io.ntole.kvizic.server.plugins.receiveOrReject

/** A player reporting a question the reveal showed them. Answered 204 whether or not it is their first. */
fun Route.reportRoutes(
    db: Db,
    clock: () -> Long = System::currentTimeMillis,
) {
    authenticate(JWT_AUTH) {
        rateLimit(RouteLimit.REPORTS) {
            post(KvizicApi.Paths.REPORTS) {
                val reporter = call.authenticatedPlayerId()
                val request = call.receiveOrReject<ReportQuestionRequest>("report")
                if (request.reason == ReportReason.UNKNOWN) throw ApiFailure.validation("no reason")
                when (db.query { ReportStore.report(reporter, request.questionId, request.reason, clock()) }) {
                    ReportStore.Outcome.REPORTED -> call.respond(HttpStatusCode.NoContent)
                    ReportStore.Outcome.NO_PLAYER -> throw ApiFailure.unauthorized("unknown player")
                    ReportStore.Outcome.NO_QUESTION -> throw ApiFailure.questionNotFound(request.questionId)
                }
            }
        }
    }
}

/** The moderator's side of reports, inside `adminRoutes`. */
fun Route.reportAdminRoutes(
    db: Db,
    adminToken: AdminToken,
    clock: () -> Long = System::currentTimeMillis,
) {
    get(KvizicApi.Paths.ADMIN_REPORTS) {
        call.requireAdmin(adminToken)
        val limit = call.request.queryParameters.pageLimit()
        call.respond(AdminReportListDto(db.query { ReportStore.open(limit) }))
    }

    post(KvizicApi.Paths.ADMIN_REPORT_RESOLUTIONS) {
        call.requireAdmin(adminToken)
        val request = call.receiveOrReject<ResolveReportsRequest>("resolution")
        if (request.resolution == ReportResolution.UNKNOWN) throw ApiFailure.validation("no resolution")
        if (!db.query { ReportStore.resolve(request.questionId, request.resolution, clock()) }) {
            throw ApiFailure.questionNotFound(request.questionId)
        }
        call.logAdmin("resolved the reports (${request.resolution.name.lowercase()}) of question", request.questionId)
        call.respond(HttpStatusCode.NoContent)
    }
}
