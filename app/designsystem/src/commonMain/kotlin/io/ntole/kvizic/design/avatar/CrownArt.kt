package io.ntole.kvizic.design.avatar

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import io.ntole.kvizic.design.skin.Metal
import io.ntole.kvizic.design.skin.Metals

/**
 * The crowns a host may wear above their avatar's head in place of the plain one everybody hosts in, by id: the
 * same ladder of metals as [FrameArt]'s, each richer than the one before, drawn by hand on a 24 by 24 grid in the
 * skin's [Metals] and its ink. Iron is plain, bronze riveted, silver tipped with balls and engraved, gold set with
 * gems, platinum five-pointed and pearled, and diamond cut in facets and tipped with stones. Any other id, a newer
 * server's, draws nothing here, so the plain crown shows.
 */
object CrownArt {
    const val IRON: String = "crown.iron"
    const val BRONZE: String = "crown.bronze"
    const val SILVER: String = "crown.silver"
    const val GOLD: String = "crown.gold"
    const val PLATINUM: String = "crown.platinum"
    const val DIAMOND: String = "crown.diamond"

    /** The crowns this build draws, from the cheapest to the richest. */
    val DRAWN: List<String> = listOf(IRON, BRONZE, SILVER, GOLD, PLATINUM, DIAMOND)

    /** Whether [id] has a drawing of its own here. */
    fun isDrawn(id: String): Boolean = id in DRAWN

    /**
     * Crown [id] over this scope, the box the plain crown stands in above a host's head, in [metals] edged in
     * [ink]. Nothing for an id not drawn here.
     */
    fun DrawScope.drawCrown(
        id: String,
        metals: Metals,
        ink: Color,
    ) {
        if (!isDrawn(id)) return
        val unit = size.minDimension / GRID
        withTransform({
            translate((size.width - size.minDimension) / 2, (size.height - size.minDimension) / 2)
            scale(unit, unit, pivot = Offset.Zero)
        }) {
            val pen = Stroke(LINE, join = StrokeJoin.Round, cap = StrokeCap.Round)
            when (id) {
                IRON -> plain(metals.iron, ink, pen)
                BRONZE -> bronze(metals, ink, pen)
                SILVER -> silver(metals, ink, pen)
                GOLD -> gold(metals, ink, pen)
                PLATINUM -> platinum(metals, ink, pen)
                DIAMOND -> diamond(metals, ink, pen)
            }
        }
    }

    private fun DrawScope.body(
        points: List<Offset>,
        metal: Metal,
        ink: Color,
        pen: Stroke,
    ) {
        val body =
            Path().apply {
                moveTo(BODY_LEFT, BODY_FOOT)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(BODY_RIGHT, BODY_FOOT)
                close()
            }
        drawPath(body, metal.face)
        drawPath(
            Path().apply {
                moveTo(MIDDLE, BODY_FOOT)
                points.drop(points.size / 2).forEach { lineTo(it.x, it.y) }
                lineTo(BODY_RIGHT, BODY_FOOT)
                close()
            },
            metal.shade.copy(alpha = SHADED),
        )
        drawPath(body, ink, style = pen)
    }

    private fun DrawScope.band(
        fill: Color,
        ink: Color,
        pen: Stroke,
    ) {
        val corner = CornerRadius(BAND_ROUND)
        val at = Offset(BAND_LEFT, BAND_TOP)
        val across = Size(BAND_RIGHT - BAND_LEFT, BAND_FOOT - BAND_TOP)
        drawRoundRect(fill, at, across, corner)
        drawRoundRect(ink, at, across, corner, style = pen)
    }

    private fun DrawScope.plain(
        iron: Metal,
        ink: Color,
        pen: Stroke,
    ) {
        body(THREE, iron, ink, pen)
        band(iron.shade, ink, pen)
    }

