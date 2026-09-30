package io.ntole.kvizic.design

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import io.ntole.kvizic.design.component.AnswerGrid
import io.ntole.kvizic.design.component.AnswerTileState
import io.ntole.kvizic.design.component.AvatarChip
import io.ntole.kvizic.design.component.AvatarStack
import io.ntole.kvizic.design.skin.Skin
import io.ntole.kvizic.design.skin.Skins
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A whole room on one answer, on the smallest phone the game is drawn for, in every skin, in a grid of
 * short answers and in a column of long ones: their heads stand behind its tile, over its top edge, inside
 * its width, and never reach the tile above.
 */
class CrowdTest {
    @Test
    fun `a room of eight on one answer stands over its tile`() {
        Skins.ALL.forEach { skin ->
            // In the grid, the bottom row's first tile, under the top row's; in the column, the second tile.
            check(skin, listOf("Дунав", "Сава", "Тиса", "Морава"), picked = 2, above = 0)
            check(skin, LONG, picked = 1, above = 0)
        }
    }

    private fun check(
        skin: Skin,
        options: List<String>,
        picked: Int,
        above: Int,
    ) {
        val scene =
            stageScene(skin, WIDTH, HEIGHT) {
                AnswerGrid(
                    options,
                    Modifier.fillMaxSize(),
                    states =
                        options.indices.map {
                            if (it ==
                                picked
                            ) {
                                AnswerTileState.LOCKED_IN
                            } else {
                                AnswerTileState.DIMMED
                            }
                        },
                    pickers = { i -> if (i == picked) AvatarStack(ROOM, contentDescription = CROWD) },
                )
            }
        try {
            scene.renderUpTo(SETTLED)
            val inColumn = options == LONG
            val where = "${skin.id}, ${if (inColumn) "a column" else "a grid"}"
            val first = tileOf(scene, options[0])
            val second = tileOf(scene, options[1])
            assertTrue((first.top == second.top) != inColumn, "$where: the answers stand as it says")
            val crowd = scene.everyNode().single { CROWD in it.descriptions }.boundsInRoot
            val tile = tileOf(scene, options[picked])
            val tileAbove = tileOf(scene, options[above])
            val peek = skin.space.tile.pickersPeek.value
            assertTrue(crowd.left >= tile.left && crowd.right <= tile.right, "$where: the crowd stands inside its tile")
            assertTrue(crowd.top < tile.top && crowd.bottom > tile.top, "$where: the crowd stands over its top edge")
            assertTrue(crowd.top >= tile.top - peek - SLACK, "$where: the crowd rises no more than the skin's peek")
            assertTrue(crowd.top >= tileAbove.bottom, "$where: the crowd reaches the tile above")
        } finally {
            scene.close()
        }
    }

    private fun tileOf(
        scene: androidx.compose.ui.ImageComposeScene,
        answer: String,
    ): Rect = scene.nodes().single { node -> node.texts.any { it.equals(answer, ignoreCase = true) } }.boundsInRoot

    private companion object {
        const val WIDTH = 328
        const val HEIGHT = 380
        const val SETTLED = 1_000L * MILLI
        const val SLACK = 1f
        const val CROWD = "crowd"

        /** Every seat of a room of eight. */
        val ROOM =
            listOf("fox", "owl", "hedgehog", "bear", "stork", "owl", "bear", "fox").mapIndexed {
                seat,
                id,
                ->
                AvatarChip(id, seat)
            }

        /** Four answers of 60 characters, which stand in a column. */
        val LONG =
            listOf(
                "Никола Тесла, проналазач који је живео у Сједињеним Државама",
                "Михајло Пупин, физичар из Идвора и изумитељ Пупиновог калема",
                "Милутин Миланковић, математичар који је објаснио ледена доба",
                "Јосиф Панчић, лекар и ботаничар, открио је Панчићеву оморику",
            )
    }
}
