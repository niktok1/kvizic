package io.ntole.kvizic.core.network.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.auth.PlayGamesSignInRequest
import io.ntole.kvizic.core.auth.RefreshRequest
import io.ntole.kvizic.core.network.BASE_URL
import io.ntole.kvizic.core.network.KvizicHttpClient
import io.ntole.kvizic.core.network.KvizicJson
import io.ntole.kvizic.core.network.jsonHeaders
import io.ntole.kvizic.core.network.respondSession
import io.ntole.kvizic.core.network.session
import io.ntole.kvizic.core.network.storeHolding
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthApiTest {
    /** Each request as it arrived: its path and its `Authorization` header. */
    private val requests = mutableListOf<Pair<String, String?>>()

    @Test
    fun `a guest's mint is read as its session whatever else the answer carries`() =
        runTest {
            val minted =
                """{"playerId":"g1","accessToken":"access-g1","refreshToken":"refresh-g1",""" +
                    """"accessTokenExpiresInSeconds":900,"somethingFromTheFuture":true}"""
            val engine = MockEngine { respond(minted, HttpStatusCode.OK, jsonHeaders) }

            val session = AuthApi(KvizicHttpClient.create(BASE_URL, storeHolding(null), engine)).guest()

            assertEquals(session("g1"), session)
        }

    @Test
    fun `a refresh carries its token in the body and no bearer`() =
        runTest {
            var sent: RefreshRequest? = null
            val engine =
                MockEngine { request ->
                    requests += request.url.encodedPath to request.headers[HttpHeaders.Authorization]
                    sent = KvizicJson.decodeFromString<RefreshRequest>(request.body.toByteArray().decodeToString())
                    respondSession(session("a2"))
                }

            val refreshed = AuthApi(KvizicHttpClient.create(BASE_URL, storeHolding(null), engine)).refresh("refresh-a")

            assertEquals(session("a2"), refreshed)
            assertEquals(RefreshRequest("refresh-a"), sent)
            assertEquals(listOf<Pair<String, String?>>(KvizicApi.Paths.AUTH_REFRESH to null), requests)
        }

    /** The bearer links an unlinked Play Games player to the guest playing, so it goes with the code. */
    @Test
    fun `a Play Games sign-in goes with the session's bearer and is read as the new session`() =
        runTest {
            var sent: PlayGamesSignInRequest? = null
            val engine =
                MockEngine { request ->
                    requests += request.url.encodedPath to request.headers[HttpHeaders.Authorization]
                    val body = request.body.toByteArray().decodeToString()
                    sent = KvizicJson.decodeFromString<PlayGamesSignInRequest>(body)
                    respondSession(session("linked"))
                }

            val signedIn =
                AuthApi(KvizicHttpClient.create(BASE_URL, storeHolding(session("g1")), engine))
                    .playGames(PlayGamesSignInRequest("code-1"))

            assertEquals(session("linked"), signedIn)
            assertEquals(PlayGamesSignInRequest("code-1"), sent)
            assertEquals(
                listOf<Pair<String, String?>>(KvizicApi.Paths.AUTH_PLAY_GAMES to "Bearer access-g1"),
                requests,
            )
        }

    @Test
    fun `a logout goes with the session's bearer and takes a 204`() =
        runTest {
            val engine =
                MockEngine { request ->
                    requests += request.url.encodedPath to request.headers[HttpHeaders.Authorization]
                    respond("", HttpStatusCode.NoContent)
                }

            AuthApi(KvizicHttpClient.create(BASE_URL, storeHolding(session("bob")), engine)).logOut()

            assertEquals(listOf<Pair<String, String?>>(KvizicApi.Paths.AUTH_LOGOUT to "Bearer access-bob"), requests)
        }

    /** The code is a one-time credential, so it never shows in a log line that prints the request. */
    @Test
    fun `a Play Games code never shows in the request's text`() {
        assertEquals("PlayGamesSignInRequest(serverAuthCode=***)", PlayGamesSignInRequest("code-1").toString())
    }
}