    private fun DrawScope.bronze(
        metals: Metals,
        ink: Color,
        pen: Stroke,
    ) {
        val bronze = metals.bronze
        body(THREE, bronze, ink, pen)
        streak(metals.shine)
        band(bronze.shade, ink, pen)
        BAND_STUDS.forEach { x ->
            drawCircle(bronze.face, RIVET, Offset(x, BAND_MIDDLE))
            drawCircle(metals.shine, RIVET * GLINT, Offset(x - RIVET * GLINT, BAND_MIDDLE - RIVET * GLINT))
        }
    }

    private fun DrawScope.silver(
        metals: Metals,
        ink: Color,
        pen: Stroke,
    ) {
        val silver = metals.silver
        body(THREE, silver, ink, pen)
        streak(metals.shine)
        band(silver.face, ink, pen)
        engraving(silver.shade)
        THREE_TIPS.forEach { ball(it, silver.face, metals.shine, ink, pen) }
    }

    private fun DrawScope.gold(
        metals: Metals,
        ink: Color,
        pen: Stroke,
    ) {
        val gold = metals.gold
        body(THREE, gold, ink, pen)
        streak(metals.shine)
        band(gold.face, ink, pen)
        gems(metals, ink)
        THREE_TIPS.forEach { ball(it, gold.face, metals.shine, ink, pen) }
    }

    private fun DrawScope.platinum(
        metals: Metals,
        ink: Color,
        pen: Stroke,
    ) {
        val platinum = metals.platinum
        body(FIVE, platinum, ink, pen)
        streak(metals.shine)
        band(platinum.face, ink, pen)
        engraving(platinum.shade)
        gems(metals, ink)
        kite(Offset(MIDDLE, HEART), HEART_KITE, metals.gem, metals.gem, metals.shine, ink)
        FIVE_TIPS.forEach { ball(it, metals.shine, metals.shine, ink, pen) }
    }

    private fun DrawScope.diamond(
        metals: Metals,
        ink: Color,
        pen: Stroke,
    ) {
        val diamond = metals.diamond
        body(FIVE, diamond, ink, pen)
        FIVE_VALLEYS.forEach { valley ->
            drawPath(
                triangle(valley, Offset(valley.x - FACET, BODY_FOOT), Offset(valley.x + FACET, BODY_FOOT)),
                metals.shine.copy(alpha = FACET_LIGHT),
            )
        }
        band(diamond.shade, ink, pen)
        gems(metals, ink)
        kite(Offset(MIDDLE, HEART), HEART_KITE * BIG_KITE, diamond.face, diamond.shade, metals.shine, ink)
        FIVE_TIPS.forEach { kite(it, TIP_KITE, diamond.face, diamond.shade, metals.shine, ink) }
    }

    private fun DrawScope.streak(shine: Color) {
        drawLine(shine.copy(alpha = STREAK_LIGHT), STREAK_FROM, STREAK_TO, strokeWidth = STREAK, cap = StrokeCap.Round)
    }

    private fun DrawScope.engraving(color: Color) {
        val path =
            Path().apply {
                moveTo(BAND_LEFT + ENGRAVING_IN, BAND_MIDDLE)
                var x = BAND_LEFT + ENGRAVING_IN
                var up = true
                while (x < BAND_RIGHT - ENGRAVING_IN - ZIG) {
                    x += ZIG
                    lineTo(x, BAND_MIDDLE + if (up) -ZAG else ZAG)
                    up = !up
                }
            }
        drawPath(path, color, style = Stroke(ENGRAVING, join = StrokeJoin.Round, cap = StrokeCap.Round))
    }

    private fun DrawScope.ball(
        at: Offset,
        fill: Color,
        shine: Color,
        ink: Color,
        pen: Stroke,
    ) {
        drawCircle(fill, BALL, at)
        drawCircle(ink, BALL, at, style = pen)
        drawCircle(shine, BALL * GLINT, at - Offset(BALL, BALL) * GLINT)
    }

