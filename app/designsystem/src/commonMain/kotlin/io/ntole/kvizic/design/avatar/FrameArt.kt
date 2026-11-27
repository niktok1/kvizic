package io.ntole.kvizic.design.avatar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.ntole.kvizic.design.skin.Metal
import io.ntole.kvizic.design.skin.Metals
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The frames an avatar may wear in place of the plain ring everybody has, by id: a ladder of metals, each richer
 * than the one before, drawn by hand round the avatar's edge in the skin's [Metals] and its ink, so one set of
 * drawings wears every skin. Iron is a plain dark ring, bronze is riveted, silver engraved, gold set with gems,
 * platinum wreathed in laurels, and diamond cut in facets under a crest. A frame stays inside the avatar's box and
 * never moves the animal; any other id, a newer server's, draws nothing, so the plain ring shows.
 */
object FrameArt {
    const val IRON: String = "frame.iron"
    const val BRONZE: String = "frame.bronze"
    const val SILVER: String = "frame.silver"
    const val GOLD: String = "frame.gold"
    const val PLATINUM: String = "frame.platinum"
    const val DIAMOND: String = "frame.diamond"

    /** The frames this build draws, from the cheapest to the richest. */
    val DRAWN: List<String> = listOf(IRON, BRONZE, SILVER, GOLD, PLATINUM, DIAMOND)

    /** Whether [id] has a drawing of its own here. */
    fun isDrawn(id: String): Boolean = id in DRAWN

    /**
     * Frame [id] round the edge of this scope, an avatar's box: over the [band], in pixels, its plain ring takes,
     * and on a large avatar a little more, in [metals] edged in [ink]. Nothing for an id not drawn here.
     */
    fun DrawScope.drawFrame(
        id: String,
        band: Float,
        metals: Metals,
        ink: Color,
    ) {
        val outer = size.minDimension / 2
        val ring = Ring(outer, max(band, outer * WIDE), metals, ink)
        when (id) {
            IRON -> iron(ring)
            BRONZE -> bronze(ring)
            SILVER -> silver(ring)
            GOLD -> gold(ring)
            PLATINUM -> platinum(ring)
            DIAMOND -> diamond(ring)
        }
    }

    private class Ring(
        val outer: Float,
        val width: Float,
        val metals: Metals,
        val ink: Color,
    ) {
        val middle: Float = outer - width / 2
        val inner: Float = outer - width
        val line: Float = width * OUTLINE
        val metal: Float = width - line * 2

        fun count(step: Float): Int = max(LEAST, (2 * PI * middle / step).roundToInt())
    }

    private fun DrawScope.band(
        ring: Ring,
        metal: Metal,
    ) {
        drawCircle(ring.ink, ring.middle, style = Stroke(ring.width))
        drawCircle(metal.face, ring.middle, style = Stroke(ring.metal))
        val bevel = ring.metal * BEVEL
        drawCircle(metal.shade, ring.inner + ring.line + bevel / 2, style = Stroke(bevel))
    }

