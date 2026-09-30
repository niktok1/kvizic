package io.ntole.kvizic.server.player

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.player.Avatars
import io.ntole.kvizic.core.player.SetAvatarRequest
import io.ntole.kvizic.server.auth.JWT_AUTH
import io.ntole.kvizic.server.auth.authenticatedPlayerId
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.plugins.ApiFailure
import io.ntole.kvizic.server.plugins.RouteLimit
import io.ntole.kvizic.server.plugins.rateLimit
import io.ntole.kvizic.server.plugins.receiveOrReject

/**
 * "Me" is whoever the bearer names: their profile, their avatar and deleting their account. A deletion
 * calls [playerDeleted] once committed, which closes the player's live sockets.
 */
fun Route.playerRoutes(
    db: Db,
    playerDeleted: (playerId: String) -> Unit,
) {
    authenticate(JWT_AUTH) {
        rateLimit(RouteLimit.ME) {
            get(KvizicApi.Paths.ME) {
                val playerId = call.authenticatedPlayerId()
                // A validly signed token can outlive its player.
                val profile = db.query { ProfileStore.of(playerId) } ?: throw ApiFailure.unauthorized("unknown player")
                call.respond(profile)
            }
        }

        rateLimit(RouteLimit.AVATARS) {
            post(KvizicApi.Paths.MY_AVATAR) {
                val playerId = call.authenticatedPlayerId()
                val body = call.receiveOrReject<SetAvatarRequest>("avatar")
                if (body.avatarId !in Avatars.ALL) throw ApiFailure.invalidAvatar()

                val profile =
                    db.query {
                        if (!PlayerStore.setAvatar(
                                playerId,
                                body.avatarId,
                            )
                        ) {
                            throw ApiFailure.unauthorized("unknown player")
                        }
                        ProfileStore.of(playerId)
                    } ?: throw ApiFailure.unauthorized("unknown player")
                call.respond(profile)
            }
        }

        rateLimit(RouteLimit.DELETIONS) {
            post(KvizicApi.Paths.ME_DELETION) {
                val playerId = call.authenticatedPlayerId()

                db.query { AccountDeletion.delete(playerId) }
                playerDeleted(playerId)
                call.application.log.info("player $playerId deleted their account")

                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
