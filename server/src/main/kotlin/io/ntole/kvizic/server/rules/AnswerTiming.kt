package io.ntole.kvizic.server.rules

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/**
 * How long an answer took, as the server scores it: from when the answers went out to when the answer
 * came in, less the connection's measured round trip, which the question's trip down and the answer's
 * trip up both spent, capped at [rttCap] so a client faking a slow link gains little. Held to
 * `0..duration`: an answer inside the grace after the buzzer counts as at the buzzer.
 */
fun answerTime(
    sinceOpened: Duration,
    rtt: Duration,
    rttCap: Duration,
    duration: Duration,
): Duration = (sinceOpened - rtt.coerceIn(ZERO, rttCap)).coerceIn(ZERO, duration)

/** The share of [duration] still left after [taken]: 1 at once, 0 at the buzzer. */
fun fractionLeft(
    taken: Duration,
    duration: Duration,
): Double = if (duration <= ZERO) 0.0 else (1.0 - taken / duration).coerceIn(0.0, 1.0)
