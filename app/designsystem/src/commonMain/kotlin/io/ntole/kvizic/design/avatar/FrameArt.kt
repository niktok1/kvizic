package io.ntole.kvizic.design.avatar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.ntole.kvizic.design.skin.AvatarPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The frames an avatar may wear in place of its seat's plain ring, by id, drawn by hand round the avatar's
 * edge in the seat's colour, with the ink and the cream of the skin's [AvatarPalette], so one set of drawings
 * wears every skin. A frame stays inside the avatar's box and never moves the animal; any other id, a newer
 * server's, draws no frame.
 */
object FrameArt {
    const val DOUBLE: String = "frame.double"
    const val DASHES: String = "frame.dashes"
    const val BULBS: String = "frame.bulbs"
    const val STARS: String = "frame.stars"
    const val RAYS: String = "frame.rays"
    const val LAUREL: String = "frame.laurel"

    /** The frames this build draws. */
    val DRAWN: List<String> = listOf(DOUBLE, DASHES, BULBS, STARS, RAYS, LAUREL)

    /** Whether [id] has a drawing of its own here. */
    fun isDrawn(id: String): Boolean = id in DRAWN

    /**
     * Frame [id] round the edge of this scope, an avatar's box: over the [band], in pixels, its ring takes, and on
     * a large avatar a little more, in [seat] and [palette]'s ink and cream. Nothing for an id not drawn here.
     */
    fun DrawScope.drawFrame(
        id: String,
        band: Float,
        seat: Color,
        palette: AvatarPalette,
    ) {
        val outer = size.minDimension / 2
        val ring = Ring(outer, max(band, outer * WIDE), seat, palette.ink, palette.cream)
        when (id) {
            DOUBLE -> double(ring)
            DASHES -> dashes(ring)
            BULBS -> bulbs(ring)
            STARS -> stars(ring)
            RAYS -> rays(ring)
            LAUREL -> laurel(ring)
        }
    }

    private class Ring(
        val outer: Float,
        val width: Float,
        val seat: Color,
        val ink: Color,
        val light: Color,
    ) {
        val middle: Float = outer - width / 2
        val inner: Float = outer - width
        val line: Float = width * OUTLINE

        fun count(step: Float): Int = max(LEAST, (2 * PI * middle / step).roundToInt())
    }

    private fun DrawScope.housing(ring: Ring) = drawCircle(ring.ink, ring.middle, style = Stroke(ring.width))

    private fun DrawScope.double(ring: Ring) {
        housing(ring)
        val rim = Stroke(ring.width * DOUBLE_RIM)
        drawCircle(ring.seat, ring.outer - ring.width * DOUBLE_OUTER, style = rim)
        drawCircle(ring.seat, ring.outer - ring.width * DOUBLE_INNER, style = rim)
    }

    private fun DrawScope.dashes(ring: Ring) {
        housing(ring)
        val dashes = ring.count(ring.width * DASH_STEP)
        val pen = ring.width * DASH_WIDTH
        val cap = degrees(pen / 2 / ring.middle)
        val step = FULL_TURN / dashes
        val corner = center - Offset(ring.middle, ring.middle)
        val across = Size(ring.middle * 2, ring.middle * 2)
        repeat(dashes) { i ->
            drawArc(
                ring.seat,
                startAngle = i * step + cap - QUARTER_TURN,
                sweepAngle = step * DASH_SHARE - cap * 2,
                useCenter = false,
                topLeft = corner,
                size = across,
                style = Stroke(pen, cap = StrokeCap.Round),
            )
        }
    }

    private fun DrawScope.bulbs(ring: Ring) {
        housing(ring)
        val bulbs = ring.count(ring.width * BULB_STEP)
        val radius = ring.width * BULB
        repeat(bulbs) { i ->
            val at = point(ring.middle, i * FULL_TURN / bulbs)
            drawCircle(ring.seat, radius, at)
            drawCircle(ring.ink, radius, at, style = Stroke(radius * BULB_RIM))
            drawCircle(ring.light, radius * GLINT, at - Offset(radius * GLINT_AT, radius * GLINT_AT))
        }
    }

    private fun DrawScope.stars(ring: Ring) {
        housing(ring)
        val stars = ring.count(ring.width * STAR_STEP)
        repeat(stars) { i ->
            val angle = i * FULL_TURN / stars - QUARTER_TURN
            drawPath(star(point(ring.middle, angle), ring.width * STAR, angle), ring.seat)
        }
    }