    private fun DrawScope.gems(
        metals: Metals,
        ink: Color,
    ) {
        BAND_STUDS.forEach { x ->
            val at = Offset(x, BAND_MIDDLE)
            val radius = if (x == MIDDLE) GEM_BIG else GEM
            drawCircle(ink, radius + STONE_EDGE, at)
            drawCircle(metals.gem, radius, at)
            drawCircle(metals.shine, radius * GLINT, at - Offset(radius, radius) * GLINT)
        }
    }

    private fun DrawScope.kite(
        at: Offset,
        reach: Float,
        face: Color,
        shade: Color,
        shine: Color,
        ink: Color,
    ) {
        val top = Offset(at.x, at.y - reach)
        val right = Offset(at.x + reach * KITE_WIDE, at.y - reach * KITE_SHOULDER)
        val foot = Offset(at.x, at.y + reach)
        val left = Offset(at.x - reach * KITE_WIDE, at.y - reach * KITE_SHOULDER)
        val stone =
            Path().apply {
                moveTo(top.x, top.y)
                lineTo(right.x, right.y)
                lineTo(foot.x, foot.y)
                lineTo(left.x, left.y)
                close()
            }
        drawPath(stone, face)
        drawPath(triangle(Offset(at.x, left.y), right, foot), shade)
        drawPath(triangle(top, Offset(at.x, left.y), left), shine.copy(alpha = FACET_LIGHT))
        drawPath(stone, ink, style = Stroke(LINE * KITE_LINE, join = StrokeJoin.Round))
    }

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

    private const val GRID = 24f
    private const val MIDDLE = GRID / 2
    private const val LINE = 1.1f

    private const val BODY_LEFT = 4.6f
    private const val BODY_RIGHT = 19.4f
    private const val BODY_FOOT = 16f
    private const val SHADED = 0.45f

    private const val BAND_LEFT = 4f
    private const val BAND_RIGHT = 20f
    private const val BAND_TOP = 15.4f
    private const val BAND_FOOT = 20.4f
    private const val BAND_MIDDLE = (BAND_TOP + BAND_FOOT) / 2
    private const val BAND_ROUND = 1.2f
    private val BAND_STUDS = listOf(7.4f, MIDDLE, 16.6f)

    private val THREE =
        listOf(Offset(3f, 7.5f), Offset(7.6f, 11.6f), Offset(12f, 4f), Offset(16.4f, 11.6f), Offset(21f, 7.5f))
    private val THREE_TIPS = listOf(THREE[0], THREE[2], THREE[4])
    private val FIVE =
        listOf(
            Offset(2.6f, 8f),
            Offset(6.2f, 12.2f),
            Offset(7.9f, 6f),
            Offset(10.2f, 11.4f),
            Offset(12f, 3.6f),
            Offset(13.8f, 11.4f),
            Offset(16.1f, 6f),
            Offset(17.8f, 12.2f),
            Offset(21.4f, 8f),
        )
    private val FIVE_TIPS = FIVE.filterIndexed { i, _ -> i % 2 == 0 }
    private val FIVE_VALLEYS = FIVE.filterIndexed { i, _ -> i % 2 == 1 }

    private const val RIVET = 1.05f
    private const val GLINT = 0.4f
    private const val BALL = 1.5f
    private const val GEM = 1.25f
    private const val GEM_BIG = 1.6f
    private const val STONE_EDGE = 0.45f

    private const val HEART = 11.6f
    private const val HEART_KITE = 2.2f
    private const val BIG_KITE = 1.3f
    private const val TIP_KITE = 1.9f
    private const val KITE_WIDE = 0.8f
    private const val KITE_SHOULDER = 0.25f
    private const val KITE_LINE = 0.7f

    private const val FACET = 1.6f
    private const val FACET_LIGHT = 0.6f

    private const val STREAK = 0.9f
    private const val STREAK_LIGHT = 0.7f
    private val STREAK_FROM = Offset(5.8f, 14.2f)
    private val STREAK_TO = Offset(4.9f, 10.6f)

    private const val ENGRAVING = 0.55f
    private const val ENGRAVING_IN = 1.4f
    private const val ZIG = 1.3f
    private const val ZAG = 0.9f
}
