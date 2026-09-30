package io.ntole.kvizic.core.network.environment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** What a build may name its environment, and where each environment is. */
class KvizicEnvironmentTest {
    @Test
    fun `each name parses in any case and trimmed`() {
        mapOf(
            KvizicEnvironment.LOCAL to listOf("local", "LOCAL", "Local", " local\n"),
            KvizicEnvironment.DEV to listOf("dev", "DEV", "Dev", "\tdev "),
            KvizicEnvironment.PROD to listOf("prod", "PROD", "pRoD", " prod"),
        ).forEach { (environment, names) ->
            names.forEach { name -> assertEquals(environment, KvizicEnvironment.parse(name), "\"$name\"") }
        }
    }

    @Test
    fun `no name or a blank one is local`() {
        listOf(null, "", "   ", "\n").forEach { name ->
            assertEquals(KvizicEnvironment.LOCAL, KvizicEnvironment.parse(name), "\"$name\"")
        }
    }

    @Test
    fun `any other name fails and says what it was`() {
        listOf("staging", "production", "development", "loc", "de v", "prod!", "0").forEach { name ->
            val failure = assertFailsWith<IllegalArgumentException>(name) { KvizicEnvironment.parse(name) }
            assertTrue("\"$name\"" in failure.message.orEmpty(), failure.message)
        }
    }

    @Test
    fun `the deployed environments are dev on Render's name and prod on its own domain over https`() {
        assertEquals("https://kvizic-server-dev.onrender.com", KvizicEnvironment.DEV.apiBaseUrl)
        assertEquals("https://kvizic-api.ntole.com", KvizicEnvironment.PROD.apiBaseUrl)
    }

    /** The same on every platform: a phone or an emulator reaches it through `adb reverse`. */
    @Test
    fun `local is plain http to localhost on the server's default port`() {
        assertEquals("http://localhost:8080", KvizicEnvironment.LOCAL.apiBaseUrl)
    }

    @Test
    fun `each environment has a name of its own`() {
        assertEquals(listOf("Local", "Dev", "Prod"), KvizicEnvironment.entries.map { it.displayName })
    }
}
