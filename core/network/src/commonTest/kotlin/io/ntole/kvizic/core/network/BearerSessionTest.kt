package io.ntole.kvizic.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.auth.RefreshRequest
import io.ntole.kvizic.core.auth.SessionDto
import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.network.api.PlayerApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Which credentials a request goes out with, as the session in [SessionStore] changes. */
class BearerSessionTest {
    @Test
    fun `a replaced session is sent on the very next request`() =
        runTest {
            val store = storeHolding(session("a"))
            val engine = MockEngine { respondProfile() }
            val api = PlayerApi(KvizicHttpClient.create(BASE_URL, store, engine))

            api.me()
            // What minting a fresh guest does: the data layer writes the new session to the store.
            store.write(session("b"))
            api.me()

            assertEquals(listOf("Bearer access-a", "Bearer access-b"), engine.authorizationHeaders())
        }

    @Test
    fun `a cleared session sends no bearer at all`() =
        runTest {
            val store = storeHolding(session("a"))
            val engine = MockEngine { respondProfile() }
            val api = PlayerApi(KvizicHttpClient.create(BASE_URL, store, engine))

            api.me()
            store.clear()
            api.me()

            assertEquals(listOf("Bearer access-a", null), engine.authorizationHeaders())
        }

    @Test
    fun `an expired access token is refreshed and the call retried with the new one`() =
        runTest {
            val store = storeHolding(session("a"))
            val engine =
                MockEngine { request ->
                    when {
                        request.url.encodedPath == KvizicApi.Paths.AUTH_REFRESH -> respondSession(session("a2"))
                        request.headers[HttpHeaders.Authorization] == "Bearer access-a2" -> respondProfile()
                        else -> respondErrorDto(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED)
                    }
                }

            PlayerApi(KvizicHttpClient.create(BASE_URL, store, engine)).me()

            assertEquals(session("a2"), store.read())
            // The refresh itself carries no bearer: its credential is the refresh token in the body.
            assertEquals(listOf("Bearer access-a", null, "Bearer access-a2"), engine.authorizationHeaders())
        }

    @Test
    fun `calls rejected together share one refresh`() =
        runTest {
            // Refresh tokens rotate on every use, so each extra refresh is a
            // needless rotation — and a lost race over one would kill the session.
            val store = storeHolding(session("a"))
            val arrivals = Mutex()
            var rejectedSoFar = 0
            val bothRejected = CompletableDeferred<Unit>()
            val engine =
                MockEngine { request ->
                    when {
                        request.url.encodedPath == KvizicApi.Paths.AUTH_REFRESH -> {
                            respondSession(session("a2"))
                        }

                        request.headers[HttpHeaders.Authorization] == "Bearer access-a2" -> {
                            respondProfile()
                        }

                        else -> {
                            // Hold the first rejection until the second call has gone out as "a" too.
                            arrivals.withLock { if (++rejectedSoFar == 2) bothRejected.complete(Unit) }
                            bothRejected.await()
                            respondErrorDto(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED)
                        }
                    }
                }
            val api = PlayerApi(KvizicHttpClient.create(BASE_URL, store, engine))

            awaitAll(async { api.me() }, async { api.me() })

            assertEquals(1, engine.requestHistory.count { it.url.encodedPath == KvizicApi.Paths.AUTH_REFRESH })
            assertEquals(session("a2"), store.read())
        }

