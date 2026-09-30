package io.ntole.kvizic.server.plugins

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.request.contentLength
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeStringUtf8
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.auth.RefreshRequest
import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.error.ErrorDto
import io.ntole.kvizic.server.auth.TokenService
import io.ntole.kvizic.server.config.ServerConfig
import io.ntole.kvizic.server.db.Players
import io.ntole.kvizic.server.db.inTransaction
import io.ntole.kvizic.server.db.serverPool
import io.ntole.kvizic.server.runTestServer
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.test.Test
import kotlin.test.assertEquals

/** No request body over 64 KiB is read, whether or not it says how long it is. */
class RequestBodyCapTest {
    @Test
    fun `a body whose Content-Length is over the cap is 413 before the route runs`() =
        runTestServer("body-cap-declared") { client, database ->
            // The mint reads no body, so only the declared length can refuse it: it would mint a guest.
            val refused =
                client.post(KvizicApi.Paths.AUTH_GUEST) {
                    contentType(ContentType.Application.Json)
                    setBody(" ".repeat(CAP + 1))
                }

            assertTooLarge(refused)
            val players = database.serverPool().use { pool -> pool.inTransaction { Players.selectAll().count() } }
            assertEquals(0L, players, "no guest was minted")
        }

    @Test
    fun `a body of the cap is read and one byte more is 413`() =
        runTestServer("body-cap-edge") { client, _ ->
            // Read, decoded, and refused for its token alone: the rules, not the cap.
            val atTheCap = client.refresh(refreshOfLength(CAP))
            assertEquals(HttpStatusCode.Unauthorized, atTheCap.status)
            assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, atTheCap.body<ErrorDto>().code)

            assertTooLarge(client.refresh(refreshOfLength(CAP + 1)))
        }

    @Test
    fun `a body that says no length is counted as it is read and 413 past the cap`() =
        testApplication {
            val config = ServerConfig.fromEnvironment { null }
            application {
                installPlugins(config, TokenService(config))
                routing {
                    // Answers the length the request declared, once its body is read.
                    post("/probe") {
                        val declared = call.request.contentLength()
                        call.receiveOrReject<RefreshRequest>("probe")
                        call.respondText("declared $declared")
                    }
                }
            }
            val client = createClient { install(ContentNegotiation) { json(ServerJson) } }

            suspend fun probe(body: String): HttpResponse = client.post("/probe") { setBody(Unmeasured(body)) }

            val small = probe("""{"refreshToken":"r1"}""")
            assertEquals("declared null", small.bodyAsText(), "said no length, and was read whole")
            assertTooLarge(probe(refreshOfLength(CAP + 1)))
            assertTooLarge(probe(refreshOfLength(4 * CAP)))
        }

    private suspend fun assertTooLarge(response: HttpResponse) {
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, response.body<ErrorDto>().code)
    }

    /** A refresh request of exactly [length] bytes, all ASCII, padded out in its token. */
    private fun refreshOfLength(length: Int): String {
        val frame = """{"refreshToken":""}"""
        val padded = frame.replace("\"refreshToken\":\"\"", "\"refreshToken\":\"${"a".repeat(length - frame.length)}\"")
        check(padded.length == length) { "padded to ${padded.length}, not $length" }
        return padded
    }

    /** Sends [body] as a refresh, its length declared. */
    private suspend fun HttpClient.refresh(body: String): HttpResponse =
        post(KvizicApi.Paths.AUTH_REFRESH) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    /** [text] as JSON, written with no Content-Length, as a chunked body is. */
    private class Unmeasured(
        private val text: String,
    ) : OutgoingContent.WriteChannelContent() {
        override val contentType: ContentType = ContentType.Application.Json
        override val contentLength: Long? = null

        override suspend fun writeTo(channel: ByteWriteChannel) {
            channel.writeStringUtf8(text)
        }
    }

    private companion object {
        val CAP = MAX_REQUEST_BODY_BYTES.toInt()
    }
}
