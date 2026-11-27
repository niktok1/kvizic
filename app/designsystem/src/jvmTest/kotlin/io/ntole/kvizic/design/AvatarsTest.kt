package io.ntole.kvizic.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.ntole.kvizic.core.player.Avatars
import io.ntole.kvizic.design.avatar.AvatarArt
import io.ntole.kvizic.design.avatar.CrownArt
import io.ntole.kvizic.design.avatar.FrameArt
import io.ntole.kvizic.design.component.Avatar
import io.ntole.kvizic.design.component.AvatarSize
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.Skin
import io.ntole.kvizic.design.skin.Skins
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every avatar the server hands out or sells is drawn, each its own drawing, and so is every frame and every crown,
 * in every skin; with `KVIZIC_DESIGN_DIR` set, sheets of them are written there for the owner to judge: the animals
 * large and at a lobby strip's size, the ladder of frames and crowns, and every animal on every seat's colour.
 */
class AvatarsTest {
    @Test
    fun `every avatar the server gives or sells is drawn`() {
        assertEquals(Avatars.ALL + Avatars.SHOP, AvatarArt.DRAWN)
        assertEquals(Avatars.SHOP, AvatarArt.SHOP)
        assertTrue(AvatarArt.DRAWN.none { it == AvatarArt.SILHOUETTE })
    }

    @Test
    fun `no two avatars, frames or crowns draw alike, in any skin, each inside its box, an unknown one not at all`() {
        Skins.ALL.forEach { skin ->
            val drawn = AvatarArt.DRAWN.map { id -> AvatarArt.of(id, skin.avatarPalette) }
            assertEquals(drawn.size, drawn.map { it.root.toString() }.toSet().size, skin.id)
            val frames: (String) -> List<Int> = { id ->
                pixels { with(FrameArt) { drawFrame(id, FRAME_BAND, skin.metals, skin.avatarPalette.ink) } }
            }
            val crowns: (String) -> List<Int> = { id ->
                pixels { with(CrownArt) { drawCrown(id, skin.metals, skin.avatarPalette.ink) } }
            }
            listOf(FrameArt.DRAWN to frames, CrownArt.DRAWN to crowns).forEach { (ids, draw) ->
                val each = ids.map(draw)
                assertEquals(each.size, each.toSet().size, "${skin.id}: two of $ids draw alike")
                each.forEachIndexed { k, pixels ->
                    val outside =
                        pixels.withIndex().count { (i, p) -> p != 0 && !(i % CANVAS in BOX && i / CANVAS in BOX) }
                    assertEquals(0, outside, "${skin.id}: ${ids[k]} draws past its box")
                    assertTrue(pixels.any { it != 0 }, "${skin.id}: ${ids[k]} draws nothing")
                }
                assertTrue(draw(UNKNOWN).all { it == 0 }, "${skin.id}: an unknown id draws")
            }
        }
    }

    @Test
    fun `an unknown frame or crown shows the plain ring and the plain crown`() {
        Skins.ALL.forEach { skin ->
            val plain = hostPixels(skin, frame = null, crown = null)
            assertContentEquals(plain, hostPixels(skin, frame = UNKNOWN, crown = UNKNOWN), skin.id)
            assertTrue(!plain.contentEquals(hostPixels(skin, FrameArt.IRON, CrownArt.IRON)), skin.id)
        }
    }