    @Test
    fun `a refresh outlives the cancelled call that started it`() =
        runTest {
            // The server rotates the refresh token the moment it answers. Abandoning the refresh
            // then would leave in the store a refresh token the server takes once more at most.
            val store = storeHolding(session("a"))
            val refreshArrived = CompletableDeferred<Unit>()
            val answerRefresh = CompletableDeferred<Unit>()
            val engine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        KvizicApi.Paths.AUTH_REFRESH -> {
                            refreshArrived.complete(Unit)
                            answerRefresh.await()
                            respondSession(session("a2"))
                        }

                        else -> {
                            respondErrorDto(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED)
                        }
                    }
                }
            val api = PlayerApi(KvizicHttpClient.create(BASE_URL, store, engine))

            val call = launch { api.me() }
            refreshArrived.await()
            call.cancel()
            answerRefresh.complete(Unit)
            call.join()

            assertEquals(session("a2"), store.read())
        }

    @Test
    fun `a session replaced during a refresh is not overwritten by it`() =
        runTest {
            val store = storeHolding(session("a"))
            val refreshArrived = CompletableDeferred<Unit>()
            val answerRefresh = CompletableDeferred<Unit>()
            val engine =
                MockEngine { request ->
                    when {
                        request.url.encodedPath == KvizicApi.Paths.AUTH_REFRESH -> {
                            refreshArrived.complete(Unit)
                            answerRefresh.await()
                            respondSession(session("a2"))
                        }

                        request.headers[HttpHeaders.Authorization] == "Bearer access-b" -> {
                            respondProfile()
                        }

                        else -> {
                            respondErrorDto(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED)
                        }
                    }
                }
            val api = PlayerApi(KvizicHttpClient.create(BASE_URL, store, engine))

            val call = async { api.me() }
            refreshArrived.await()
            // A deliberate session change lands while the refresh for "a" is still in flight.
            store.write(session("b"))
            answerRefresh.complete(Unit)
            call.await()

            assertEquals(session("b"), store.read())
            assertEquals(listOf("Bearer access-a", null, "Bearer access-b"), engine.authorizationHeaders())
        }

    @Test
    fun `a refresh token another client spent first is not taken for a dead session`() =
        runTest {
            // Two browser tabs share one store. The other tab's refresh with "refresh-a" won; the
            // server refuses this one, and by the time it answers the store holds the rotation.
            val store = storeHolding(session("a"))
            val engine =
                MockEngine { request ->
                    when {
                        request.url.encodedPath == KvizicApi.Paths.AUTH_REFRESH -> {
                            store.write(session("a2"))
                            respondErrorDto(HttpStatusCode.Unauthorized, ErrorCode.INVALID_REFRESH_TOKEN)
                        }

                        request.headers[HttpHeaders.Authorization] == "Bearer access-a2" -> {
                            respondProfile()
                        }

                        else -> {
                            respondErrorDto(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED)
                        }
                    }
                }

            PlayerApi(KvizicHttpClient.create(BASE_URL, store, engine)).me()

            assertEquals(session("a2"), store.read())
            assertEquals(listOf("Bearer access-a", null, "Bearer access-a2"), engine.authorizationHeaders())
        }

    @Test
    fun `a refresh another client's refresh of the same session overtook is settled on the latest rotation`() =
        runTest {
            // Two browser tabs share one store and refresh "refresh-a" at once. The server lets both
            // through (its grace): the other tab's answer lands in the store
            // first, and this one is answered too. Which of the two is the player's current token is
            // unknown here, and the other is good for one refresh only, so one more refresh as the
            // stored session settles it on the latest rotation.
            val store = storeHolding(session("a"))
            val engine =
                MockEngine { request ->
                    when {
                        request.url.encodedPath == KvizicApi.Paths.AUTH_REFRESH -> {
                            when (request.presentedRefreshToken()) {
                                "refresh-a" -> {
                                    store.write(rotation("a", 2))
                                    respondSession(rotation("a", 3))
                                }

                                "refresh-a2" -> {
                                    respondSession(rotation("a", 4))
                                }

                                else -> {
                                    respondErrorDto(HttpStatusCode.Unauthorized, ErrorCode.INVALID_REFRESH_TOKEN)
                                }
                            }
                        }

                        request.headers[HttpHeaders.Authorization] == "Bearer access-a4" -> {
                            respondProfile()
                        }

                        else -> {
                            respondErrorDto(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED)
                        }
                    }
                }

            PlayerApi(KvizicHttpClient.create(BASE_URL, store, engine)).me()

            assertEquals(rotation("a", 4), store.read())
            assertEquals(listOf("refresh-a", "refresh-a2"), engine.presentedRefreshTokens())
            assertEquals(listOf("Bearer access-a", null, null, "Bearer access-a4"), engine.authorizationHeaders())
        }

    @Test
    fun `a settling refresh overtaken in its turn is not settled again`() =
        runTest {
            // Every refresh holds the bearer provider's lock, uncancellably, so the settling refresh
            // is made once at most: overtaken again, the call is retried as whatever is stored.
            val store = storeHolding(session("a"))
            val engine =
                MockEngine { request ->
                    when {
                        request.url.encodedPath == KvizicApi.Paths.AUTH_REFRESH -> {
                            val presented = request.presentedRefreshToken()
                            val overtakenBy = if (presented == "refresh-a") 2 else 5
                            store.write(rotation("a", overtakenBy))
                            respondSession(rotation("a", overtakenBy + 1))
                        }

                        request.headers[HttpHeaders.Authorization] == "Bearer access-a5" -> {
                            respondProfile()
                        }

                        else -> {
                            respondErrorDto(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED)
                        }
                    }
                }

            PlayerApi(KvizicHttpClient.create(BASE_URL, store, engine)).me()

            assertEquals(listOf("refresh-a", "refresh-a2"), engine.presentedRefreshTokens())
            assertEquals(rotation("a", 5), store.read())
        }

    /** [playerId]'s session after its refresh token has rotated to its [n]th: same player, new tokens. */
    private fun rotation(
        playerId: String,
        n: Int,
    ): SessionDto = session(playerId).copy(accessToken = "access-$playerId$n", refreshToken = "refresh-$playerId$n")

    private suspend fun HttpRequestData.presentedRefreshToken(): String =
        KvizicJson.decodeFromString<RefreshRequest>(body.toByteArray().decodeToString()).refreshToken

    private suspend fun MockEngine.presentedRefreshTokens(): List<String> =
        requestHistory.filter { it.url.encodedPath == KvizicApi.Paths.AUTH_REFRESH }.map { it.presentedRefreshToken() }

    private fun MockEngine.authorizationHeaders(): List<String?> =
        requestHistory.map { it.headers[HttpHeaders.Authorization] }
}
