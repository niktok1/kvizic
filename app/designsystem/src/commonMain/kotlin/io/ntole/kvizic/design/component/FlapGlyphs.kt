package io.ntole.kvizic.design.component

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import io.ntole.kvizic.design.skin.FlapGlyph

/**
 * The glyphs a flap or a timer shows, each laid out once for a style, with the middle of its figures:
 * [figureHeight] em tall, above the baseline. Read in the draw alone; plain fields, no state.
 */
internal class FlapGlyphs(
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    private val figureHeight: Float,
) {
    private val laidOut = mutableMapOf<String, FlapGlyph>()

    operator fun get(text: String?): FlapGlyph? {
        if (text.isNullOrBlank()) return null
        return laidOut.getOrPut(text) {
            val layout = measurer.measure(text, style)
            val fontPx = with(layout.layoutInput.density) { style.fontSize.toPx() }
            FlapGlyph(layout, middle = layout.firstBaseline - figureHeight * fontPx / 2)
        }
    }

    operator fun get(char: Char?): FlapGlyph? = get(char?.toString())
}
