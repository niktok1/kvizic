package io.ntole.kvizic.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.ntole.kvizic.design.skin.KvizicTheme
import kotlin.math.roundToInt

/**
 * Every player's standing after a question, on one board: place, how many places it moved (▲ up, ▼ down),
 * avatar, name, what the question gave, and the total on flaps; the player's own line lit. The board scrolls
 * when it has less room than its lines, and keeps the player's own line in sight.
 *
 * [rows] are in their new order, each saying in [ScoreRow.moved] how far it came. Once per [reorderKey]
 * the lines start where they stood before the question and, after a beat, slide to their new places while
 * the totals flap to theirs; a board composed anew for the same key, after a rotation, shows them already
 * there. All of the slide in the placement alone.
 *
 * [timeLeft], when given, is the share of the time left to what comes next, drawn as a line draining along
 * the board's foot, in the draw alone; [timeDescription] is what a screen reader hears of it.
 */
@Composable
fun StandingsBoard(
    rows: List<ScoreRow>,
    modifier: Modifier = Modifier,
    reorderKey: Any? = null,
    timeLeft: (() -> Float)? = null,
    timeDescription: String? = null,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val colors = skin.colors
    var moved by rememberSaveable(reorderKey) { mutableStateOf(reorderKey == null) }
    val slide = remember(reorderKey) { Animatable(if (moved) 1f else 0f) }
    val scroll = rememberScrollState()
    val metrics = remember { BoardMetrics() }
    val own = rows.indexOfFirst { it.own }
    LaunchedEffect(reorderKey) {
        if (!moved) {
            // Where the player stood before, in sight first, then the slide to where they stand now.
            val from = if (own < 0) -1 else (own + rows[own].moved).coerceIn(0, rows.lastIndex)
            if (from >= 0) scroll.scrollTo(metrics.centring(from, scroll.viewportSize))
            // A beat on the frame clock, so the result is seen before the lines move.
            Animatable(0f).animateTo(1f, tween(REORDER_DELAY_MS, easing = LinearEasing))
            moved = true
            slide.animateTo(1f, tween(REORDER_MILLIS, easing = FastOutSlowInEasing))
        }
        if (own >= 0) scroll.animateScrollTo(metrics.centring(own, scroll.viewportSize))
    }
    Panel(modifier, padding = space.md) {
        Column {
            Box(Modifier.weight(1f, fill = false).verticalScroll(scroll)) {
                Layout(
                    content = {
                        rows.forEach { row -> key(row.id) { ScoreLine(row, showMove = moved, slide = slide) } }
                    },
                ) { measurables, constraints ->
                    val lines = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
                    val gap = space.sm.roundToPx()
                    val pitch = (lines.maxOfOrNull { it.height } ?: 0) + gap
                    metrics.pitch = pitch
                    val height = if (lines.isEmpty()) 0 else pitch * lines.size - gap
                    layout(constraints.maxWidth, height) {
                        val p = slide.value
                        lines.forEachIndexed { i, line ->
                            val from = (i + rows[i].moved).coerceIn(0, lines.lastIndex)
                            val y = ((from + (i - from) * p) * pitch).roundToInt()
                            // The player's own line slides over the others.
                            line.place(0, y, zIndex = if (rows[i].own) 1f else 0f)
                        }
                    }
                }
            }
            if (timeLeft != null) {
                Spacer(Modifier.height(space.sm))
                TimeLine(timeLeft, timeDescription, Modifier.fillMaxWidth().height(space.xs))
            }
        }
    }
}

/** What the board's slide needs of its layout between frames: how far apart two lines stand. */
private class BoardMetrics {
    var pitch = 0

    /** How far to scroll for line [index] to stand in the middle of a [viewport] that tall. */
    fun centring(
        index: Int,
        viewport: Int,
    ): Int = (index * pitch + pitch / 2 - viewport / 2).coerceAtLeast(0)
}

/** One player's line: place, its move, avatar, name, the question's points, and the total. */
@Composable
internal fun ScoreLine(
    row: ScoreRow,
    showMove: Boolean = false,
    slide: Animatable<Float, *>? = null,
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (row.own) {
                        Modifier.drawBehind {
                            val margin = space.xs.toPx()
                            drawRoundRect(
                                colors.onPageAccent.copy(alpha = OWN_LINE),
                                topLeft = Offset(-margin, -margin / 2),
                                size = Size(size.width + margin * 2, size.height + margin),
                                cornerRadius = CornerRadius(space.sm.toPx()),
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        KvizicText(
            "${row.place}.",
            Modifier.width(space.lg),
            style = type.name,
            color = colors.onRaisedMuted,
            maxLines = 1,
        )
        Move(if (showMove) row.moved else 0, slide, Modifier.width(space.lg))
        Avatar(row.avatarId, row.seat, size = AvatarSize.XS, host = row.host)
        KvizicText(
            row.name,
            Modifier.weight(1f),
            style = type.name,
            color = if (row.own) colors.onPageAccent else colors.onRaised,
            maxLines = 1,
        )
        row.delta?.let { delta -> Chip(signed(delta), tone = toneOfDelta(delta)) }
        val before = row.total - (row.delta ?: 0)
        FlipNumber(if (showMove || slide == null) row.total else before, size = FlapSize.SMALL)
    }
}

/** How many places a line moved: a triangle, up in the gain colour or down in the loss's, and the count. */
@Composable
private fun Move(
    moved: Int,
    slide: Animatable<Float, *>?,
    modifier: Modifier = Modifier,
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    Row(
        modifier.graphicsLayer { alpha = slide?.value ?: 1f },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.xxs),
    ) {
        if (moved == 0) return@Row
        val tint = if (moved > 0) colors.gain else colors.loss
        Spacer(
            Modifier.size(space.sm).drawBehind {
                val path =
                    Path().apply {
                        if (moved > 0) {
                            moveTo(size.width / 2, 0f)
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                        } else {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2, size.height)
                        }
                        close()
                    }
                drawPath(path, tint)
            },
        )
        KvizicText("${kotlin.math.abs(moved)}", style = type.badge, color = tint, maxLines = 1)
    }
}

/** A line draining from full to nothing as [left] goes from 1 to 0, drawn every frame until it is empty. */
@Composable
private fun TimeLine(
    left: () -> Float,
    description: String?,
    modifier: Modifier = Modifier,
) {
    val colors = KvizicTheme.colors
    val frame = remember { mutableLongStateOf(0L) }
    LaunchedEffect(left) {
        while (left() > 0f) withFrameNanos { frame.longValue = it }
        frame.longValue = -1L
    }
    Spacer(
        modifier
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = KvizicTheme.space.xxs)
            .drawBehind {
                frame.longValue
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(colors.raisedSide, cornerRadius = radius)
                val share = left().coerceIn(0f, 1f)
                if (share >
                    0f
                ) {
                    drawRoundRect(
                        colors.primary,
                        size = Size(size.width * share, size.height),
                        cornerRadius = radius,
                    )
                }
            },
    )
}

internal fun toneOfDelta(delta: Int): ChipTone =
    when {
        delta > 0 -> ChipTone.GAIN
        delta < 0 -> ChipTone.LOSS
        else -> ChipTone.NEUTRAL
    }

/** How long the lines stand where they were before they slide, and how long the slide takes. */
private const val REORDER_DELAY_MS = 700
private const val REORDER_MILLIS = 650

/** How strongly the player's own line is lit. */
private const val OWN_LINE = 0.14f
