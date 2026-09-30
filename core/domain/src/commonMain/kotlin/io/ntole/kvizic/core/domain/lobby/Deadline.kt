package io.ntole.kvizic.core.domain.lobby

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration

/**
 * When something ends, on this device's clock: the server says how long is left, never when, and the
 * client anchors it the moment the message arrives, so no two clocks need agree.
 */
public data class Deadline(
    val endsAt: ComparableTimeMark,
    /** How long it lasts in all, for a bar that empties: the time left when it began. */
    val total: Duration,
) {
    /** What is left now, never below zero. */
    public fun remaining(): Duration = (-endsAt.elapsedNow()).coerceAtLeast(Duration.ZERO)

    /** What is left as a share of [total], from 1 to 0. */
    public fun fractionLeft(): Float =
        if (total <= Duration.ZERO) 0f else (remaining() / total).toFloat().coerceIn(0f, 1f)
}
