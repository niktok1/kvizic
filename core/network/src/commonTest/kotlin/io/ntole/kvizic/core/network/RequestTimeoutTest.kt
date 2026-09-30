package io.ntole.kvizic.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.network.KvizicHttpClient.CONNECT_TIMEOUT
import io.ntole.kvizic.core.network.KvizicHttpClient.REFRESH_TIMEOUT
import io.ntole.kvizic.core.network.KvizicHttpClient.REQUEST_TIMEOUT
import io.ntole.kvizic.core.network.api.AuthApi
import io.ntole.kvizic.core.network.api.PlayerApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * How long a call may take. Every engine here runs on the test's scheduler, so the server's delays
 * and HttpTimeout's own timer both run in virtual time, and [currentTime] says when a call gave up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestTimeoutTest {
    @Test
    fun `a call the server never answers fails at the request timeout`() =
        runTest {
            val silent =
                engine {
                    delay(1.hours)
                    respondProfile()
                }

            // Not an ApiException: nothing answered, and runApi calls this NETWORK.
            assertFailsWith<HttpRequestTimeoutException> { playerApi(silent).me() }
            assertEquals(REQUEST_TIMEOUT.inWholeMilliseconds, currentTime)
        }

    @Test
    fun `an answer as slow as a cold start still arrives`() =
        runTest {
            // Render's free tier can take most of a minute to wake, and the call that
            // woke it should get its answer.
            val coldStart =
                engine {
                    delay(50.seconds)
                    respondProfile()
                }

            playerApi(coldStart).me()
        }

    @Test
    fun `every call tells the engine its connect and socket timeouts`() =
        runTest {
            // The engines enforce both, and each has its own shorter default: OkHttp waits 10 s for
            // a packet, CIO 5 s for a connection. A retry after a refresh must carry them too.
            val engine = engine(expiredAccessToken())

            playerApi(engine).me()

            val calls = engine.requestHistory.filter { it.url.encodedPath != KvizicApi.Paths.AUTH_REFRESH }
            assertEquals(2, calls.size)
            calls.forEach { call -> assertEquals(ordinaryTimeouts, call.timeouts()) }
        }

    @Test
    fun `a refresh gets the longer timeout and keeps the ordinary connect timeout`() =
        runTest {
            val engine = engine(expiredAccessToken())

            playerApi(engine).me()

            val refresh = engine.requestHistory.single { it.url.encodedPath == KvizicApi.Paths.AUTH_REFRESH }
            assertEquals(refreshTimeouts, refresh.timeouts())
        }

    @Test
    fun `a refresh slower than the request timeout still lands`() =
        runTest {
            // The server rotates the refresh token as it answers. Cut short at the
            // ordinary bound, this refresh would leave the rotated-out token stored, which the server
            // takes once more at most, and not at all if it was already the previous one.
            val store = storeHolding(session("a"))
            val engine =
                engine(
                    expiredAccessToken {
                        delay(REQUEST_TIMEOUT + 30.seconds)
                    },
                )

            // The call that asked for it has outlived its own bound by then, so it still fails, as
            // a timeout: NETWORK, never a cancellation it did not ask for.
            assertFailsWith<HttpRequestTimeoutException> { PlayerApi(client(store, engine)).me() }
            assertEquals(session("a2"), store.read())
        }

    @Test
    fun `a refresh that never ends is still bounded`() =
        runTest {
            // Every call rejected meanwhile waits behind the refresh, uncancellably, so without a
            // bound a dead connection would hold them all until the app is killed.
            val store = storeHolding(session("a"))
            val engine =
                engine(
                    expiredAccessToken {
                        delay(1.hours)
                    },
                )

            assertFailsWith<HttpRequestTimeoutException> { PlayerApi(client(store, engine)).me() }
            assertEquals(REFRESH_TIMEOUT.inWholeMilliseconds, currentTime)
            assertEquals(session("a"), store.read())
        }

    @Test
    fun `AuthApi's refresh gets the longer timeout too`() =
        runTest {
            val engine = engine { respondSession(session("a2")) }

            AuthApi(client(storeHolding(null), engine)).refresh("refresh-a")

            assertEquals(refreshTimeouts, engine.requestHistory.single().timeouts())
        }

    private val ordinaryTimeouts =
        HttpTimeoutConfig(
            requestTimeoutMillis = REQUEST_TIMEOUT.inWholeMilliseconds,
            connectTimeoutMillis = CONNECT_TIMEOUT.inWholeMilliseconds,
            socketTimeoutMillis = REQUEST_TIMEOUT.inWholeMilliseconds,
        )

    private val refreshTimeouts =
        HttpTimeoutConfig(
            requestTimeoutMillis = REFRESH_TIMEOUT.inWholeMilliseconds,
            connectTimeoutMillis = CONNECT_TIMEOUT.inWholeMilliseconds,
            socketTimeoutMillis = REFRESH_TIMEOUT.inWholeMilliseconds,
        )

    private fun HttpRequestData.timeouts(): HttpTimeoutConfig? = getCapabilityOrNull(HttpTimeoutCapability)

    /** Rejects the stored access token, so a call goes 401, refresh, retry. [beforeRefresh] delays the refresh. */
    private fun expiredAccessToken(beforeRefresh: suspend () -> Unit = {}): MockRequestHandler =
        { request ->
            when {
                request.url.encodedPath == KvizicApi.Paths.AUTH_REFRESH -> {
                    beforeRefresh()
                    respondSession(session("a2"))
                }

                request.headers[HttpHeaders.Authorization] == "Bearer access-a2" -> {
                    respondProfile()
                }

                else -> {
                    respondErrorDto(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED)
                }
            }
        }

    /** A [MockEngine] on the test's scheduler, so its delays and HttpTimeout's run in virtual time. */
    private fun TestScope.engine(handler: MockRequestHandler): MockEngine =
        MockEngine(
            MockEngineConfig().apply {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler(handler)
            },
        )

    private fun client(
        store: SessionStore,
        engine: MockEngine,
    ) = KvizicHttpClient.create(BASE_URL, store, engine)

    private fun playerApi(engine: MockEngine): PlayerApi = PlayerApi(client(storeHolding(session("a")), engine))
}
