package io.ntole.kvizic.loading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.Spinner
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.language.LocalStrings
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The game's loading spinner, named [name] for a screen reader where no text says what loads, with one
 * short line under it once it has turned for [SLOW_AFTER]: a free Render service sleeps, and its first
 * request takes up to a minute to wake it, so the player knows nothing is stuck.
 */
@Composable
fun LoadingSpinner(
    name: String? = null,
    modifier: Modifier = Modifier,
) {
    val slow = rememberShownFor(SLOW_AFTER)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KvizicTheme.space.sm),
        modifier = modifier,
    ) {
        Spinner(contentDescription = name)
        if (slow) {
            KvizicText(
                text = LocalStrings.current.stillLoading,
                color = KvizicTheme.colors.onPageMuted,
                style = KvizicTheme.type.caption,
            )
        }
    }
}

/** How long a spinner turns before the line under it says a moment more. */
internal val SLOW_AFTER: Duration = 5.seconds

/**
 * Whether what calls this has been shown for [duration], counted in the frames it is drawn in: the spinner
 * draws one every frame it turns anyway, so this asks for none more, and a test steps the frames' clock
 * through it.
 */
@Composable
private fun rememberShownFor(duration: Duration): Boolean {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        do {
            val now = withFrameNanos { it }
        } while (now - start < duration.inWholeNanoseconds)
        shown = true
    }
    return shown
}
