package io.ntole.kvizic.analytics

import io.ntole.kvizic.analytics.RecordingAnalytics.Recorded
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * What the analytics hear of the app's comings and goings and of its screens: what
 * PostHog counts live players, sessions and the time on each screen from.
 */
class UsageTrackerTest {
    private val analytics = RecordingAnalytics()
    private val clock = TestTimeSource()
    private val usage = UsageTracker(analytics, clock)

    @Test
    fun `a launch opens the app then shows its first screen`() {
        usage.foreground("sr-Cyrl")
        usage.show("home")

        assertEquals(
            listOf(opened(fromBackground = false), screen("home")),
            analytics.recorded,
        )
    }

    @Test
    fun `a screen shown leaves the one before after the time on it`() {
        usage.foreground("sr-Cyrl")
        usage.show("home")
        clock += 3.seconds

        usage.show("about")

        assertEquals(listOf(left("home", 3_000), screen("about")), analytics.recorded.drop(2))
    }

    /** A composition made anew, as a rotation's is, shows the screen it showed: nothing new. */
    @Test
    fun `the screen shown already is nothing new`() {
        usage.foreground("sr-Cyrl")
        usage.show("about")

        usage.show("about")

        assertEquals(listOf(opened(fromBackground = false), screen("about")), analytics.recorded)
    }

    @Test
    fun `the background leaves the screen and sends what waits`() {
        usage.foreground("en")
        usage.show("about")
        clock += 5.seconds

        usage.background(configurationChanging = false)

        assertEquals(
            listOf(left("about", 5_000), backgrounded(5_000), Recorded(RecordingAnalytics.FLUSH)),
            analytics.recorded.drop(2),
        )
    }

    @Test
    fun `back from the background the app opens again on the screen it left`() {
        usage.foreground("en")
        usage.show("about")
        usage.background(configurationChanging = false)
        clock += 60.seconds

        usage.foreground("en")
        clock += 2.seconds
        usage.show("update")

        assertEquals(
            listOf(
                opened(fromBackground = true, language = "en"),
                screen("about"),
                left("about", 2_000),
                screen("update"),
            ),
            analytics.recorded.drop(5),
        )
    }

    /** Android's activity made anew on a rotation stops and starts again: nothing to report. */
    @Test
    fun `a rotation is neither the background nor a new opening`() {
        usage.foreground("sr-Cyrl")
        usage.show("about")
        val before = analytics.recorded.toList()

        usage.background(configurationChanging = true)
        usage.foreground("sr-Cyrl")
        usage.show("about")

        assertEquals(before, analytics.recorded)
    }

    /** A desktop window closed: the app goes to the background, and its last events are sent before it ends. */
    @Test
    fun `the end leaves the screen and waits for what waits to be sent`() =
        runTest {
            usage.foreground("sr-Cyrl")
            usage.show("about")
            clock += 4.seconds

            usage.end()

            assertEquals(
                listOf(
                    left("about", 4_000),
                    backgrounded(4_000),
                    Recorded(RecordingAnalytics.FLUSH),
                    Recorded(RecordingAnalytics.FLUSH_AND_WAIT),
                ),
                analytics.recorded.drop(2),
            )
        }

    /** The window's lifecycle may have said the app went to the background first: that is not said twice. */
    @Test
    fun `the end after the background only waits for the send`() =
        runTest {
            usage.foreground("sr-Cyrl")
            usage.background(configurationChanging = false)
            val before = analytics.recorded.size

            usage.end()

            assertEquals(listOf(Recorded(RecordingAnalytics.FLUSH_AND_WAIT)), analytics.recorded.drop(before))
        }

    /** A screen the navigator shows before the lifecycle says the app is in the foreground waits for it. */
    @Test
    fun `a screen shown before the app is in the foreground is shown with it`() {
        usage.show("home")
        assertEquals(emptyList(), analytics.recorded)

        usage.foreground("sr-Cyrl")

        assertEquals(listOf(opened(fromBackground = false), screen("home")), analytics.recorded)
    }

    @Test
    fun `a second start or stop in a row is nothing new`() {
        usage.foreground("sr-Cyrl")
        usage.foreground("sr-Cyrl")
        usage.background(configurationChanging = false)
        usage.background(configurationChanging = false)

        assertEquals(1, analytics.named(AnalyticsEvent.APP_OPENED).size)
        assertEquals(1, analytics.named(AnalyticsEvent.APP_BACKGROUNDED).size)
    }

    private fun opened(
        fromBackground: Boolean,
        language: String = "sr-Cyrl",
    ) = Recorded(
        AnalyticsEvent.APP_OPENED,
        mapOf(AnalyticsProperty.FROM_BACKGROUND to fromBackground, AnalyticsProperty.LANGUAGE to language),
    )

    private fun screen(name: String) =
        Recorded(RecordingAnalytics.SCREEN, mapOf(RecordingAnalytics.SCREEN_NAME to name))

    private fun left(
        name: String,
        millis: Long,
    ) = Recorded(
        AnalyticsEvent.SCREEN_LEFT,
        mapOf(AnalyticsProperty.SCREEN to name, AnalyticsProperty.DURATION_MS to millis),
    )

    private fun backgrounded(millis: Long) =
        Recorded(
            AnalyticsEvent.APP_BACKGROUNDED,
            mapOf(AnalyticsProperty.DURATION_MS to millis),
        )
}
