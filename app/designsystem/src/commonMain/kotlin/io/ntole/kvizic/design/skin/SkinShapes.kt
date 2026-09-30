package io.ntole.kvizic.design.skin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape

/** The outline of each kind of thing a skin draws. A skin's depth draws round it, so it may be any [Shape]. */
@Immutable
data class SkinShapes(
    val tile: Shape,
    val button: Shape,
    val heroButton: Shape,
    val roundButton: Shape,
    val panel: Shape,
    val chip: Shape,
    val flap: Shape,
    val letterMark: Shape,
    val avatar: Shape,
    val podium: Shape,
    val seat: Shape,
)
