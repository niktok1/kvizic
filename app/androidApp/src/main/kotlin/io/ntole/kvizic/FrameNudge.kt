package io.ntole.kvizic

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Asks the [Choreographer] for an empty frame every [EVERY_MILLIS] for [FOR_MILLIS] after the activity
 * starts, so a frame Compose waits for is never left behind as the app comes back to the foreground.
 *
 * On a POCO X3 Pro (MIUI, Android 12, 2026-10-12) the app came back with Compose's frame callback queued on
 * the Choreographer and no vsync scheduled to run it: taps were heard and changed the state, but nothing was
 * drawn until a focus change asked for a frame, up to half a minute later. One more frame callback posted
 * from outside ran it within 80 ms, and so does this. Never seen on the emulator; the device's log warns
 * of vsync timestamps "in the future" throughout. A nudge with nothing to draw is a frame tick, nothing more.
 */
internal class FrameNudge(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : DefaultLifecycleObserver {
    private var until = 0L

    private val nothing = Choreographer.FrameCallback {}

    private val nudge =
        object : Runnable {
            override fun run() {
                Choreographer.getInstance().postFrameCallback(nothing)
                if (SystemClock.uptimeMillis() < until) handler.postDelayed(this, EVERY_MILLIS)
            }
        }

    override fun onStart(owner: LifecycleOwner) {
        until = SystemClock.uptimeMillis() + FOR_MILLIS
        handler.removeCallbacks(nudge)
        handler.post(nudge)
    }

    override fun onStop(owner: LifecycleOwner) {
        handler.removeCallbacks(nudge)
    }

    private companion object {
        const val EVERY_MILLIS = 100L
        const val FOR_MILLIS = 5_000L
    }
}
