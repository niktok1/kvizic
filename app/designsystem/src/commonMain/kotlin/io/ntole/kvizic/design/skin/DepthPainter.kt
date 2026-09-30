package io.ntole.kvizic.design.skin

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin

/** The room a surface of [look] keeps to its right and below it, for its side or shadow. */
internal fun SkinDepth.reserve(look: SurfaceLook): Pair<Dp, Dp> =
    when (this) {
        is SkinDepth.HardOffset -> look.height * abs(direction.x) to look.height * direction.y
        is SkinDepth.Soft -> 0.dp to look.height
        is SkinDepth.Sketched -> look.height * abs(direction.x) to look.height * direction.y
    }

/** The colours a surface is drawn in at one moment: the look it settles from mixed into the one it settles to. */
internal class SurfaceColors(
    val fill: Color,
    val side: Color,
    val outline: Color,
    val glow: Color,
)

/**
 * Draws a raised surface of [look] in [colors] over this scope's whole size, standing [liftPx] of its
 * [SurfaceLook.height], and returns how far its face has moved from where it stands highest, so its
 * content can be drawn there too. What costs to work out, a hand-drawn outline above all, is kept in
 * [cache] from one frame to the next.
 */
internal fun DrawScope.drawSurface(
    depth: SkinDepth,
    look: SurfaceLook,
    colors: SurfaceColors,
    liftPx: Float,
    cache: SurfaceCache,
): Offset {
    val heightPx = look.height.toPx()
    val sink = (heightPx - liftPx).coerceIn(0f, heightPx)
    return when (depth) {
        is SkinDepth.HardOffset -> drawHard(depth, look, colors, heightPx, sink, cache)
        is SkinDepth.Soft -> drawSoft(depth, look, colors, heightPx, liftPx, cache)
        is SkinDepth.Sketched -> drawSketched(depth, look, colors, heightPx, sink, cache)
    }
}

/** The outlines a surface draws, worked out again only when its size, shape or seed changes. */
internal class SurfaceCache {
    private var key: CacheKey? = null
    var outline: Outline? = null
        private set
    var faceWobble: Path? = null
        private set
    var shadowWobble: Path? = null
        private set

    /** The face's outline at [size], and with [wobble] its two hand-drawn edges, for [look]. */
    fun of(
        look: SurfaceLook,
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
        wobble: Float = 0f,
    ): Outline {
        val key = CacheKey(size, look.shape, look.seed, wobble, layoutDirection)
        val kept = outline
        if (key == this.key && kept != null) return kept
        val made = look.shape.createOutline(size, layoutDirection, density)
        this.key = key
        outline = made
        faceWobble = if (wobble > 0f) made.wobbled(wobble, look.seed) else null
        shadowWobble = if (wobble > 0f) made.wobbled(wobble, look.seed + SHADOW_SEED) else null
        return made
    }

    private data class CacheKey(
        val size: Size,
        val shape: Shape,
        val seed: Int,
        val wobble: Float,
        val layoutDirection: LayoutDirection,
    )
}

private fun DrawScope.drawHard(
    depth: SkinDepth.HardOffset,
    look: SurfaceLook,
    colors: SurfaceColors,
    heightPx: Float,
    sink: Float,
    cache: SurfaceCache,
): Offset {
    val stroke = depth.outline.toPx()
    val dir = depth.direction
    val faceSize = Size(size.width - abs(dir.x) * heightPx - stroke, size.height - dir.y * heightPx - stroke)
    if (faceSize.width <= 0f || faceSize.height <= 0f) return Offset.Zero
    val outline = cache.of(look, faceSize, layoutDirection, this)
    val line = Stroke(width = stroke, join = StrokeJoin.Round)
    // Half the outline's width in from the edge, so the whole of it is drawn inside the surface's bounds.
    translate(stroke / 2, stroke / 2) {
        if (colors.glow.isSpecified) drawHalo(outline, colors.glow, heightPx)
        val body = if (dir == DepthDirection.Down) outline.extendedDown(heightPx) else null
        if (body != null) {
            // Straight down, the side is the face drawn on to the bottom: a key's body under its cap.
            drawOutline(body, colors.side)
            drawOutline(body, colors.outline, style = line)
        } else {
            stack(Offset(dir.x * heightPx, dir.y * heightPx)) { drawOutline(outline, colors.side) }
            translate(dir.x * heightPx, dir.y * heightPx) { drawOutline(outline, colors.outline, style = line) }
        }
        translate(dir.x * sink, dir.y * sink) {
            drawOutline(outline, colors.fill)
            with(look.decor) { decorate(faceSize, outline) }
            drawOutline(outline, colors.outline, style = line)
        }
    }
    return Offset(dir.x * sink, dir.y * sink)
}

