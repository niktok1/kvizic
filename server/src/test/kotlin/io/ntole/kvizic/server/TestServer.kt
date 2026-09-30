package io.ntole.kvizic.server

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.post
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.auth.SessionDto
import io.ntole.kvizic.server.config.ServerConfig
import kotlinx.serialization.json.Json

/** The admin token a [runTestServer] server is moderated with. */
internal const val FLOW_ADMIN_TOKEN = "flow-admin-token-0123456789abcdef"

/**
 * The whole server, on its own database [name] names, moderated with [FLOW_ADMIN_TOKEN], with budgets no
 * test comes near and the game's fast timings, and whatever [configure] changes. [block] gets a client
 * that decodes the contract and opens sockets, and the database, for a test that reaches into it.
 */
internal fun runTestServer(
    name: String,
    configure: (ServerConfig) -> ServerConfig = { it },
    googleEngine: (() -> HttpClientEngine)? = null,
    parts: GameParts = GameParts(),
    block: suspend ApplicationTestBuilder.(client: HttpClient, database: TestDatabaseSettings) -> Unit,
) = testApplication {
    val database = testDatabaseFor(name)
    val config = configure(testServerConfig(database, adminToken = FLOW_ADMIN_TOKEN))

    application {
        if (googleEngine == null) kvizicModule(config, parts = parts) else kvizicModule(config, googleEngine, parts)
    }

    val client =
        createClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(WebSockets)
        }
    block(client, database)
}

/** A fresh guest's session. */
internal suspend fun HttpClient.mintGuest(): SessionDto = post(KvizicApi.Paths.AUTH_GUEST).body()
