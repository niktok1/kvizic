package io.ntole.kvizic.design.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import io.ntole.kvizic.design.skin.FlapGlyph
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.SkinMotion
import io.ntole.kvizic.design.skin.TimerMoment
import kotlin.math.ceil

/**
 * Where a question's clock stands, in milliseconds relative to now, as the wire sends every time: a
 * deadline is anchored where it is received, never compared with another device's clock.
 */
@Immutable
sealed interface TimerPhase {
    /** The question's whole time to answer in. */
    val totalMillis: Int

    /** No question yet: the lights are out and the whole time shows. */
    data class Waiting(
        override val totalMillis: Int,
    ) : TimerPhase

    /** The question read alone: the lights come up over [readMillis], [readLeftMillis] of it still to go. */
    data class Reading(
        override val totalMillis: Int,
        val readMillis: Int,
        val readLeftMillis: Int,
    ) : TimerPhase

    /** Counting down, [leftMillis] left when the timer is first drawn in this phase. */
    data class Running(
        override val totalMillis: Int,
        val leftMillis: Int,
    ) : TimerPhase

    /** Stopped with [leftMillis] left: everyone has answered, or the time is up. */
    data class Stopped(
        override val totalMillis: Int,
        val leftMillis: Int,
    ) : TimerPhase
}

/**
 * A question's timer, drawn from its [phase] by the skin's part: marquee bulbs going out one by one
 * round the seconds left, which flap on as each second goes. A running phase is anchored to the frame it
 * is first drawn in and counts on the frame clock, never the device's: a frame of it draws again and
 * composes nothing, and its [contentDescription] is set once for the phase, never each second, so a
 * screen reader is not told the time every second.
 */
@Composable
fun QuestionTimer(
    phase: TimerPhase,
    modifier: Modifier = Modifier,
    size: TimerSize = TimerSize.REGULAR,
    contentDescription: String? = null,
) {
    val skin = KvizicTheme.skin
    val part = skin.parts.timer
    val motion = skin.motion
    val whole = if (size == TimerSize.REGULAR) skin.space.timer.regular else skin.space.timer.small
    val measurer = rememberTextMeasurer(cacheSize = SECONDS_CACHE)
    val style =
        with(LocalDensity.current) {
            KvizicTheme.type.digits.style.copy(
                fontSize = (whole * SECONDS_SHARE).toSp(),
                lineHeight = (whole * SECONDS_SHARE).toSp(),
                color = part.digit,
            )
        }
    val figures = skin.fonts.display.figureHeight
    val numbers = remember(measurer, style, figures) { FlapGlyphs(measurer, style, figures) }
    val clock = remember(phase) { PhaseClock() }
    LaunchedEffect(phase) {
        val runsFor =
            when (phase) {
                is TimerPhase.Reading -> phase.readLeftMillis.toLong()
                is TimerPhase.Running -> phase.leftMillis.toLong() + motion.flap
                is TimerPhase.Waiting, is TimerPhase.Stopped -> 0L
            }
        var anchor = -1L
        while (clock.elapsedMillis.longValue < runsFor) {
            withFrameNanos { now ->
                if (anchor < 0) anchor = now
                clock.elapsedMillis.longValue = (now - anchor) / NANOS_PER_MILLI
            }
        }
    }
    val described =
        if (contentDescription !=
            null
        ) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        }
    Spacer(
        modifier =
            modifier
                .size(whole)
                .then(described)
                // A layer of its own: a frame of the timer records this and nothing round it.
                .graphicsLayer()
                .drawBehind {
                    val moment = momentOf(phase, clock.elapsedMillis.longValue, motion, numbers)
                    with(part) { drawTimer(moment) }
                },
    )
}

/** How far a phase has run, on the frame clock: written each frame, read only where the timer is drawn. */
private class PhaseClock {
    val elapsedMillis = mutableLongStateOf(0L)
}

/** Each number of seconds the timer shows, laid out once each. */
private operator fun FlapGlyphs.get(seconds: Int): FlapGlyph? = get(seconds.toString())

/** What the timer shows [elapsed] milliseconds into [phase]. */
private fun momentOf(
    phase: TimerPhase,
    elapsed: Long,
    motion: SkinMotion,
    numbers: FlapGlyphs,
): TimerMoment {
    val total = phase.totalMillis.coerceAtLeast(1)
    val wholeSeconds = secondsIn(total.toLong())
    return when (phase) {
        is TimerPhase.Waiting -> {
            TimerMoment(
                lit = 0f,
                filling = false,
                warn = false,
                seconds = numbers[wholeSeconds],
                previousSeconds = null,
                turn = 1f,
            )
        }

        is TimerPhase.Reading -> {
            val read = phase.readMillis.coerceAtLeast(1)
            val left = (phase.readLeftMillis - elapsed).coerceAtLeast(0L)
            TimerMoment(
                lit = (1f - left.toFloat() / read).coerceIn(0f, 1f),
                filling = true,
                warn = false,
                seconds = numbers[wholeSeconds],
                previousSeconds = null,
                turn = 1f,
            )
        }

        is TimerPhase.Running -> {
            val left = (phase.leftMillis - elapsed).coerceAtLeast(0L)
            val shown = secondsIn(left)
            // The seconds shown changed as the time left crossed a whole second, and flap on from then.
            val since = shown * MILLIS_PER_SECOND - left
            val changedHere = (shown + 1) * MILLIS_PER_SECOND <= phase.leftMillis
            val turn = if (changedHere) (since.toFloat() / motion.flap).coerceIn(0f, 1f) else 1f
            TimerMoment(
                lit = left.toFloat() / total,
                filling = false,
                warn = left in 1..motion.warnSeconds * MILLIS_PER_SECOND.toLong(),
                seconds = numbers[shown],
                previousSeconds = if (turn < 1f) numbers[shown + 1] else null,
                turn = turn,
            )
        }

        is TimerPhase.Stopped -> {
            val left = phase.leftMillis.coerceAtLeast(0).toLong()
            TimerMoment(
                lit = left.toFloat() / total,
                filling = false,
                warn = false,
                seconds = numbers[secondsIn(left)],
                previousSeconds = null,
                turn = 1f,
            )
        }
    }
}

/** The whole seconds a clock shows for [millis] left: up, so a second shows until it has wholly gone. */
private fun secondsIn(millis: Long): Int = ceil(millis / MILLIS_PER_SECOND.toDouble()).toInt()

private const val MILLIS_PER_SECOND = 1_000
private const val NANOS_PER_MILLI = 1_000_000L
private const val SECONDS_CACHE = 16

/** The seconds' size, as a share of the timer's. */
private const val SECONDS_SHARE = 0.34f
