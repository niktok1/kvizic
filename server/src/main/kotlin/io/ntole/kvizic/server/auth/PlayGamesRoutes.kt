package io.ntole.kvizic.server.auth

import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.auth.PlayGamesSignInRequest
import io.ntole.kvizic.server.config.ServerConfig
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.player.DisplayNames
import io.ntole.kvizic.server.player.PlayerStore
import io.ntole.kvizic.server.plugins.ApiFailure
import io.ntole.kvizic.server.plugins.RouteLimit
import io.ntole.kvizic.server.plugins.rateLimit
import io.ntole.kvizic.server.plugins.receiveOrReject

/**
 * Signing in with Google Play Games Services, asking Google through [playGames], or no route at all when
 * that is null: a server without Play Games configured answers 404.
 *
 * The bearer is optional: a request without one gets through with no principal, and one whose token is
 * expired or forged is refused 401 before the code goes anywhere, so it stays unspent for the refreshed
 * retry. The player's shown name becomes their Play Games name, cleaned, at every sign-in; a name the
 * cleaning refuses leaves the one they had.
 */
fun Route.playGamesRoutes(
    db: Db,
    tokens: TokenService,
    config: ServerConfig,
    playGames: PlayGamesVerifier?,
) {
    if (playGames == null) return

    rateLimit(RouteLimit.PLAY_GAMES) {
        authenticate(JWT_AUTH, optional = true) {
            post(KvizicApi.Paths.AUTH_PLAY_GAMES) {
                val callerId = call.principal<JWTPrincipal>()?.payload?.playerId()
                val body = call.receiveOrReject<PlayGamesSignInRequest>("Play Games sign-in")
                val code = checkedServerAuthCode(body.serverAuthCode)

                // Outside the transaction, which then holds no connection while Google answers. From here
                // on the code is spent, so nothing after it refuses the sign-in.
                val player =
                    when (val answer = playGames.playerOf(code)) {
                        is PlayGamesAnswer.Player -> answer
                        PlayGamesAnswer.Refused -> throw ApiFailure.playGamesCodeRefused()
                        PlayGamesAnswer.Unavailable -> throw ApiFailure.playGamesUnavailable()
                    }
                val name = DisplayNames.clean(player.displayName)

                val refresh = tokens.issueRefreshToken()
                val expiresAt = System.currentTimeMillis() + config.refreshTokenTtlSeconds * 1_000L
                val session =
                    db.query {
                        val playerId = IdentityStore.signIn(IdentityProvider.PLAY_GAMES, player.playerId, callerId)
                        name?.let { PlayerStore.setPlayGamesName(playerId, it) }
                        SessionStore.open(playerId, refresh.hash, expiresAt)
                    }

                call.respond(tokens.answer(session, refresh, config))
            }
        }
    }
}

/**
 * [code] as a server auth code worth sending to Google, or [ApiFailure.validation]: not blank, at most
 * [KvizicApi.Limits.MAX_SERVER_AUTH_CODE_LENGTH], and visible ASCII only. The message never holds the code.
 */
internal fun checkedServerAuthCode(code: String): String {
    if (code.isEmpty()) throw ApiFailure.validation("serverAuthCode is blank")
    if (code.length > KvizicApi.Limits.MAX_SERVER_AUTH_CODE_LENGTH) {
        throw ApiFailure.validation("serverAuthCode is over ${KvizicApi.Limits.MAX_SERVER_AUTH_CODE_LENGTH} characters")
    }
    if (code.any { it !in '!'..'~' }) throw ApiFailure.validation("serverAuthCode holds a character no code has")
    return code
}
