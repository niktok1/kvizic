package io.ntole.kvizic.server.plugins

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.server.config.ServerConfig
import io.ntole.kvizic.server.runTestServer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What `/health` says: Render reads its status, the prod deploy's workflow the commit serving. */
class HealthTest {
    @Test
    fun `health names the commit Render built, and says nothing of one off Render`() {
        assertEquals(
            "abc123",
            ServerConfig
                .fromEnvironment {
                    if (it ==
                        "RENDER_GIT_COMMIT"
                    ) {
                        " abc123 "
                    } else {
                        null
                    }
                }.commit,
        )
        assertNull(ServerConfig.fromEnvironment { null }.commit)

        runTestServer("health-commit", { it.copy(commit = "abc123") }) { client, _ ->
            val response = client.get(KvizicApi.Paths.HEALTH)
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<JsonObject>()
            assertEquals("ok", body.getValue("status").jsonPrimitive.content)
            assertEquals("abc123", body.getValue("commit").jsonPrimitive.content)
        }
        runTestServer("health-no-commit") { client, _ ->
            assertEquals(setOf("status"), client.get(KvizicApi.Paths.HEALTH).body<JsonObject>().keys)
        }
    }
}
