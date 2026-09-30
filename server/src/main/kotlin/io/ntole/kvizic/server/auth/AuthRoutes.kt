package io.ntole.kvizic.server.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.auth.RefreshRequest
import io.ntole.kvizic.core.auth.SessionDto
import io.ntole.kvizic.server.config.ServerConfig
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.player.PlayerStore
import io.ntole.kvizic.server.plugins.ApiFailure
import io.ntole.kvizic.server.plugins.RouteLimit
import io.ntole.kvizic.server.plugins.rateLimit
import io.ntole.kvizic.server.plugins.receiveOrReject

/**
 * Sessions. A guest's mint and a refresh are public: the first has no credential yet, the second's
 * refresh token is one. A logout ends the session the bearer names, and [sessionEnded] then closes the
 * sockets it holds.
 */
fun Route.authRoutes(
    db: Db,
    tokens: TokenService,
    config: ServerConfig,
    sessionEnded: (playerId: String, sessionId: String) -> Unit,
) {
    // The server mints the player, so the identity cannot be forged by a tampered client. Limited per
    // address: what bounds a script minting guests.
    rateLimit(RouteLimit.GUESTS) {
        post(KvizicApi.Paths.AUTH_GUEST) {
            val refresh = tokens.issueRefreshToken()
            val expiresAt = System.currentTimeMillis() + config.refreshTokenTtlSeconds * 1_000L

            // The guest, their profile and first session, in one transaction.
            val session = db.query { SessionStore.open(PlayerStore.createGuest().id, refresh.hash, expiresAt) }

            call.respond(tokens.answer(session, refresh, config))
        }
    }

    // Per address too, as the refresh token is the caller's only credential. A refusal rotates nothing.
    rateLimit(RouteLimit.REFRESHES) {
        post(KvizicApi.Paths.AUTH_REFRESH) {
            val body = call.receiveOrReject<RefreshRequest>("refresh request")

            if (body.refreshToken.isBlank()) throw ApiFailure.validation("refreshToken is blank")

            val rotated = tokens.issueRefreshToken()
            val expiresAt = System.currentTimeMillis() + config.refreshTokenTtlSeconds * 1_000L

            // Rotates on every use, in the session the token belongs to. The token a rotation displaces
            // still works once more, until the next rotation displaces it (the grace), so a refresh whose
            // answer was lost can be sent again.
            val session =
                db.query {
                    SessionStore.rotate(
                        presentedHash = tokens.hash(body.refreshToken),
                        newHash = rotated.hash,
                        expiresAt = expiresAt,
                        graceMillis = config.refreshGraceSeconds?.let { seconds -> seconds * 1_000L },
                    ) ?: throw ApiFailure.invalidRefreshToken()
                }

            call.respond(tokens.answer(session, rotated, config))
        }
    }

    authenticate(JWT_AUTH) {
        rateLimit(RouteLimit.LOGOUTS) {
            post(KvizicApi.Paths.AUTH_LOGOUT) {
                val playerId = call.authenticatedPlayerId()
                val sessionId = call.authenticatedSessionId()

                db.query { SessionStore.close(sessionId, playerId) }
                sessionEnded(playerId, sessionId)

                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

/** What a mint, a refresh and a Play Games sign-in answer: [session]'s credentials, its new [refresh] token among them. */
internal fun TokenService.answer(
    session: SessionStore.Session,
    refresh: TokenService.Opaque,
    config: ServerConfig,
): SessionDto =
    SessionDto(
        playerId = session.playerId,
        accessToken = issueAccessToken(session.playerId, session.id),
        refreshToken = refresh.value,
        accessTokenExpiresInSeconds = config.accessTokenTtlSeconds,
    )
