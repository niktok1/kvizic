package io.ntole.kvizic.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.SkinMotion
import io.ntole.kvizic.design.skin.SkinSpace
import kotlin.math.abs

/**
 * A number on split-flap cells, a digit a cell, in groups of three: a score, a count. When [value]
 * changes each digit that changes flaps on to its new one, the right-most first, through at most the
 * skin's few flaps however far it goes. The new value is laid out once, as the flaps' place and what a
 * screen reader reads; the flaps are only drawn.
 *
 * [signed] shows a plus before a value that is not negative: points won. [minDigits] pads with zeros.
 */
@Composable
fun FlipNumber(
    value: Int,
    modifier: Modifier = Modifier,
    size: FlapSize = FlapSize.MEDIUM,
    minDigits: Int = 1,
    signed: Boolean = false,
    contentDescription: String? = null,
) {
    val text = flapText(value, minDigits, signed)
    FlipText(text, modifier, size, contentDescription ?: text.replace(" ", ""))
}

/**
 * A room's code on large split-flap cells, in two groups of three: 482 915. A screen reader reads it
 * digit by digit, or as [contentDescription] says.
 */
@Composable
fun CodeDisplay(
    code: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    require(code.all { it.isDigit() }) { "a room's code is digits: $code" }
    val grouped = code.chunked(CODE_GROUP).joinToString(" ")
    Panel(modifier = modifier, kind = PanelKind.WELL, padding = KvizicTheme.space.md) {
        FlipText(
            grouped,
            size = FlapSize.LARGE,
            contentDescription =
                contentDescription ?: code.toList().joinToString(" "),
        )
    }
}

/**
 * [text] on split-flap cells: each digit and sign a cell, each space a gap between groups. Flaps from the
 * text shown before whenever [text] changes, in the draw alone ([FlipTimeline]).
 */
@Composable
internal fun FlipText(
    text: String,
    modifier: Modifier = Modifier,
    size: FlapSize = FlapSize.MEDIUM,
    contentDescription: String = text,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val part = skin.parts.flap
    val motion = skin.motion
    val cell = space.cellOf(size)
    val measurer = rememberTextMeasurer(cacheSize = GLYPH_CACHE)
    val style =
        with(LocalDensity.current) {
            KvizicTheme.type.digits.style.copy(
                fontSize = (cell.height * space.flap.digitFraction).toSp(),
                lineHeight = (cell.height * space.flap.digitFraction).toSp(),
                color = part.digit,
                textAlign = TextAlign.Center,
            )
        }
    val figures = skin.fonts.display.figureHeight
    val glyphs = remember(measurer, style, figures) { FlapGlyphs(measurer, style, figures) }
    // The gaps scale with the cells, so a small score is as tight as a large code.
    val scale = cell.width / space.flap.medium.width
    val timeline = remember { FlipTimeline(text) }
    val clock = remember { Animatable(timeline.total(motion)) }
    LaunchedEffect(text) {
        if (timeline.to == text) return@LaunchedEffect
        timeline.moveOn(to = text, shownAt = clock.value, motion = motion)
        clock.snapTo(0f)
        clock.animateTo(
            timeline.total(motion),
            tween(timeline.total(motion).toInt().coerceAtLeast(1), easing = LinearEasing),
        )
    }
    Row(
        modifier =
            modifier
                .semantics { this.contentDescription = contentDescription }
                // A layer of its own, so a frame of the flaps records these cells again and nothing round them.
                .graphicsLayer(),
    ) {
        val cells = text.length
        text.forEachIndexed { i, char ->
            val fromRight = cells - 1 - i
            if (char == ' ') {
                Spacer(Modifier.width(space.flap.groupGap * scale))
            } else {
                if (i > 0 && text[i - 1] != ' ') Spacer(Modifier.width(space.flap.gap * scale))
                Spacer(
                    Modifier
                        .size(cell)
                        .drawBehind {
                            val (from, to, turn) = timeline.at(fromRight, clock.value, motion)
                            with(part) { drawCell(glyphs[from], glyphs[to], turn) }
                        },
                )
            }
        }
    }
}

