package io.ntole.kvizic.core.network.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.auth.PlayGamesSignInRequest
import io.ntole.kvizic.core.auth.RefreshRequest
import io.ntole.kvizic.core.auth.SessionDto
import io.ntole.kvizic.core.network.refreshTimeout

/**
 * The session's endpoints. [guest] and [refresh] need no session: a guest has no credential yet, and a
 * refresh carries its own in the body. [playGames] and [logOut] go with the session's bearer, as any
 * other call does.
 */
public class AuthApi(
    private val client: HttpClient,
) {
    /** Mints a brand-new server-issued guest player, with nothing asked of the player. */
    public suspend fun guest(): SessionDto = client.post(KvizicApi.Paths.AUTH_GUEST).body()

    /**
     * Spends [refreshToken], which the server rotates as it answers, so this gets the refresh's longer
     * timeout, as the bearer provider's own refresh does (`KvizicHttpClient.REFRESH_TIMEOUT`).
     */
    public suspend fun refresh(refreshToken: String): SessionDto =
        client
            .post(KvizicApi.Paths.AUTH_REFRESH) {
                refreshTimeout()
                setBody(RefreshRequest(refreshToken))
            }.body()

    /**
     * Signs in with Google Play Games Services, [request] carrying the one-time server auth code Play Games
     * gave the app, answered with a new session, this device's own, of the player Play Games names: the
     * session player, now linked, or the one it was linked to already. Sent with the session's bearer, as
     * any call is: an expired access token is a 401 before the code goes anywhere, so the plugin's refresh
     * and retry send it still unspent.
     */
    public suspend fun playGames(request: PlayGamesSignInRequest): SessionDto =
        client.post(KvizicApi.Paths.AUTH_PLAY_GAMES) { setBody(request) }.body()

    /**
     * Ends the session the bearer names, this device's and no other. Answered 204, as it is for a session
     * already ended. An expired access token is refreshed first, as for any call.
     */
    public suspend fun logOut() {
        client.post(KvizicApi.Paths.AUTH_LOGOUT)
    }
}
