package io.ntole.kvizic

import androidx.compose.ui.ImageComposeScene
import org.jetbrains.skia.Image

/*
 * What the tests that draw the game's screens as stills share: a scene rendered once every animation has
 * ended.
 */

/** How long a still waits for every animation to end: past the spinner's slow line, the longest yet. */
internal const val SETTLED_NANOS: Long = 6_000_000_000L

private const val FRAME_NANOS: Long = 1_000_000_000L / 60

/** The scene once every animation has ended, drawn a frame at a time up to then, as a screen draws. */
internal fun ImageComposeScene.renderSettled(): Image {
    (0..SETTLED_NANOS / FRAME_NANOS).forEach { frame -> renderAt(frame * FRAME_NANOS) }
    return render(SETTLED_NANOS)
}
