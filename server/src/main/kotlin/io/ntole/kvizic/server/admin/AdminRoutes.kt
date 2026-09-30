package io.ntole.kvizic.server.admin

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.player.DeleteAccountRequest
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.db.Players
import io.ntole.kvizic.server.player.AccountDeletion
import io.ntole.kvizic.server.plugins.ApiFailure
import io.ntole.kvizic.server.plugins.RouteLimit
import io.ntole.kvizic.server.plugins.rateLimit
import io.ntole.kvizic.server.plugins.receiveOrReject
import io.ntole.kvizic.server.plugins.requireValidId
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select

/**
 * Puts the admin routes [build] declares behind the admin token: every one spends from the admin budget,
 * and one without the right token from the failed-token budget first, which is what bounds guessing it.
 * With no [adminToken] configured none is registered, so each path is 404.
 */
fun Route.adminRoutes(
    adminToken: AdminToken?,
    build: Route.(AdminToken) -> Unit,
) {
    if (adminToken == null) return
    rateLimit(RouteLimit.ADMIN_TOKEN_FAILURES) {
        rateLimit(RouteLimit.ADMIN) {
            build(adminToken)
        }
    }
}

/** A moderator deleting a player's account at their request, by the account id the player sent. */
fun Route.accountDeletionRoutes(
    db: Db,
    adminToken: AdminToken,
    playerDeleted: (playerId: String) -> Unit,
) {
    post(KvizicApi.Paths.ADMIN_ACCOUNT_DELETIONS) {
        call.requireAdmin(adminToken)
        val request = call.receiveOrReject<DeleteAccountRequest>("account deletion")
        val accountId = request.accountId.trim()
        requireValidId("accountId", accountId)

        db.query {
            // Locked as it is found; the deletion locks it again. Of two racing, the second waits and is 404.
            Players
                .select(Players.id)
                .where { Players.id eq accountId }
                .forUpdate()
                .singleOrNull() ?: throw ApiFailure.playerNotFound()
            AccountDeletion.delete(accountId)
        }
        playerDeleted(accountId)
        call.logAdmin("deleted the account of player", accountId)

        call.respond(HttpStatusCode.NoContent)
    }
}

/**
 * One INFO line for an admin action that went through, naming [what] it did and to [id]: the operator's
 * trail of what the moderator did. Never a token, nor text the moderator typed.
 */
internal fun ApplicationCall.logAdmin(
    what: String,
    id: String,
) = application.log.info("admin $what $id")
