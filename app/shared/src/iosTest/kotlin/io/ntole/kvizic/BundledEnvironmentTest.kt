package io.ntole.kvizic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the iOS app reads from its Info.plist. The test binary's bundle has no `KVIZIC_ENV` key, as an
 * app built without the setting would not, so the key's value is given through [environmentNameFrom].
 * The app's own Info.plist, and the value `Config.xcconfig` gives it, are checked only by building the
 * app.
 */
class BundledEnvironmentTest {
    @Test
    fun `a bundle without the key names no environment`() {
        assertNull(bundledEnvironmentName())
    }

    @Test
    fun `the name is the KVIZIC_ENV key's value`() {
        val plist = mapOf<String, Any>("KVIZIC_ENV" to "dev", "KVIZIC_ENVIRONMENT" to "prod")

        assertEquals("dev", environmentNameFrom { key -> plist[key] })
    }

    @Test
    fun `no value for the key names no environment`() {
        assertNull(environmentNameFrom { key -> if (key == "KVIZIC_ENV") null else "prod" })
    }

    @Test
    fun `a value that is not a string is handed on as its text`() {
        assertEquals("1", environmentNameFrom { 1 })
    }
}