    private fun DrawScope.gloss(
        ring: Ring,
        strength: Float,
    ) {
        val radius = ring.middle + ring.metal * GLOSS_OUT
        drawArc(
            ring.metals.shine.copy(alpha = strength),
            startAngle = GLOSS_FROM,
            sweepAngle = GLOSS_SWEEP,
            useCenter = false,
            topLeft = center - Offset(radius, radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(ring.metal * GLOSS_WIDTH, cap = StrokeCap.Round),
        )
    }

    private fun DrawScope.iron(ring: Ring) {
        band(ring, ring.metals.iron)
        gloss(ring, DULL)
    }

    private fun DrawScope.bronze(ring: Ring) {
        val bronze = ring.metals.bronze
        band(ring, bronze)
        gloss(ring, DULL)
        repeat(RIVETS) { i ->
            val at = point(ring.middle, i * FULL_TURN / RIVETS + HALF_STEP * FULL_TURN / RIVETS)
            val radius = ring.width * RIVET
            drawCircle(ring.ink, radius + ring.line / 2, at)
            drawCircle(bronze.shade, radius, at)
            drawCircle(bronze.face, radius * RIVET_DOME, at - Offset(radius, radius) * RIVET_LIT)
            drawCircle(ring.metals.shine, radius * GLINT, at - Offset(radius, radius) * GLINT_AT)
        }
    }

    private fun DrawScope.silver(ring: Ring) {
        val silver = ring.metals.silver
        band(ring, silver)
        rope(ring, silver.shade)
        gloss(ring, POLISHED)
    }

    private fun DrawScope.rope(
        ring: Ring,
        color: Color,
    ) {
        val twists = ring.count(ring.width * TWIST_STEP)
        val lean = degrees(ring.width * TWIST_LEAN / ring.middle)
        val reach = ring.metal * TWIST_REACH
        repeat(twists) { i ->
            val angle = i * FULL_TURN / twists
            drawLine(
                color,
                point(ring.middle + reach, angle - lean),
                point(ring.middle - reach, angle + lean),
                strokeWidth = ring.width * TWIST_PEN,
                cap = StrokeCap.Round,
            )
        }
    }

    private fun DrawScope.gold(ring: Ring) {
        val gold = ring.metals.gold
        band(ring, gold)
        beads(ring, gold.face)
        gloss(ring, POLISHED)
        gems(ring, gold, ring.metals.gem, GEMS)
    }

    private fun DrawScope.beads(
        ring: Ring,
        color: Color,
    ) {
        val beads = ring.count(ring.width * BEAD_STEP)
        val at = ring.inner + ring.line + ring.metal * BEVEL / 2
        repeat(beads) { i -> drawCircle(color, ring.width * BEAD, point(at, i * FULL_TURN / beads)) }
    }

    private fun DrawScope.gems(
        ring: Ring,
        setting: Metal,
        stone: Color,
        angles: List<Float>,
    ) {
        val radius = ring.width * GEM
        angles.forEach { angle ->
            val at = point(ring.middle, angle)
            drawCircle(ring.ink, radius + ring.line / 2, at)
            drawCircle(setting.shade, radius, at)
            drawCircle(stone, radius * GEM_STONE, at)
            drawCircle(ring.metals.shine, radius * GLINT, at - Offset(radius, radius) * GLINT_AT)
        }
    }

    private fun DrawScope.platinum(ring: Ring) {
        val platinum = ring.metals.platinum
        band(ring, platinum)
        rope(ring, platinum.shade)
        gloss(ring, POLISHED)
        laurels(ring, platinum)
        gems(ring, platinum, ring.metals.gem, TOP_AND_FOOT)
    }

    private fun DrawScope.laurels(
        ring: Ring,
        metal: Metal,
    ) {
        val spread = degrees(ring.width * LEAF_LENGTH / ring.middle)
        val pairs = max(LEAST_LEAVES, ((LAUREL_TO - LAUREL_FROM) / spread * LEAF_DENSITY).roundToInt())
        val pen = Stroke(ring.line, join = StrokeJoin.Round)
        listOf(1f, -1f).forEach { side ->
            repeat(pairs) { k ->
                val angle = QUARTER_TURN + side * (LAUREL_FROM + (LAUREL_TO - LAUREL_FROM) * k / pairs)
                val base = point(ring.middle, angle)
                listOf(LEAF_OUT, -LEAF_IN).forEach { tilt ->
                    val tip = point(ring.middle + tilt * ring.width, angle + side * spread)
                    val leaf = leaf(base, tip, ring.width * LEAF_WIDTH)
                    drawPath(leaf, metal.face)
                    drawLine(metal.shade, base, tip, strokeWidth = ring.line, cap = StrokeCap.Round)
                    drawPath(leaf, ring.ink, style = pen)
                }
            }
        }
    }

    private fun DrawScope.diamond(ring: Ring) {
        val diamond = ring.metals.diamond
        facets(ring, diamond)
        laurels(ring, diamond)
        gems(ring, diamond, ring.metals.gem, FOOT)
        SPARKLES.forEach { angle -> sparkle(point(ring.middle, angle), ring.width * SPARKLE, ring.metals.shine) }
        crest(ring, diamond)
    }

    private fun DrawScope.facets(
        ring: Ring,
        diamond: Metal,
    ) {
        drawCircle(ring.ink, ring.middle, style = Stroke(ring.width))
        drawCircle(diamond.face, ring.middle, style = Stroke(ring.metal))
        val facets = ring.count(ring.width * FACET_STEP) / 2 * 2
        val outside = ring.outer - ring.line
        val inside = ring.inner + ring.line
        val step = FULL_TURN / facets
        repeat(facets) { i ->
            val from = point(inside, i * step)
            val to = point(inside, (i + 1) * step)
            val tip = point(outside, (i + HALF_STEP) * step)
            drawPath(triangle(from, to, tip), diamond.shade)
            if (i % 2 == 0) {
                drawPath(triangle(tip, point(outside, (i + 1) * step), to), ring.metals.shine.copy(alpha = FACET_LIGHT))
            }
        }
    }

    private fun DrawScope.sparkle(
        at: Offset,
        reach: Float,
        color: Color,
    ) {
        val path =
            Path().apply {
                repeat(SPARKLE_POINTS * 2) { k ->
                    val length = if (k % 2 == 0) reach else reach * SPARKLE_WAIST
                    val point = at + polar(length, k * FULL_TURN / (SPARKLE_POINTS * 2))
                    if (k == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
                close()
            }
        drawPath(path, color)
    }

    private fun DrawScope.crest(
        ring: Ring,
        diamond: Metal,
    ) {
        val top = center.y - ring.outer + ring.line
        val x = center.x
        val w = ring.outer * WIDE
        val girdle = top + w * CREST_GIRDLE
        val culet = top + w * CREST_DEPTH
        val table = w * CREST_TABLE
        val half = w * CREST_HALF
        listOf(1f, -1f).forEach { side ->
            val wing =
                Path().apply {
                    moveTo(x + side * half * WING_FROM, girdle)
                    quadraticTo(x + side * half * WING_BEND, top, x + side * half * WING_TIP, top + w * WING_TIP_DROP)
                    quadraticTo(
                        x + side * half * WING_BEND,
                        girdle + w * WING_FOOT,
                        x + side * half * WING_FROM,
                        culet - w,
                    )
                    close()
                }
            drawPath(wing, diamond.shade)
            drawPath(wing, ring.ink, style = Stroke(ring.line, join = StrokeJoin.Round))
        }
        val stone =
            Path().apply {
                moveTo(x - table, top)
                lineTo(x + table, top)
                lineTo(x + half, girdle)
                lineTo(x, culet)
                lineTo(x - half, girdle)
                close()
            }
        drawPath(stone, diamond.face)
        drawPath(triangle(Offset(x - half, girdle), Offset(x, girdle), Offset(x, culet)), diamond.shade)
        drawPath(
            triangle(Offset(x - table, top), Offset(x - half, girdle), Offset(x - table / 2, girdle)),
            diamond.shade,
        )
        drawPath(
            triangle(Offset(x + table / 2, top), Offset(x + table, top), Offset(x + half, girdle)),
            ring.metals.shine,
        )
        drawLine(ring.ink, Offset(x - half, girdle), Offset(x + half, girdle), strokeWidth = ring.line / 2)
        drawPath(stone, ring.ink, style = Stroke(ring.line, join = StrokeJoin.Round))
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

    private fun triangle(
        a: Offset,
        b: Offset,
        c: Offset,
    ): Path =
        Path().apply {
            moveTo(a.x, a.y)
            lineTo(b.x, b.y)
            lineTo(c.x, c.y)
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
    private const val HALF_STEP = 0.5f
    private const val LEAST = 6

    private const val WIDE = 0.17f
    private const val OUTLINE = 0.13f
    private const val BEVEL = 0.38f

    private const val GLOSS_OUT = 0.18f
    private const val GLOSS_FROM = 195f
    private const val GLOSS_SWEEP = 60f
    private const val GLOSS_WIDTH = 0.28f
    private const val DULL = 0.3f
    private const val POLISHED = 0.75f

    private const val RIVETS = 8
    private const val RIVET = 0.26f
    private const val RIVET_DOME = 0.62f
    private const val RIVET_LIT = 0.18f
    private const val GLINT = 0.3f
    private const val GLINT_AT = 0.35f

    private const val TWIST_STEP = 0.75f
    private const val TWIST_LEAN = 0.22f
    private const val TWIST_REACH = 0.36f
    private const val TWIST_PEN = 0.11f

    private const val BEAD_STEP = 0.7f
    private const val BEAD = 0.09f

    private const val GEM = 0.4f
    private const val GEM_STONE = 0.7f
    private val GEMS = listOf(0f, 90f, 180f, 270f)
    private val TOP_AND_FOOT = listOf(90f, 270f)
    private val FOOT = listOf(90f)

    private const val LEAF_LENGTH = 1.6f
    private const val LEAF_WIDTH = 0.5f
    private const val LEAF_OUT = 0.3f
    private const val LEAF_IN = 0.75f
    private const val LEAF_DENSITY = 1.15f
    private const val LEAST_LEAVES = 3
    private const val LAUREL_FROM = 22f
    private const val LAUREL_TO = 128f

    private const val FACET_STEP = 1.3f
    private const val FACET_LIGHT = 0.55f
    private const val SPARKLE = 0.55f
    private const val SPARKLE_WAIST = 0.28f
    private const val SPARKLE_POINTS = 4
    private val SPARKLES = listOf(315f, 150f, 30f)

    private const val CREST_GIRDLE = 0.7f
    private const val CREST_DEPTH = 2.3f
    private const val CREST_TABLE = 0.7f
    private const val CREST_HALF = 1.15f
    private const val WING_FROM = 0.8f
    private const val WING_BEND = 1.8f
    private const val WING_TIP = 2.4f
    private const val WING_TIP_DROP = 0.35f
    private const val WING_FOOT = 0.35f
}
