package io.ntole.kvizic.design.skins.buzzers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import io.ntole.kvizic.design.skin.Backdrop
import kotlin.math.exp
import kotlin.math.pow

/**
 * The stage: near black, one warm spotlight falling from above the top edge into a pool of light on the
 * floor, the floor drawn as halftone dots that grow toward the front of the stage and brighten in the
 * pool, and the corners fading darker. Every light is the spotlight's warm white at a few percent, and
 * the brightest place, a dot in the middle of the pool, still keeps the page's text at AA
 * ([colors], `SkinContrastTest`).
 */
internal object BuzzersBackdrop : Backdrop {
    /** The beam's layers, each as faint as the whole beam's middle over their number. */
    private val layer = BEAM / BEAM_LAYERS

    override val colors: List<Color> =
        listOf(
            // The beam's middle at the top, every layer over it, and where it meets the floor.
            Spotlight.over(Stage, stacked(*FloatArray(BEAM_LAYERS) { layer })),
            Spotlight.over(Stage, stacked(*FloatArray(BEAM_LAYERS) { layer * BEAM_FADE }, POOL)),
            // The brightest the stage gets: a dot in the middle of the pool, under the beam.
            Spotlight.over(Stage, stacked(*FloatArray(BEAM_LAYERS) { layer * BEAM_FADE }, POOL, DOT)),
        )

    override fun DrawScope.draw() {
        val w = size.width
        val h = size.height
        drawRect(Stage)
        val poolY = h * POOL_Y
        // The beam, from a lamp above the top edge down to the pool: cones one inside another, each as
        // faint as the next, so its edge is soft and its middle brightest.
        for (i in 0 until BEAM_LAYERS) {
            val t = i / (BEAM_LAYERS - 1f)
            beam(
                topHalf = BEAM_TOP + (CORE_TOP - BEAM_TOP) * t,
                bottomHalf = BEAM_BOTTOM + (CORE_BOTTOM - BEAM_BOTTOM) * t,
                poolY = poolY,
                alpha = layer,
            )
        }
        // The pool: an ellipse of light on the floor, drawn as a circle squashed to the floor's angle.
        val poolRadius = w * POOL_WIDTH
        scale(scaleX = 1f, scaleY = POOL_FLAT, pivot = Offset(w / 2, poolY)) {
            drawCircle(
                Brush.radialGradient(
                    0f to Spotlight.copy(alpha = POOL),
                    1f to Color.Transparent,
                    center = Offset(w / 2, poolY),
                    radius = poolRadius,
                ),
                radius = poolRadius,
                center = Offset(w / 2, poolY),
            )
        }
        halftoneFloor(poolY)
        // The corners fall away into the dark of the house.
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent,
                VIGNETTE_FROM to Color.Transparent,
                1f to Ink.copy(alpha = VIGNETTE),
                center = Offset(w / 2, h * VIGNETTE_Y),
                radius = maxOf(w, h) * VIGNETTE_REACH,
            ),
        )
    }

    /** A cone of light, [topHalf] of the width either side of the middle at the top, [bottomHalf] at the pool. */
    private fun DrawScope.beam(
        topHalf: Float,
        bottomHalf: Float,
        poolY: Float,
        alpha: Float,
    ) {
        val w = size.width
        val cone =
            Path().apply {
                moveTo(w * (0.5f - topHalf), -size.height * LAMP_ABOVE)
                lineTo(w * (0.5f + topHalf), -size.height * LAMP_ABOVE)
                lineTo(w * (0.5f + bottomHalf), poolY)
                lineTo(w * (0.5f - bottomHalf), poolY)
                close()
            }
        drawPath(
            cone,
            Brush.verticalGradient(
                0f to Spotlight.copy(alpha = alpha),
                1f to Spotlight.copy(alpha = alpha * BEAM_FADE),
                startY = 0f,
                endY = poolY,
            ),
        )
    }

    /**
     * The floor, from the horizon to the front of the stage: rows of dots closer together toward the
     * horizon, as a floor seen from the house is, each row shifted half a dot from the one before, the
     * dots larger toward the front and larger and brighter in the pool.
     */
    private fun DrawScope.halftoneFloor(poolY: Float) {
        val w = size.width
        val h = size.height
        val horizon = h * HORIZON
        val near = FLOOR_NEAR.dp.toPx()
        val far = FLOOR_FAR.dp.toPx()
        val dotNear = DOT_NEAR.dp.toPx()
        val dotFar = DOT_FAR.dp.toPx()
        for (row in 0 until FLOOR_ROWS) {
            val t = (row + 1).toFloat() / FLOOR_ROWS
            val y = horizon + t.pow(FLOOR_BUNCH) * (h - horizon)
            val spacing = far + (near - far) * t
            val radius = dotFar + (dotNear - dotFar) * t
            var x = if (row % 2 == 0) 0f else spacing / 2
            while (x <= w) {
                val dx = (x - w / 2) / (w * POOL_WIDTH)
                val dy = (y - poolY) / (h * POOL_DEPTH)
                val light = AMBIENT + (1f - AMBIENT) * exp(-(dx * dx + dy * dy) * POOL_FALLOFF)
                drawCircle(
                    Spotlight.copy(alpha = DOT * light),
                    radius * (DOT_SMALLEST + (1f - DOT_SMALLEST) * light),
                    Offset(x, y),
                )
                x += spacing
            }
        }
    }

    /** The one alpha that [alphas], laid one over another, come to. */
    private fun stacked(vararg alphas: Float): Float = 1f - alphas.fold(1f) { clear, a -> clear * (1f - a) }

    private fun Color.over(
        below: Color,
        alpha: Float,
    ): Color = copy(alpha = alpha).over(below)

    // The beam's alpha at its middle and top, fading to BEAM_FADE of it where it meets the floor, from
    // its outer edge's half widths at the top and bottom in to its core's.
    private const val BEAM = 0.1f
    private const val BEAM_LAYERS = 7
    private const val BEAM_FADE = 0.4f
    private const val BEAM_TOP = 0.13f
    private const val BEAM_BOTTOM = 0.56f
    private const val CORE_TOP = 0.03f
    private const val CORE_BOTTOM = 0.2f
    private const val LAMP_ABOVE = 0.06f

    private const val POOL = 0.07f
    private const val POOL_Y = 0.86f
    private const val POOL_WIDTH = 0.5f
    private const val POOL_FLAT = 0.2f
    private const val POOL_DEPTH = 0.1f
    private const val POOL_FALLOFF = 1.4f

    private const val HORIZON = 0.66f
    private const val FLOOR_ROWS = 18
    private const val FLOOR_BUNCH = 1.7f
    private const val FLOOR_NEAR = 11f
    private const val FLOOR_FAR = 3.5f
    private const val DOT_NEAR = 2.4f
    private const val DOT_FAR = 0.55f
    private const val DOT = 0.1f
    private const val DOT_SMALLEST = 0.35f
    private const val AMBIENT = 0.22f

    private const val VIGNETTE = 0.55f
    private const val VIGNETTE_FROM = 0.45f
    private const val VIGNETTE_Y = 0.42f
    private const val VIGNETTE_REACH = 0.82f
}
