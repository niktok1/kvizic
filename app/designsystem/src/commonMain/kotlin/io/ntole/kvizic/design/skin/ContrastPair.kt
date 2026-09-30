package io.ntole.kvizic.design.skin

import androidx.compose.ui.graphics.Color

/** What a colour pair carries, and so the least contrast WCAG AA asks of it. */
enum class ContrastUse(
    val least: Float,
) {
    /** Text of any size: 4.5 to 1. */
    TEXT(4.5f),

    /** Text 18.66 or larger and bold, or 24 or larger: 3 to 1. */
    LARGE_TEXT(3f),

    /** An icon, or the edge of a thing a player needs to see: 3 to 1. */
    GRAPHIC(3f),
}

/** A colour a skin draws [foreground] on [background], for [use], named for a test's failure. */
data class ContrastPair(
    val what: String,
    val foreground: Color,
    val background: Color,
    val use: ContrastUse,
)
