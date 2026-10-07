package io.ntole.kvizic.server.admin

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.player.DeleteAccountRequest
import io.ntole.kvizic.core.player.DeleteEmptyAccountsRequest
import io.ntole.kvizic.core.player.DeletedAccountsDto
import io.ntole.kvizic.core.player.EmptyAccountsDto
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
import kotlin.time.Duration.Companion.days

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
 * A moderator clearing out the empty accounts, such as the platform's pre-launch bots make: counted first
 * (GET), then deleted (POST) only when the count still is the one the moderator saw.
 */
fun Route.emptyAccountRoutes(
    db: Db,
    adminToken: AdminToken,
    playerDeleted: (playerId: String) -> Unit,
    clock: () -> Long = System::currentTimeMillis,
) {
    get(KvizicApi.Paths.ADMIN_EMPTY_ACCOUNTS) {
        call.requireAdmin(adminToken)
        val idleDays = call.request.queryParameters.idleDays()
        val ids = db.query { AccountAdmin.emptyIds(idleBefore(clock(), idleDays)) }
        call.respond(EmptyAccountsDto(idleDays, ids.size))
    }

    post(KvizicApi.Paths.ADMIN_EMPTY_ACCOUNTS) {
        call.requireAdmin(adminToken)
        val request = call.receiveOrReject<DeleteEmptyAccountsRequest>("empty account deletion")
        if (request.idleDays < MIN_IDLE_DAYS) throw ApiFailure.validation("idleDays is at least $MIN_IDLE_DAYS")
        val deleted =
            db.query {
                val ids = AccountAdmin.emptyIds(idleBefore(clock(), request.idleDays))
                if (ids.size != request.expectedCount) throw ApiFailure.staleCount()
                ids.filter { id ->
                    try {
                        AccountDeletion.delete(id)
                        true
                    } catch (gone: ApiFailure) {
                        false
                    }
                }
            }
        deleted.forEach(playerDeleted)
        call.logAdmin("deleted empty accounts, idle days", "${request.idleDays}: ${deleted.size}")

        call.respond(DeletedAccountsDto(deleted.size))
    }
}

private fun idleBefore(
    now: Long,
    idleDays: Int,
): Long = now - idleDays.days.inWholeMilliseconds

private fun Parameters.idleDays(): Int =
    this[KvizicApi.Query.IDLE_DAYS]?.toIntOrNull()?.takeIf { it >= MIN_IDLE_DAYS }
        ?: throw ApiFailure.validation("idleDays is a whole number of at least $MIN_IDLE_DAYS")

private const val MIN_IDLE_DAYS = 1

/**
 * One INFO line for an admin action that went through, naming [what] it did and to [id]: the operator's
 * trail of what the moderator did. Never a token, nor text the moderator typed.
 */
internal fun ApplicationCall.logAdmin(
    what: String,
    id: String,
) = application.log.info("admin $what $id")
