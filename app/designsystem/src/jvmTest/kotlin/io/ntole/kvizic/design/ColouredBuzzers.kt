package io.ntole.kvizic.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import io.ntole.kvizic.design.component.AnswerTileState
import io.ntole.kvizic.design.skin.SkinParts
import io.ntole.kvizic.design.skin.Skins
import io.ntole.kvizic.design.skin.SurfaceLook
import io.ntole.kvizic.design.skin.TilePart
import io.ntole.kvizic.design.skins.buzzers.BuzzersParts

/*
 * The other tile scheme the owner chooses between, drawn for the design gate only: four buzzers each in
 * its letter's colour, the letter on a cream cap, where Buzzers has uniform enamel with a coloured bulb.
 * A skin's part and nothing else, so it also shows what swapping one renderer takes.
 */

private val colours = Skins.Buzzers.colors

/** Buzzers' tile, its face in its answer's own colour. */
private object ColouredTile : TilePart by BuzzersParts.tile {
    override fun surface(
        state: AnswerTileState,
        index: Int,
    ): SurfaceLook {
        val enamel = BuzzersParts.tile.surface(state, index)
        val face = colours.letter(index)
        return when (state) {
            AnswerTileState.IDLE -> {
                enamel.copy(fill = face, side = lerp(face, colours.outline, SIDE_DARKER))
            }

            // Locked in, it keeps its own colour, held down and glowing in it.
            AnswerTileState.LOCKED_IN -> {
                enamel.copy(
                    fill = face,
                    side = lerp(face, colours.outline, SIDE_DARKER),
                    glow = face.copy(alpha = LOCK_GLOW),
                )
            }

            else -> {
                enamel
            }
        }
    }

    override fun DrawScope.drawStamp(
        state: AnswerTileState,
        shown: Float,
    ) {
        with(BuzzersParts.tile) { drawStamp(state, shown) }
    }

    override fun DrawScope.drawLetterMark(
        state: AnswerTileState,
        index: Int,
        lit: Float,
    ) {
        if (state != AnswerTileState.IDLE && state != AnswerTileState.LOCKED_IN) {
            with(BuzzersParts.tile) { drawLetterMark(state, index, lit) }
            return
        }
        // The letter on a cream cap, as a key's legend, since the face is the colour now.
        val radius = size.minDimension / 2
        drawCircle(colours.plate, radius)
        drawCircle(colours.outline, radius, style = Stroke(width = radius * EDGE))
        drawCircle(
            Color.White.copy(alpha = GLINT),
            radius * GLINT_SIZE,
            center + Offset(-radius * GLINT_AT, -radius * GLINT_AT),
        )
    }
}

/** Buzzers with [ColouredTile] for its tiles and nothing else changed. */
internal val ColouredBuzzers =
    Skins.Buzzers.copy(
        id = "buzzers-coloured",
        parts =
            object : SkinParts by BuzzersParts {
                override val tile: TilePart = ColouredTile
            },
    )

private const val SIDE_DARKER = 0.4f
private const val LOCK_GLOW = 0.35f
private const val EDGE = 0.1f
private const val GLINT = 0.45f
private const val GLINT_SIZE = 0.28f
private const val GLINT_AT = 0.32f