    @Test
    fun `sheets of every avatar, frame and crown, and of every avatar on every seat, in every skin`() {
        Skins.ALL.forEach { skin ->
            sheet("avatars-${skin.id}", skin, SHEET_WIDTH, SHEET_HEIGHT) {
                val space = KvizicTheme.space
                Column(Modifier.padding(space.lg), verticalArrangement = Arrangement.spacedBy(space.md)) {
                    (AvatarArt.DRAWN.chunked(Avatars.ALL.size / 2).take(2) + listOf(AvatarArt.SHOP)).forEach { ids ->
                        Row(horizontalArrangement = Arrangement.spacedBy(space.md)) {
                            ids.forEach { id -> Avatar(id, AvatarArt.DRAWN.indexOf(id), size = AvatarSize.XL) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
                        AvatarArt.DRAWN.forEachIndexed { seat, id -> Avatar(id, seat, size = AvatarSize.XS) }
                    }
                    (listOf(null) + FrameArt.DRAWN.indices).forEach { tier ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(space.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FRAMED_SIZES.forEach { size ->
                                FRAMED.forEachIndexed { i, id ->
                                    val seat = size == AvatarSize.MD
                                    Avatar(
                                        id,
                                        (tier ?: 0) + i,
                                        size = size,
                                        host = seat && i == 0,
                                        level = LEVEL.takeIf { seat },
                                        frame = tier?.let { FrameArt.DRAWN[it] },
                                        crown = tier?.let { CrownArt.DRAWN[it] },
                                        dimmed = seat && i == FRAMED.lastIndex,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            sheet("avatar-seats-${skin.id}", skin, SEATS_WIDTH, SEATS_HEIGHT) {
                val space = KvizicTheme.space
                Row(Modifier.padding(space.lg), horizontalArrangement = Arrangement.spacedBy(space.xl)) {
                    AvatarArt.DRAWN.chunked((AvatarArt.DRAWN.size + 1) / 2).forEach { half ->
                        Column(verticalArrangement = Arrangement.spacedBy(space.xs)) {
                            half.forEach { id ->
                                Row(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
                                    skin.colors.seats.indices
                                        .forEach { seat -> Avatar(id, seat, size = AvatarSize.SM) }
                                    Avatar(id, 0, size = AvatarSize.SM, dimmed = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun sheet(
        name: String,
        skin: Skin,
        width: Int,
        height: Int,
        content: @Composable () -> Unit,
    ) {
        val scene = stageScene(skin, width, height, density = 2f, content = content)
        try {
            writeDesign(name, scene.renderUpTo(SETTLED))
        } finally {
            scene.close()
        }
    }

    private fun hostPixels(
        skin: Skin,
        frame: String?,
        crown: String?,
    ): IntArray {
        val scene =
            stageScene(skin, HOST_CANVAS, HOST_CANVAS, density = 2f) {
                Avatar(
                    AvatarArt.FOX,
                    0,
                    Modifier.padding(KvizicTheme.space.xl),
                    size = AvatarSize.LG,
                    host = true,
                    frame = frame,
                    crown = crown,
                )
            }
        return try {
            pixelsOf(scene.renderUpTo(SETTLED))
        } finally {
            scene.close()
        }
    }

    private fun pixels(draw: DrawScope.() -> Unit): List<Int> {
        val bitmap = ImageBitmap(CANVAS, CANVAS)
        val whole = Size(CANVAS.toFloat(), CANVAS.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), whole) {
            inset(MARGIN.toFloat()) { draw() }
        }
        return IntArray(CANVAS * CANVAS).also { bitmap.readPixels(it) }.toList()
    }

    private companion object {
        const val SHEET_WIDTH = 1_500
        const val SHEET_HEIGHT = 2_000
        const val SEATS_WIDTH = 1_500
        const val SEATS_HEIGHT = 920
        const val HOST_CANVAS = 240
        const val SETTLED = 500L * MILLI
        const val LEVEL = 12
        const val UNKNOWN = "frame.unknown"
        const val MARGIN = 8
        const val CANVAS = 112
        const val FRAME_BAND = 8f
        val BOX = MARGIN until CANVAS - MARGIN
        val FRAMED = listOf(AvatarArt.FOX, AvatarArt.HARE, AvatarArt.PANTHER)
        val FRAMED_SIZES = listOf(AvatarSize.XL, AvatarSize.MD, AvatarSize.XS)
    }
}
