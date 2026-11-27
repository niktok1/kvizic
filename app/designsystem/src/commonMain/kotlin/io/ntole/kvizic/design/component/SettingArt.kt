package io.ntole.kvizic.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.cued

@Composable
private fun settling(value: Float) =
    animateFloatAsState(value, tween(KvizicTheme.skin.motion.settle), label = "setting")

/**
 * The questions of a game as a stack of question cards, one card for each [step] of them, [count] on the top one,
 * lit; a card slides on or off as the count changes and the number pops.
 */
@Composable
fun CardStackArt(
    count: Int,
    step: Int,
    most: Int,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val colors = skin.colors
    val whole = space.timer.small
    val stack = settling((count / step).toFloat())
    val pop = rememberPop(count)
    val cards = most / step
    Box(Modifier.size(whole)) {
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val card = Size(size.width * CARD_WIDTH, size.height * CARD_HEIGHT)
                val corner = CornerRadius(card.width * CARD_CORNER)
                val side = space.strokeThin.toPx()
                repeat(cards) { k ->
                    val shown = (stack.value - k).coerceIn(0f, 1f)
                    if (shown > 0f) {
                        val at = cardAt(k.toFloat(), size, card) - Offset(0f, (1f - shown) * card.height * SLIDE)
                        val top = (1f - kotlin.math.abs(stack.value - 1f - k)).coerceIn(0f, 1f)
                        drawRoundRect(colors.plateSide.copy(alpha = shown), at + Offset(0f, side * 2), card, corner)
                        drawRoundRect(lerp(colors.plate, colors.primary, top).copy(alpha = shown), at, card, corner)
                        drawRoundRect(colors.outline.copy(alpha = shown), at, card, corner, style = Stroke(side * 2))
                    }
                }
            },
        )
        KvizicText(
            count.toString(),
            Modifier
                .align(Alignment.TopStart)
                .width(whole * CARD_WIDTH)
                .graphicsLayer {
                    val area = Size(whole.toPx(), whole.toPx())
                    val card = Size(area.width * CARD_WIDTH, area.height * CARD_HEIGHT)
                    val at = cardAt(stack.value - 1f, area, card)
                    translationX = at.x
                    translationY = at.y + (card.height - size.height) / 2
                    scaleX = pop.value
                    scaleY = pop.value
                },
            style = KvizicTheme.type.headline,
            color = colors.onPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            autoSize =
                TextAutoSize.StepBased(
                    KvizicTheme.type.bodyStrong.style.fontSize,
                    KvizicTheme.type.headline.style.fontSize,
                ),
        )
    }
}

private fun cardAt(
    k: Float,
    area: Size,
    card: Size,
): Offset =
    Offset(
        (area.width - card.width) / 2 - area.width * CARD_STEP * (STACK_MIDDLE - k),
        area.height - card.height - area.height * CARD_STEP * (k + 1f),
    )

@Composable
private fun rememberPop(key: Any): Animatable<Float, *> {
    val settle = KvizicTheme.skin.motion.settle
    val pop = remember { Animatable(1f) }
    val seen = remember { Seen(key) }
    LaunchedEffect(key) {
        if (seen.key != key) {
            seen.key = key
            pop.snapTo(POP)
            pop.animateTo(1f, tween(settle))
        }
    }
    return pop
}

private class Seen(
    var key: Any,
) {
    var moved = false
}

/**
 * How hard the questions are, of [levels], as a gauge: a needle on a half dial, its steps lit up to [level], the
 * last in the colour of a loss; the needle sweeps and the steps light as the level changes.
 */
