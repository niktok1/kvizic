package io.ntole.kvizic.core.network

import io.ntole.kvizic.core.api.KvizicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The build a request names, so a server can refuse one too old. */
class ClientBuildTest {
    @Test
    fun `a build number is this platform's build`() {
        val build = ClientBuild.of(10203)

        assertEquals(10203, build?.number)
        val platforms =
            setOf(
                KvizicApi.ClientPlatform.ANDROID,
                KvizicApi.ClientPlatform.IOS,
                KvizicApi.ClientPlatform.WEB,
                KvizicApi.ClientPlatform.DESKTOP,
            )
        assertTrue(build?.platform in platforms, "${build?.platform}")
    }

    @Test
    fun `no build number or none of at least 1 is no build`() {
        assertNull(ClientBuild.of(null))
        assertNull(ClientBuild.of(0))
        assertNull(ClientBuild.of(-1))
    }

    @Test
    fun `a platform the server does not know is refused`() {
        assertFailsWith<IllegalArgumentException> { ClientBuild("windows", 10000) }
    }
}
