package io.ntole.kvizic.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.ntole.kvizic.core.player.Avatars
import io.ntole.kvizic.design.avatar.AvatarArt
import io.ntole.kvizic.design.avatar.FrameArt
import io.ntole.kvizic.design.component.Avatar
import io.ntole.kvizic.design.component.AvatarSize
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.Skin
import io.ntole.kvizic.design.skin.Skins
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every avatar the server hands out or sells is drawn, each its own drawing, and so is every frame, in every
 * skin; with `KVIZIC_DESIGN_DIR` set, a sheet of them is written there for the owner to judge, large, at a
 * lobby seat's size and at a lobby strip's.
 */
class AvatarsTest {
    @Test
    fun `every avatar the server gives or sells is drawn`() {
        assertEquals(Avatars.ALL + Avatars.SHOP, AvatarArt.DRAWN)
        assertEquals(Avatars.SHOP, AvatarArt.SHOP)
        assertTrue(AvatarArt.DRAWN.none { it == AvatarArt.SILHOUETTE })
    }

    @Test
    fun `no two avatars or frames draw alike, in any skin, a frame only inside its box, an unknown one not at all`() {
        Skins.ALL.forEach { skin ->
            val drawn = AvatarArt.DRAWN.map { id -> AvatarArt.of(id, skin.avatarPalette) }
            assertEquals(drawn.size, drawn.map { it.root.toString() }.toSet().size, skin.id)
            val frames = FrameArt.DRAWN.map { id -> framePixels(id, skin) }
            assertEquals(frames.size, frames.toSet().size, skin.id)
            frames.forEachIndexed { f, pixels ->
                val outside = pixels.withIndex().count { (i, p) -> p != 0 && !(i % CANVAS in BOX && i / CANVAS in BOX) }
                assertEquals(0, outside, "${skin.id}: ${FrameArt.DRAWN[f]} draws past its box")
            }
            assertTrue(framePixels(UNKNOWN_FRAME, skin).all { it == 0 }, skin.id)
        }
    }

    @Test
    fun `a sheet of every avatar and every frame, in every skin`() {
        Skins.ALL.forEach { skin ->
            val scene =
                stageScene(skin, SHEET_WIDTH, SHEET_HEIGHT, density = 2f) {
                    val space = KvizicTheme.space
                    Column(Modifier.padding(space.lg), verticalArrangement = Arrangement.spacedBy(space.md)) {
                        AvatarArt.DRAWN.chunked(PER_ROW).forEachIndexed { row, ids ->
                            Row(horizontalArrangement = Arrangement.spacedBy(space.md)) {
                                ids.forEachIndexed { i, id -> Avatar(id, row * PER_ROW + i, size = AvatarSize.XL) }
                            }
                        }
                        AvatarArt.DRAWN.chunked(PER_ROW * 2).forEachIndexed { row, ids ->
                            Row(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
                                ids.forEachIndexed { i, id -> Avatar(id, row * PER_ROW * 2 + i, size = AvatarSize.XS) }
                            }
                        }
                        FrameArt.DRAWN.forEachIndexed { row, frame ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(space.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FRAMED_SIZES.forEach { size ->
                                    FRAMED.forEachIndexed { i, id ->
                                        val seat = size == AvatarSize.MD
                                        Avatar(
                                            id,
                                            row + i,
                                            size = size,
                                            host = seat && i == 0,
                                            level = LEVEL.takeIf { seat },
                                            frame = frame,
                                            dimmed = seat && i == FRAMED.lastIndex,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            try {
                writeDesign("avatars-${skin.id}", scene.renderUpTo(SETTLED))
            } finally {
                scene.close()
            }
        }
    }

    private fun framePixels(
        id: String,
        skin: Skin,
    ): List<Int> {
        val bitmap = ImageBitmap(CANVAS, CANVAS)
        val whole = Size(CANVAS.toFloat(), CANVAS.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), whole) {
            inset(MARGIN.toFloat()) {
                with(FrameArt) { drawFrame(id, FRAME_BAND, skin.colors.seat(0), skin.avatarPalette) }
            }
        }
        return IntArray(CANVAS * CANVAS).also { bitmap.readPixels(it) }.toList()
    }

    private companion object {
        const val SHEET_WIDTH = 1_300
        const val SHEET_HEIGHT = 2_150
        const val PER_ROW = 5
        const val SETTLED = 500L * MILLI
        const val LEVEL = 12
        const val UNKNOWN_FRAME = "frame.unknown"
        const val MARGIN = 8
        const val CANVAS = 112
        const val FRAME_BAND = 8f
        val BOX = MARGIN until CANVAS - MARGIN
        val FRAMED = listOf(AvatarArt.FOX, AvatarArt.HARE, AvatarArt.EAGLE)
        val FRAMED_SIZES = listOf(AvatarSize.XL, AvatarSize.MD, AvatarSize.XS)
    }
}
