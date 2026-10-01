package io.ntole.kvizic.design.skin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The colours the avatars are drawn in, by material rather than by animal, so a new animal reuses them:
 * the fox's fur is [orange], the owl's [tan]. A skin tints the whole set by changing these alone.
 */
@Immutable
data class AvatarPalette(
    /** The lines and the dark features: eyes, noses, outlines. */
    val ink: Color,
    /** Muzzles, cheeks, bellies, the whites of eyes. */
    val cream: Color,
    /** A nose's tip, a cheek's flush. */
    val blush: Color,
    val orange: Color,
    val orangeDark: Color,
    val brown: Color,
    val brownDark: Color,
    val tan: Color,
    val tanDark: Color,
    val grey: Color,
    val greyDark: Color,
    /** An owl's eyes, a bee, and whatever else of an animal shines. */
    val gold: Color,
    /** A frog, a tortoise. */
    val green: Color,
    val greenDark: Color,
    /** The disc behind the animal, and the silhouette drawn for an avatar this build cannot draw. */
    val disc: Color,
    val silhouette: Color,
)
