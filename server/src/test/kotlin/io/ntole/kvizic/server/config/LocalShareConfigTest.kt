package io.ntole.kvizic.server.config

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** `LOCAL_SHARE`: the part of a one-country room's game taken from its country's questions. */
class LocalShareConfigTest {
    private fun share(raw: String?) = ServerConfig.fromEnvironment(mapOf("LOCAL_SHARE" to raw)::get).localShare

    @Test
    fun `unset or blank is a fifth`() {
        listOf(null, "", "  ").forEach { unset -> assertEquals(0.2, share(unset), "\"$unset\"") }
        assertEquals(0.2, ServerConfig.fromEnvironment { null }.localShare)
    }

    @Test
    fun `a number from 0 to 1 is the share, and 0 turns it off`() {
        mapOf("0" to 0.0, "1" to 1.0, " 0.35 " to 0.35, "0.25" to 0.25, ".1" to 0.1).forEach { (raw, expected) ->
            assertEquals(expected, share(raw), raw)
        }
    }

    @Test
    fun `anything else refuses to start, naming the variable`() {
        listOf("1.5", "-0.1", "0,2", "a fifth", "20%", "NaN", "Infinity").forEach { raw ->
            val refusal = assertFailsWith<IllegalArgumentException>(raw) { share(raw) }
            assertContains(refusal.message.orEmpty(), "LOCAL_SHARE", message = raw)
        }
    }
}
