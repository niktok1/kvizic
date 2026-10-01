package io.ntole.kvizic

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import org.jetbrains.skia.Image

/*
 * What the tests that draw the game's screens as stills share: a scene rendered once every animation has
 * ended.
 */

/** How long a still waits for every animation to end: past the spinner's slow line, the longest yet. */
internal const val SETTLED_NANOS: Long = 6_000_000_000L

/** How long a still is drawn at a screen's 60 frames a second: past the steps giving way to each other. */
private const val SMOOTH_NANOS: Long = 500_000_000L

private const val FRAME_NANOS: Long = 1_000_000_000L / 60

/** A frame each 100 ms from then on, as [passTime] draws. */
private const val SLOW_FRAME_NANOS: Long = 100_000_000L

/**
 * The scene once every animation has ended: drawn a frame at a time as a screen draws while one step gives
 * way to the next, then a frame each 100 ms up to then. What runs on the frame clock takes its value from
 * the frame's time, so the last frame is the one 60 a second would draw, from a quarter of the frames. The
 * slow frames are drawn once each: one that misses the snapshot manager only starts an animation a frame
 * late, which still ends long before the last.
 */
internal fun ImageComposeScene.renderSettled(): Image {
    var time = 0L
    while (time < SMOOTH_NANOS) {
        renderAt(time)
        time += FRAME_NANOS
    }
    while (time < SETTLED_NANOS) {
        Snapshot.sendApplyNotifications()
        render(time).close()
        time += SLOW_FRAME_NANOS
    }
    renderAt(SETTLED_NANOS)
    return render(SETTLED_NANOS)
}
