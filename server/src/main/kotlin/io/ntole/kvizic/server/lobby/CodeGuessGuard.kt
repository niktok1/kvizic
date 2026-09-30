package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.server.config.RequestBudget
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * What bounds guessing lobby codes: each address may name [budget] codes no lobby has per window, and
 * once it has, every join from it is refused until the window ends, a right code's too, so a guesser
 * learns nothing from which of their guesses got through. A fixed window from the first miss, in memory.
 */
class CodeGuessGuard(
    private val budget: RequestBudget,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    private class Window(
        val endsAt: ComparableTimeMark,
        var misses: Int,
    )

    private val windows = ConcurrentHashMap<String, Window>()

    /** How long [address] must wait, or null when it may try a code. */
    fun lockedOut(address: String): Duration? {
        val window = windows[address] ?: return null
        val left = window.endsAt - timeSource.markNow()
        if (!left.isPositive()) {
            windows.remove(address, window)
            return null
        }
        return left.takeIf { synchronized(window) { window.misses >= budget.requests } }
    }

    /** [address] named a code no lobby has. */
    fun missed(address: String) {
        val now = timeSource.markNow()
        windows.compute(address) { _, window ->
            if (window == null || window.endsAt <= now) {
                Window(now + budget.per, 1)
            } else {
                synchronized(window) { window.misses++ }
                window
            }
        }
        if (windows.size > SWEEP_ABOVE) windows.entries.removeIf { it.value.endsAt <= now }
    }

    private companion object {
        const val SWEEP_ABOVE = 10_000
    }
}
