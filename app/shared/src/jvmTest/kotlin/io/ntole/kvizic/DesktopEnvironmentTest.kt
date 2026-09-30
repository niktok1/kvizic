package io.ntole.kvizic

import io.ntole.kvizic.analytics.AnalyticsSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the desktop client reads of its process as it starts, and hands `initKoin`. */
class DesktopEnvironmentTest {
    @Test
    fun `the environment is KVIZIC_ENV's as it is and none when unset`() {
        assertEquals("dev", desktopEnvironmentName(mapOf("KVIZIC_ENV" to "dev")))
        assertEquals(" Prod ", desktopEnvironmentName(mapOf("KVIZIC_ENV" to " Prod ")), "initKoin trims it")
        assertNull(desktopEnvironmentName(mapOf("ENV" to "dev")), "no other variable names it")
    }

    /** Each window side by side a player of its own, and its title says which. */
    @Test
    fun `the profile is KVIZIC_PROFILE's and the window's title names it`() {
        assertEquals("ana", desktopProfileName(mapOf("KVIZIC_PROFILE" to "ana")))
        assertNull(desktopProfileName(emptyMap()))

        assertEquals("Kvizić · ana", desktopWindowTitle("ana"))
        assertEquals("Kvizić", desktopWindowTitle(null))
        assertEquals("Kvizić", desktopWindowTitle("  "))
    }

    @Test
    fun `the analytics are KVIZIC_POSTHOG_KEY's and KVIZIC_POSTHOG_HOST's with the app's version`() {
        val variables = mapOf("KVIZIC_POSTHOG_KEY" to "phc_key", "KVIZIC_POSTHOG_HOST" to "us.i.posthog.com")

        assertEquals(
            AnalyticsSettings("phc_key", "us.i.posthog.com", "0.1.0"),
            desktopAnalyticsSettings(variables, mapOf("kvizic.app.version" to "0.1.0")),
        )
        assertEquals(AnalyticsSettings(null, null, ""), desktopAnalyticsSettings(emptyMap(), emptyMap()))
    }

    @Test
    fun `the build number is kvizic app build's and none that is no whole number`() {
        assertEquals(100, desktopBuildNumber(mapOf("kvizic.app.build" to " 100 ")))
        assertNull(desktopBuildNumber(mapOf("kvizic.app.build" to "0.1.0")))
        assertNull(desktopBuildNumber(emptyMap()))
    }
}
