package io.ntole.kvizic.design.skin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * How a skin makes a thing stand off the page, and what a press does to it. A part picks how high each
 * of its things stands ([SurfaceLook]); the depth draws it, so the same tile is a key with a side under
 * it in one skin and a sketch with a pencilled shadow in another.
 */
@Immutable
sealed interface SkinDepth {
    /** How high a raised thing stands at rest, while pressed, and once locked in: the depth's own scale. */
    val lift: Dp
    val pressedLift: Dp
    val lockedLift: Dp

    /**
     * A hard shadow, solid and outlined, offset from the face along [direction]. Straight down it joins
     * the face as the thing's side, as a physical key's; a press moves the face down over it.
     */
    @Immutable
    data class HardOffset(
        override val lift: Dp,
        override val pressedLift: Dp,
        override val lockedLift: Dp,
        val outline: Dp,
        val direction: DepthDirection = DepthDirection.Down,
    ) : SkinDepth

    /** A soft shadow under a face that stays put: a press only draws the shadow in. */
    @Immutable
    data class Soft(
        override val lift: Dp,
        override val pressedLift: Dp,
        override val lockedLift: Dp,
        val shadow: Color,
        val outline: Dp,
    ) : SkinDepth

    /**
     * Drawn by hand: every outline wobbles, by up to [wobble], in a pen [stroke] wide, and the shadow is
     * pencil hatching [hatchGap] apart, offset along [direction].
     */
    @Immutable
    data class Sketched(
        override val lift: Dp,
        override val pressedLift: Dp,
        override val lockedLift: Dp,
        val stroke: Dp,
        val wobble: Dp,
        val hatchGap: Dp,
        val direction: DepthDirection = DepthDirection.DownRight,
    ) : SkinDepth
}

/** Which way a shadow falls, as how far across and down it goes for each unit of height. */
@Immutable
data class DepthDirection(
    val x: Float,
    val y: Float,
) {
    companion object {
        /** Straight down, as under a light hung right overhead. */
        val Down: DepthDirection = DepthDirection(0f, 1f)

        /** Down and to the right, as under a desk lamp over the left shoulder. */
        val DownRight: DepthDirection = DepthDirection(1f, 1f)
    }
}
