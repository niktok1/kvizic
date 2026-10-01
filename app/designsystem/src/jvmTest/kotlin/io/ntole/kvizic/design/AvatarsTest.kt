package io.ntole.kvizic.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import io.ntole.kvizic.core.player.Avatars
import io.ntole.kvizic.design.avatar.AvatarArt
import io.ntole.kvizic.design.component.Avatar
import io.ntole.kvizic.design.component.AvatarSize
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.Skins
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every avatar the server hands out is drawn, each its own drawing, in every skin; with `KVIZIC_DESIGN_DIR`
 * set, a sheet of them is written there for the owner to judge, large and at a lobby strip's size.
 */
class AvatarsTest {
    @Test
    fun `every avatar the server gives is drawn`() {
        assertEquals(Avatars.ALL.toSet(), AvatarArt.DRAWN.toSet())
        assertTrue(AvatarArt.DRAWN.none { it == AvatarArt.SILHOUETTE })
    }

    @Test
    fun `no two avatars draw alike, in any skin`() {
        Skins.ALL.forEach { skin ->
            val drawn = AvatarArt.DRAWN.map { id -> AvatarArt.of(id, skin.avatarPalette) }
            assertEquals(drawn.size, drawn.map { it.root.toString() }.toSet().size, skin.id)
        }
    }

    @Test
    fun `a sheet of every avatar, in every skin`() {
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
                        Row(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
                            AvatarArt.DRAWN.forEachIndexed { i, id -> Avatar(id, i, size = AvatarSize.XS) }
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

    private companion object {
        const val SHEET_WIDTH = 900
        const val SHEET_HEIGHT = 900
        const val PER_ROW = 4
        const val SETTLED = 500L * MILLI
    }
}
