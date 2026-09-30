package io.ntole.kvizic

import io.ntole.kvizic.analytics.AnalyticsSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The analytics the iOS app reads from its Info.plist. The test binary's bundle has
 * no `KVIZIC_POSTHOG_KEY`, as an app built without `Local.xcconfig` does not, so the keys' values are
 * given through [analyticsSettingsFrom]; the app's own Info.plist is checked only by building it.
 */
class BundledAnalyticsTest {
    @Test
    fun `a bundle without the keys sends nothing`() {
        assertEquals(null, bundledAnalyticsSettings().key)
    }

    @Test
    fun `the keys' values are the analytics`() {
        val plist =
            mapOf<String, Any>(
                "KVIZIC_POSTHOG_KEY" to "phc_key",
                "KVIZIC_POSTHOG_HOST" to "eu.i.posthog.com",
                "CFBundleShortVersionString" to "1.0",
            )

        assertEquals(AnalyticsSettings("phc_key", "eu.i.posthog.com", "1.0"), analyticsSettingsFrom { plist[it] })
    }

    @Test
    fun `no values are no analytics and no version`() {
        assertEquals(AnalyticsSettings(null, null, ""), analyticsSettingsFrom { null })
    }
}