@Composable
fun GaugeArt(
    level: Int,
    levels: Int,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val colors = skin.colors
    val at = settling(level.toFloat())
    Spacer(
        Modifier.size(space.timer.small).drawBehind {
            val band = size.width * GAUGE_BAND
            val radius = size.width / 2 - band
            val centre = Offset(size.width / 2, size.height * GAUGE_MIDDLE)
            val box = Size(radius * 2, radius * 2)
            val corner = Offset(centre.x - radius, centre.y - radius)
            val sweep = HALF_TURN / levels
            val line = space.strokeThin.toPx()
            drawArc(
                colors.outline,
                HALF_TURN,
                HALF_TURN,
                false,
                corner,
                box,
                style = Stroke(band + line * 2, cap = StrokeCap.Round),
            )
            repeat(levels) { i ->
                val lit = (at.value + 1f - i).coerceIn(0f, 1f)
                val colour = if (i == levels - 1) colors.loss else colors.primary
                drawArc(
                    lerp(colors.raised, colour, lit),
                    HALF_TURN + i * sweep + GAUGE_GAP,
                    sweep - GAUGE_GAP * 2,
                    false,
                    corner,
                    box,
                    style = Stroke(band),
                )
            }
            rotate(-QUARTER_TURN + (at.value + 0.5f) * sweep, centre) {
                val tip = Offset(centre.x, centre.y - radius)
                drawLine(colors.outline, centre, tip, space.stroke.toPx() + line * 2, StrokeCap.Round)
                drawLine(colors.primary, centre, tip, space.stroke.toPx(), StrokeCap.Round)
            }
            drawCircle(colors.raised, band / 2, centre)
            drawCircle(colors.outline, band / 2, centre, style = Stroke(line))
        },
    )
}

/**
 * A room's seats as chairs round a table seen from above, [chairs] of them, the first [seats] taken, each in its
 * seat's colour, and their number on flaps in the middle; the chairs light one by one as the number changes.
 */
@Composable
fun RoundTableArt(
    seats: Int,
    chairs: Int,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val colors = skin.colors
    val taken = settling(seats.toFloat())
    Box(Modifier.size(space.timer.small), contentAlignment = Alignment.Center) {
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val chair = Size(size.width * CHAIR_WIDTH, size.width * CHAIR_DEPTH)
                val reach = size.width / 2 - chair.height
                val line = space.strokeThin.toPx()
                repeat(chairs) { i ->
                    val lit = (taken.value - i).coerceIn(0f, 1f)
                    rotate(i * FULL_TURN / chairs + FULL_TURN / chairs / 2) {
                        val at = Offset(center.x - chair.width / 2, center.y - reach - chair.height)
                        val corner = CornerRadius(chair.height / 2)
                        drawRoundRect(colors.seat(i).copy(alpha = lit), at, chair, corner)
                        drawRoundRect(
                            lerp(colors.onPageMuted, colors.outline, lit),
                            at,
                            chair,
                            corner,
                            style = Stroke(line),
                        )
                    }
                }
                val table = reach - chair.height / 2
                drawCircle(colors.raisedSide, table, center + Offset(0f, line * 2))
                drawCircle(colors.raised, table)
                drawCircle(colors.outline, table, style = Stroke(line))
            },
        )
        FlipNumber(seats, size = FlapSize.SMALL)
    }
}

/**
 * A question's clock showing [seconds], as a question will; once the seconds change, its lights come up again
 * round the new number, as they do while a question is read.
 */
@Composable
fun ClockArt(seconds: Int) {
    val motion = KvizicTheme.skin.motion
    val seen = remember { Seen(seconds) }
    if (seconds != seen.key) seen.moved = true
    val total = seconds * MILLIS_PER_SECOND
    QuestionTimer(
        if (!seen.moved) {
            TimerPhase.Stopped(total, total)
        } else {
            TimerPhase.Reading(total, motion.reveal, motion.reveal)
        },
        size = TimerSize.SMALL,
    )
}

/** Who may come in, as the lock or the globe, one fading into the other as it changes. */
@Composable
fun VisibilityArt(public: Boolean) {
    val space = KvizicTheme.space
    val open = settling(if (public) 1f else 0f)
    Box(Modifier.size(space.timer.small), contentAlignment = Alignment.Center) {
        listOf(false, true).forEach { globe ->
            KvizicIcon(
                if (globe) KvizicIcons.Globe else KvizicIcons.Lock,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { alpha = if (globe) open.value else 1f - open.value },
                tint = KvizicTheme.colors.primary,
                size = space.icon.large,
            )
        }
    }
}