    private fun DrawScope.rays(ring: Ring) {
        housing(ring)
        val base = ring.outer - ring.width * RAY_BASE
        drawCircle(ring.seat, (ring.inner + base) / 2, style = Stroke(base - ring.inner))
        val rays = ring.count(ring.width * RAY_STEP) / 2 * 2
        val half = FULL_TURN / rays * RAY_SPREAD
        repeat(rays) { i ->
            val angle = i * FULL_TURN / rays - QUARTER_TURN
            val tip = if (i % 2 == 0) ring.outer else ring.outer - ring.width * RAY_SHORT
            val from = point(base - ring.line, angle - half)
            val to = point(base - ring.line, angle + half)
            val end = point(tip, angle)
            val ray =
                Path().apply {
                    moveTo(from.x, from.y)
                    lineTo(end.x, end.y)
                    lineTo(to.x, to.y)
                    close()
                }
            drawPath(ray, ring.seat)
        }
    }

    private fun DrawScope.laurel(ring: Ring) {
        housing(ring)
        val spread = degrees(ring.width * LEAF_LENGTH / ring.middle)
        val pairs = max(LEAST_LEAVES, ((LAUREL_TO - LAUREL_FROM) / spread * LEAF_DENSITY).roundToInt())
        val pen = Stroke(ring.line, join = StrokeJoin.Round)
        listOf(1f, -1f).forEach { side ->
            repeat(pairs) { k ->
                val angle = QUARTER_TURN + side * (LAUREL_FROM + (LAUREL_TO - LAUREL_FROM) * k / pairs)
                val base = point(ring.middle, angle)
                listOf(1f, -1f).forEach { outwards ->
                    val tip = point(ring.middle + outwards * ring.width * LEAF_TILT, angle + side * spread)
                    val leaf = leaf(base, tip, ring.width * LEAF_WIDTH)
                    drawPath(leaf, ring.seat)
                    drawPath(leaf, ring.ink, style = pen)
                }
            }
        }
    }

    private fun DrawScope.point(
        radius: Float,
        degrees: Float,
    ): Offset = center + polar(radius, degrees)

    private fun polar(
        radius: Float,
        degrees: Float,
    ): Offset {
        val radians = degrees * PI / HALF_TURN
        return Offset(cos(radians).toFloat() * radius, sin(radians).toFloat() * radius)
    }

    private fun degrees(radians: Float): Float = (radians * HALF_TURN / PI).toFloat()

    private fun star(
        at: Offset,
        reach: Float,
        degrees: Float,
    ): Path =
        Path().apply {
            val corners = STAR_POINTS * 2
            repeat(corners) { k ->
                val point = at + polar(if (k % 2 == 0) reach else reach * STAR_INNER, degrees + k * FULL_TURN / corners)
                if (k == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
            close()
        }

    private fun leaf(
        base: Offset,
        tip: Offset,
        width: Float,
    ): Path {
        val middle = (base + tip) / 2f
        val along = tip - base
        val across = Offset(-along.y, along.x) / along.getDistance() * width
        return Path().apply {
            moveTo(base.x, base.y)
            quadraticTo(middle.x + across.x, middle.y + across.y, tip.x, tip.y)
            quadraticTo(middle.x - across.x, middle.y - across.y, base.x, base.y)
            close()
        }
    }

    private const val FULL_TURN = 360f
    private const val HALF_TURN = 180.0
    private const val QUARTER_TURN = 90f
    private const val LEAST = 6

    private const val WIDE = 0.17f
    private const val OUTLINE = 0.16f

    private const val DOUBLE_RIM = 0.3f
    private const val DOUBLE_OUTER = 0.29f
    private const val DOUBLE_INNER = 0.71f

    private const val DASH_STEP = 2.2f
    private const val DASH_WIDTH = 0.55f
    private const val DASH_SHARE = 0.55f

    private const val BULB_STEP = 1.5f
    private const val BULB = 0.4f
    private const val BULB_RIM = 0.22f
    private const val GLINT = 0.3f
    private const val GLINT_AT = 0.32f

    private const val STAR_STEP = 1.2f
    private const val STAR = 0.5f
    private const val STAR_INNER = 0.48f
    private const val STAR_POINTS = 5

    private const val RAY_BASE = 0.62f
    private const val RAY_STEP = 1.1f
    private const val RAY_SPREAD = 0.3f
    private const val RAY_SHORT = 0.22f

    private const val LEAF_LENGTH = 1.4f
    private const val LEAF_WIDTH = 0.5f
    private const val LEAF_TILT = 0.3f
    private const val LEAF_DENSITY = 1.3f
    private const val LEAST_LEAVES = 3
    private const val LAUREL_FROM = 14f
    private const val LAUREL_TO = 140f
}