/** The cell size for [size]. */
private fun SkinSpace.cellOf(size: FlapSize): DpSize =
    when (size) {
        FlapSize.SMALL -> flap.small
        FlapSize.MEDIUM -> flap.medium
        FlapSize.LARGE -> flap.large
    }

/** A value as the flaps show it: its digits in groups of three once there are four, and its sign. */
internal fun flapText(
    value: Int,
    minDigits: Int,
    signed: Boolean,
): String {
    val digits = abs(value.toLong()).toString().padStart(minDigits, '0')
    val grouped =
        if (digits.length < GROUP_FROM) {
            digits
        } else {
            digits
                .reversed()
                .chunked(GROUP)
                .joinToString(" ")
                .reversed()
        }
    val sign =
        when {
            value < 0 -> MINUS
            signed -> PLUS
            else -> ""
        }
    return sign + grouped
}

/**
 * What a flip shows at each moment: the text it flips from and to, and each cell's flaps between them,
 * the right-most cell first. Plain fields, no state: its draw reads the clock, which is state, and this
 * only as the clock tells it the time.
 */
internal class FlipTimeline(
    initial: String,
) {
    var from: String = initial
        private set
    var to: String = initial
        private set

    /** Starts a flip to [to] from whatever [shownAt] of the flip before shows, so a flip cut short goes on from there. */
    fun moveOn(
        to: String,
        shownAt: Float,
        motion: SkinMotion,
    ) {
        val shown = StringBuilder()
        for (i in this.to.indices) {
            val fromRight = this.to.length - 1 - i
            shown.append(at(fromRight, shownAt, motion).second ?: ' ')
        }
        from = shown.toString()
        this.to = to
    }

    /** How long the whole flip takes, in milliseconds. */
    fun total(motion: SkinMotion): Float {
        var longest = 0
        for (fromRight in to.indices) {
            val flaps = flaps(fromRight, motion).size - 1
            if (flaps > 0) longest = maxOf(longest, fromRight * motion.flapStagger + flaps * motion.flap)
        }
        return longest.toFloat()
    }

    /** The glyph cell [fromRight] turns from and to at [millis] into the flip, and how far through the turn it is. */
    fun at(
        fromRight: Int,
        millis: Float,
        motion: SkinMotion,
    ): Triple<Char?, Char?, Float> {
        val steps = flaps(fromRight, motion)
        if (steps.size == 1) return Triple(steps[0], steps[0], 1f)
        val into = millis - fromRight * motion.flapStagger
        if (into <= 0f) return Triple(steps[0], steps[0], 1f)
        val flap = (into / motion.flap).toInt()
        if (flap >= steps.size - 1) return Triple(steps.last(), steps.last(), 1f)
        return Triple(steps[flap], steps[flap + 1], (into - flap * motion.flap) / motion.flap)
    }

    /** The characters cell [fromRight] shows in turn, from the one before to the new one, at most the skin's few flaps. */
    private fun flaps(
        fromRight: Int,
        motion: SkinMotion,
    ): List<Char?> {
        val old = from.getOrNull(from.length - 1 - fromRight)?.takeUnless { it == ' ' }
        val new = to.getOrNull(to.length - 1 - fromRight)?.takeUnless { it == ' ' }
        if (old == new) return listOf(new)
        if (old == null || new == null || !old.isDigit() || !new.isDigit()) return listOf(old, new)
        // Forward round the drum, as a real flap board turns: through the digits after the old one.
        val distance = (new - old).mod(DIGITS)
        val between = minOf(distance, motion.flapsPerDigit) - 1
        return buildList {
            add(old)
            for (k in 1..between) add('0' + (old - '0' + k).mod(DIGITS))
            add(new)
        }
    }
}

private const val DIGITS = 10
private const val GROUP = 3
private const val GROUP_FROM = 4
private const val CODE_GROUP = 3
private const val GLYPH_CACHE = 24
private const val PLUS = "+"

/** A true minus, not a hyphen, as a score's loss is set. */
private const val MINUS = "−"
