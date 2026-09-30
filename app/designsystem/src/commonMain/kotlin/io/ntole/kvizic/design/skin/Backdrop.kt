package io.ntole.kvizic.design.skin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * The art a skin draws on the page under every screen, drawn in code so no image ships, and laid out in
 * shares of the page it is drawn on, so it fits a phone and a tablet alike. It is the same every time it
 * is drawn: nothing in it is random.
 */
@Immutable
interface Backdrop {
    /** Draws the page and its art over the whole of this scope. */
    fun DrawScope.draw()

    /**
     * Every colour the art shows where text may stand on it, as it shows there, one drawn over another
     * included: what `SkinContrastTest` holds the page's text to.
     */
    val colors: List<Color>
}
