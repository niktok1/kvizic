package io.ntole.kvizic.design.skins.notebook

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import io.ntole.kvizic.design.font.Faces
import io.ntole.kvizic.design.skin.Backdrop
import io.ntole.kvizic.design.skin.Skin
import io.ntole.kvizic.design.skin.SkinFonts

/**
 * Skin #2, rough, to prove a skin is more than a palette: the same components as Buzzers in a squared
 * exercise book, hand-drawn edges and pencilled shadows for enamel and ink, highlighter for light,
 * rolling digits for flaps, and Sofia Sans Extra Condensed for the display face.
 */
internal val NotebookSkin: Skin =
    Skin(
        id = "notebook",
        name = "Notebook",
        colors = NotebookColors,
        fonts = SkinFonts(display = Faces.SofiaExtraCondensed, body = Faces.FiraSans),
        type = NotebookType,
        shapes = NotebookShapes,
        depth = NotebookDepth,
        space = NotebookSpace,
        motion = NotebookMotion,
        parts = NotebookParts,
        backdrop = NotebookBackdrop,
        avatarPalette = NotebookAvatars,
    )

/** Squared paper: a light blue grid, and the red margin line down the left of the page. */
private object NotebookBackdrop : Backdrop {
    override val colors: List<Color> = listOf(Paper, GridLine, MarginLine)

    override fun DrawScope.draw() {
        drawRect(Paper)
        val step = GRID.dp.toPx()
        val line = LINE.dp.toPx()
        var x = step / 2
        while (x < size.width) {
            drawLine(GridLine, Offset(x, 0f), Offset(x, size.height), strokeWidth = line)
            x += step
        }
        var y = step / 2
        while (y < size.height) {
            drawLine(GridLine, Offset(0f, y), Offset(size.width, y), strokeWidth = line)
            y += step
        }
        val margin = MARGIN.dp.toPx()
        drawLine(MarginLine, Offset(margin, 0f), Offset(margin, size.height), strokeWidth = line * 2)
    }

    private const val GRID = 18f
    private const val LINE = 1f
    private const val MARGIN = 14f
}
