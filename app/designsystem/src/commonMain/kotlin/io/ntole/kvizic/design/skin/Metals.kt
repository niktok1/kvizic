package io.ntole.kvizic.design.skin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The metals the shop's frames and crowns are struck in, from the cheapest to the richest, and the coin's gold.
 * A skin tints them all by changing these alone.
 */
@Immutable
data class Metals(
    val iron: Metal,
    val bronze: Metal,
    val silver: Metal,
    val gold: Metal,
    val platinum: Metal,
    val diamond: Metal,
    /** The stones set in gold and the richer metals. */
    val gem: Color,
    /** The light a polished metal catches. */
    val shine: Color,
)

/** One metal: its [face], and the [shade] of its bevels, its engraving and a coin's rim. */
@Immutable
data class Metal(
    val face: Color,
    val shade: Color,
)
