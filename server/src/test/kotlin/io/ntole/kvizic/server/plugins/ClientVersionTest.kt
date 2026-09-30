package io.ntole.kvizic.server.plugins

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.error.ErrorDto
import io.ntole.kvizic.server.NO_PRACTICAL_LIMIT
import io.ntole.kvizic.server.config.RequestBudget
import io.ntole.kvizic.server.db.Players
import io.ntole.kvizic.server.db.inTransaction
import io.ntole.kvizic.server.db.serverPool
import io.ntole.kvizic.server.runTestServer
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/** A build older than its platform's minimum is told to update. */
class ClientVersionTest {
    @Test
    fun `a build older than its platform's minimum is 426 on every path but health`() =
        runTestServer("client-version-old", { it.copy(minClientVersions = ANDROID_5) }) { client, database ->
            assertUpgradeRequired(
                client.post(KvizicApi.Paths.AUTH_GUEST) { build(KvizicApi.ClientPlatform.ANDROID, "4") },
            )
            assertUpgradeRequired(client.get(KvizicApi.Paths.TOPICS) { build(KvizicApi.ClientPlatform.ANDROID, "1") })
            // Trimmed and in any case, as a header's value may come.
            assertUpgradeRequired(client.get(KvizicApi.Paths.TOPICS) { build(" Android ", " 4 ") })

            val health = client.get(KvizicApi.Paths.HEALTH) { build(KvizicApi.ClientPlatform.ANDROID, "1") }
            assertEquals(HttpStatusCode.OK, health.status, "Render's check is never refused")
            val players = database.serverPool().use { pool -> pool.inTransaction { Players.selectAll().count() } }
            assertEquals(0L, players, "the refused mint minted nobody")
        }

    @Test
    fun `the minimum build and newer pass and so does a request that says nothing of its build`() =
        runTestServer("client-version-current", { it.copy(minClientVersions = ANDROID_5) }) { client, _ ->
            val passing: List<Pair<String, HttpRequestBuilder.() -> Unit>> =
                listOf(
                    "the minimum" to { build(KvizicApi.ClientPlatform.ANDROID, "5") },
                    "a newer build" to { build(KvizicApi.ClientPlatform.ANDROID, "6") },
                    "no headers, as the moderation app sends" to {},
                    "a platform alone" to
                        { header(KvizicApi.Headers.CLIENT_PLATFORM, KvizicApi.ClientPlatform.ANDROID) },
                    "a platform with no minimum" to { build(KvizicApi.ClientPlatform.IOS, "1") },
                    "a platform nobody named" to { build("toaster", "1") },
                    "a version that is no whole number" to { build(KvizicApi.ClientPlatform.ANDROID, "4.9") },
                )

            passing.forEach { (case, headers) ->
                assertEquals(HttpStatusCode.OK, client.post(KvizicApi.Paths.AUTH_GUEST) { headers() }.status, case)
            }
        }

    @Test
    fun `an old build is refused before its rate limit spends anything`() =
        runTestServer(
            "client-version-before-limits",
            { config ->
                config.copy(
                    minClientVersions = ANDROID_5,
                    rateLimits = NO_PRACTICAL_LIMIT.copy(guests = RequestBudget(requests = 1, per = 1.minutes)),
                )
            },
        ) { client, _ ->
            repeat(3) { assertUpgradeRequired(client.mint("4")) }

            assertEquals(HttpStatusCode.OK, client.mint("5").status, "the one mint the budget holds is left")
            assertEquals(HttpStatusCode.TooManyRequests, client.mint("5").status)
        }

    @Test
    fun `with no minimum set every build passes`() =
        runTestServer("client-version-none") { client, _ ->
            assertEquals(HttpStatusCode.OK, client.mint("1").status)
        }

    private suspend fun assertUpgradeRequired(response: HttpResponse) {
        assertEquals(HttpStatusCode.UpgradeRequired, response.status)
        assertEquals(ErrorCode.UPGRADE_REQUIRED, response.body<ErrorDto>().code)
    }

    private suspend fun HttpClient.mint(version: String): HttpResponse =
        post(KvizicApi.Paths.AUTH_GUEST) { build(KvizicApi.ClientPlatform.ANDROID, version) }

    private fun HttpRequestBuilder.build(
        platform: String,
        version: String,
    ) {
        header(KvizicApi.Headers.CLIENT_PLATFORM, platform)
        header(KvizicApi.Headers.CLIENT_VERSION, version)
    }

    private companion object {
        val ANDROID_5 = mapOf(KvizicApi.ClientPlatform.ANDROID to 5)
    }
}
