package io.ntole.kvizic.design.skin

import androidx.compose.runtime.Immutable
import io.ntole.kvizic.design.font.Face

/**
 * A whole look of the game: not only its colours but its type, its shapes, how things stand off the
 * page, how they move, how each component is drawn and the art under every screen. Screens and
 * components read a skin's tokens and never write a colour, a length or a type size of their own, so a
 * new skin is one package of values and parts, and one line in [Skins.ALL].
 */
@Immutable
data class Skin(
    /** A stable id, to be kept on a device or sent on the wire. */
    val id: String,
    /** The skin's name as the developer's showcase lists it; a player-facing name belongs to the strings. */
    val name: String,
    val colors: SkinColors,
    val fonts: SkinFonts,
    val type: SkinTypeScale,
    val shapes: SkinShapes,
    val depth: SkinDepth,
    val space: SkinSpace,
    val motion: SkinMotion,
    val parts: SkinParts,
    val backdrop: Backdrop,
    val avatarPalette: AvatarPalette,
) {
    /** This skin with its display face swapped for [face], to compare faces in one look. */
    fun withDisplay(face: Face): Skin = copy(fonts = fonts.copy(display = face))
}

/** The two faces a skin sets its text in: one to show off with, one to read. */
@Immutable
data class SkinFonts(
    val display: Face,
    val body: Face,
)
