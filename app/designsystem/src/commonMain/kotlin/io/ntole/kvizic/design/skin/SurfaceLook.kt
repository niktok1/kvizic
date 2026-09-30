package io.ntole.kvizic.design.skin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp

/**
 * How one raised thing looks at rest in one state, as a part decides it and its skin's depth draws it:
 * a tile idle or locked in, a button, a panel. A change of look settles into the new one over the
 * skin's `settle`, in the draw alone.
 */
@Immutable
data class SurfaceLook(
    val fill: Color,
    val side: Color,
    val outline: Color,
    val shape: Shape,
    /** The most it ever stands: the room the layout keeps for its side or shadow, so a press moves nothing else. */
    val height: Dp,
    /** How high it stands now, at rest in this state. */
    val rest: Dp,
    /** How high it stands while a finger holds it. */
    val pressed: Dp,
    /** A halo round it, for something lit: an answer shown right. Unspecified for none. */
    val glow: Color = Color.Unspecified,
    /** What the skin draws on the face, over its fill and under its content: an enamel's sheen, tape. */
    val decor: SurfaceDecor = SurfaceDecor.None,
    /** Varies what a hand-drawn depth draws, so two tiles side by side do not wobble alike. */
    val seed: Int = 0,
)

/**
 * A skin's drawing on a surface's face, given the face's [size] and [outline]. An object or a value of
 * the skin's, never a lambda made anew, so two looks that draw the same are equal.
 */
fun interface SurfaceDecor {
    fun DrawScope.decorate(
        size: Size,
        outline: Outline,
    )

    companion object {
        val None: SurfaceDecor = SurfaceDecor { _, _ -> }
    }
}