private fun DrawScope.drawSoft(
    depth: SkinDepth.Soft,
    look: SurfaceLook,
    colors: SurfaceColors,
    heightPx: Float,
    liftPx: Float,
    cache: SurfaceCache,
): Offset {
    val faceSize = Size(size.width, size.height - heightPx)
    if (faceSize.width <= 0f || faceSize.height <= 0f) return Offset.Zero
    val outline = cache.of(look, faceSize, layoutDirection, this)
    if (colors.glow.isSpecified) drawHalo(outline, colors.glow, heightPx)
    // A few offset copies at a low alpha each: a shadow as soft as a blur, which common code lacks.
    for (i in SOFT_STEPS downTo 1) {
        val spread = liftPx * i / SOFT_STEPS
        translate(0f, spread / 2) {
            drawOutline(outline.expandedBy(spread), depth.shadow.copy(alpha = depth.shadow.alpha / SOFT_STEPS))
        }
    }
    drawOutline(outline, colors.fill)
    with(look.decor) { decorate(faceSize, outline) }
    val stroke = depth.outline.toPx()
    if (stroke > 0f) drawOutline(outline, colors.outline, style = Stroke(width = stroke))
    return Offset.Zero
}

private fun DrawScope.drawSketched(
    depth: SkinDepth.Sketched,
    look: SurfaceLook,
    colors: SurfaceColors,
    heightPx: Float,
    sink: Float,
    cache: SurfaceCache,
): Offset {
    val stroke = depth.stroke.toPx()
    val wobble = depth.wobble.toPx()
    // In from the edge by the pen's half width and the wobble, so neither is drawn past the bounds.
    val inset = stroke / 2 + wobble
    val dir = depth.direction
    val faceSize =
        Size(size.width - abs(dir.x) * heightPx - inset * 2, size.height - dir.y * heightPx - inset * 2)
    if (faceSize.width <= 0f || faceSize.height <= 0f) return Offset.Zero
    val outline = cache.of(look, faceSize, layoutDirection, this, wobble)
    val face = cache.faceWobble ?: return Offset.Zero
    val shadow = cache.shadowWobble ?: return Offset.Zero
    translate(inset, inset) {
        if (colors.glow.isSpecified) drawHalo(outline, colors.glow, heightPx)
        translate(dir.x * heightPx, dir.y * heightPx) {
            clipPath(shadow) { hatch(faceSize, depth.hatchGap.toPx(), colors.side, stroke / 2) }
            drawPath(shadow, colors.side, style = Stroke(width = stroke / 2, cap = StrokeCap.Round))
        }
        translate(dir.x * sink, dir.y * sink) {
            drawPath(face, colors.fill)
            with(look.decor) { decorate(faceSize, outline) }
            drawPath(
                face,
                colors.outline,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
    return Offset(dir.x * sink, dir.y * sink)
}

/** A soft halo round [outline] in [glow], reaching past it by about [reach]: something lit. */
private fun DrawScope.drawHalo(
    outline: Outline,
    glow: Color,
    reach: Float,
) {
    for (i in HALO_RINGS downTo 1) {
        val grown = outline.expandedBy(reach * HALO_REACH * i / HALO_RINGS)
        drawOutline(grown, glow.copy(alpha = glow.alpha * HALO_ALPHA / i))
    }
}

/** Pencil hatching across [area], lines [gap] apart at forty-five degrees. */
private fun DrawScope.hatch(
    area: Size,
    gap: Float,
    color: Color,
    width: Float,
) {
    if (gap <= 0f) return
    var x = -area.height
    while (x < area.width) {
        drawLine(color, Offset(x, area.height), Offset(x + area.height, 0f), strokeWidth = width, cap = StrokeCap.Round)
        x += gap
    }
}

/** [draw] repeated a pixel apart along [offset]: a side that no rounded rectangle describes. */
private inline fun DrawScope.stack(
    offset: Offset,
    draw: DrawScope.() -> Unit,
) {
    val steps = ceil(max(abs(offset.x), abs(offset.y))).toInt().coerceAtLeast(1)
    for (i in steps downTo 0) {
        translate(offset.x * i / steps, offset.y * i / steps) { draw() }
    }
}

/** The outline drawn on [by] further down, as a key's body under its cap, where it is a rectangle or a rounded one. */
private fun Outline.extendedDown(by: Float): Outline? =
    when (this) {
        is Outline.Rectangle -> Outline.Rectangle(rect.copy(bottom = rect.bottom + by))
        is Outline.Rounded -> Outline.Rounded(roundRect.copy(bottom = roundRect.bottom + by))
        is Outline.Generic -> null
    }

/** The outline grown by [by] on every side, its corners with it. */
internal fun Outline.expandedBy(by: Float): Outline =
    when (this) {
        is Outline.Rectangle -> Outline.Rectangle(rect.inflate(by))
        is Outline.Rounded -> Outline.Rounded(roundRect.inflated(by))
        is Outline.Generic -> this
    }

private fun RoundRect.inflated(by: Float): RoundRect =
    RoundRect(
        rect = Rect(left - by, top - by, right + by, bottom + by),
        topLeft = topLeftCornerRadius.grown(by),
        topRight = topRightCornerRadius.grown(by),
        bottomRight = bottomRightCornerRadius.grown(by),
        bottomLeft = bottomLeftCornerRadius.grown(by),
    )

private fun CornerRadius.grown(by: Float): CornerRadius = CornerRadius(x + by, y + by)

/**
 * The outline as a hand draws it: its edge sampled every few pixels and each point pushed off it by up to
 * [amplitude], along three waves that each fit the edge a whole number of times, so the line closes where
 * it began. [seed] shifts the waves: no two surfaces wobble alike, and one always the same way.
 */
internal fun Outline.wobbled(
    amplitude: Float,
    seed: Int,
): Path {
    val edge = Path().apply { addOutline(this@wobbled) }
    val measure = PathMeasure()
    measure.setPath(edge, true)
    val length = measure.length
    if (length <= 0f || amplitude <= 0f) return edge
    val points = max(MIN_WOBBLE_POINTS, (length / WOBBLE_STEP).toInt())
    val phase = seed * SEED_PHASE
    return Path().apply {
        for (i in 0..points) {
            val distance = length * i / points
            val at = measure.getPosition(distance)
            val tangent = measure.getTangent(distance)
            val turn = 2 * PI.toFloat() * i / points
            val push =
                amplitude *
                    (
                        WAVE_A * sin(WAVE_A_TIMES * turn + phase) +
                            WAVE_B * sin(WAVE_B_TIMES * turn + phase * WAVE_B_PHASE) +
                            WAVE_C * sin(WAVE_C_TIMES * turn + phase * WAVE_C_PHASE)
                    )
            // Along the edge's normal, the tangent turned a quarter.
            val x = at.x - tangent.y * push
            val y = at.y + tangent.x * push
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

private const val SOFT_STEPS = 6
private const val HALO_RINGS = 4
private const val HALO_REACH = 1.6f
private const val HALO_ALPHA = 0.5f
private const val SHADOW_SEED = 7_919
private const val MIN_WOBBLE_POINTS = 24
private const val WOBBLE_STEP = 6f
private const val SEED_PHASE = 1.618f

// Three waves of falling weight, fitting the edge 3, 7 and 13 times: a slow sway and a tremor.
private const val WAVE_A = 0.55f
private const val WAVE_A_TIMES = 3f
private const val WAVE_B = 0.3f
private const val WAVE_B_TIMES = 7f
private const val WAVE_B_PHASE = 2.1f
private const val WAVE_C = 0.15f
private const val WAVE_C_TIMES = 13f
private const val WAVE_C_PHASE = 0.7f