/** The wrong answer's minus, on a badge in the colour of a loss while [on], greyed while off. */
@Composable
fun PenaltyArt(on: Boolean) {
    val space = KvizicTheme.space
    val colors = KvizicTheme.colors
    val lit = settling(if (on) 1f else 0f)
    Spacer(
        Modifier.size(space.icon.large * BADGE).drawBehind {
            val radius = size.minDimension / 2
            val line = space.strokeThin.toPx()
            drawCircle(lerp(colors.raisedSide, colors.outline, lit.value), radius - line, center + Offset(0f, line * 2))
            drawCircle(lerp(colors.raised, colors.loss, lit.value), radius - line * 2)
            drawCircle(colors.outline, radius - line * 2, style = Stroke(line))
            val half = radius * MINUS
            drawLine(
                lerp(colors.onRaisedMuted, colors.page, lit.value),
                Offset(center.x - half, center.y),
                Offset(center.x + half, center.y),
                space.stroke.toPx() * 2,
                StrokeCap.Round,
            )
        },
    )
}

/**
 * The topics a room plays, as a button: a fan of question cards in the answers' colours, [label] over the topics'
 * [names] and [detail] under them, and a chevron to the picker, which [onClick] opens.
 */
@Composable
fun TopicsButton(
    label: String,
    names: String,
    detail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val colors = skin.colors
    val muted = skin.parts.panel.muted(PanelKind.PLAIN)
    val presses = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .raised(skin.parts.panel.surface(PanelKind.PLAIN), skin.depth, skin.motion, presses)
            .clickable(
                interactionSource = presses,
                indication = null,
                role = Role.Button,
                onClick = cued(Cue.TAP, onClick),
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = space.md, vertical = space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space.md),
        ) {
            Spacer(
                Modifier.size(space.icon.large * FAN_WIDTH, space.icon.large).drawBehind {
                    val card = Size(size.height * CARD_WIDTH, size.height * CARD_HEIGHT * FAN_CARD)
                    val corner = CornerRadius(card.width * CARD_CORNER)
                    repeat(FAN) { i ->
                        rotate(FAN_TILT * (i - 1), Offset(card.width / 2 + i * card.width * FAN_STEP, size.height)) {
                            val at = Offset(i * card.width * FAN_STEP, (size.height - card.height) / 2)
                            drawRoundRect(colors.letter(i), at, card, corner)
                            drawRoundRect(colors.outline, at, card, corner, style = Stroke(space.strokeThin.toPx()))
                        }
                    }
                },
            )
            Column(Modifier.weight(1f)) {
                KvizicText(label, style = KvizicTheme.type.label, color = muted)
                KvizicText(names, style = KvizicTheme.type.bodyStrong, maxLines = 2)
                if (detail != null) KvizicText(detail, style = KvizicTheme.type.caption, color = muted)
            }
            KvizicIcon(KvizicIcons.ChevronRight, contentDescription = null, size = space.icon.small)
        }
    }
}

private const val MILLIS_PER_SECOND = 1000
private const val HALF_TURN = 180f
private const val QUARTER_TURN = 90f
private const val FULL_TURN = 360f

private const val CARD_WIDTH = 0.6f
private const val CARD_HEIGHT = 0.7f
private const val CARD_CORNER = 0.14f
private const val CARD_STEP = 0.06f
private const val STACK_MIDDLE = 1.5f

private const val SLIDE = 0.4f
private const val POP = 0.7f

private const val GAUGE_BAND = 0.16f
private const val GAUGE_MIDDLE = 0.72f
private const val GAUGE_GAP = 2f

private const val CHAIR_WIDTH = 0.2f
private const val CHAIR_DEPTH = 0.12f

private const val BADGE = 1.2f
private const val MINUS = 0.45f

private const val FAN = 3
private const val FAN_WIDTH = 1.4f
private const val FAN_CARD = 1.3f
private const val FAN_STEP = 0.45f
private const val FAN_TILT = 10f
