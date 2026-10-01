package io.ntole.kvizic.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import io.ntole.kvizic.core.domain.analytics.Analytics
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty

/**
 * The analytics the game's screens report taps to: the app's, which `App` provides, and none for a
 * screen drawn on its own, as a test draws one.
 */
val LocalAnalytics = staticCompositionLocalOf { Analytics.None }

/**
 * [onClick], reporting first a tap on [element], with [properties] of its own, to [LocalAnalytics]: what
 * every button, tile and line of the game hands its `onClick`, so a tap is counted wherever it lands, by a
 * name that never changes with the language, the text or the layout.
 *
 * An element is named `screen.what`, in lower case and underscores (`home.about`, `top_bar.back`): once
 * sent it never changes, or the dashboards built on it lose it. A new button gets one this way, and
 * `TapsTest` taps everything tappable on every screen and fails on one that reports nothing.
 */
@Composable
fun tapped(
    element: String,
    properties: Map<String, Any?> = emptyMap(),
    onClick: () -> Unit,
): () -> Unit {
    val analytics = LocalAnalytics.current
    val action by rememberUpdatedState(onClick)
    return remember(analytics, element, properties) {
        {
            analytics.track(AnalyticsEvent.TAP, mapOf(AnalyticsProperty.ELEMENT to element) + properties)
            action()
        }
    }
}

/**
 * [onPick], reporting first a tap on [element] with the index picked under `option`: for a row of things
 * that hands back which was tapped, a question's answers, where [tapped] would need one per index.
 */
@Composable
fun tappedAt(
    element: String,
    onPick: (Int) -> Unit,
): (Int) -> Unit {
    val analytics = LocalAnalytics.current
    val action by rememberUpdatedState(onPick)
    return remember(analytics, element) {
        { index ->
            analytics.track(AnalyticsEvent.TAP, mapOf(AnalyticsProperty.ELEMENT to element, OPTION to index))
            action(index)
        }
    }
}

/** The property naming which of a row of things was tapped. */
private const val OPTION = "option"
