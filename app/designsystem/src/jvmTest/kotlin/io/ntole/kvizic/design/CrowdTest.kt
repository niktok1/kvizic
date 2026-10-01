package io.ntole.kvizic.design

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import io.ntole.kvizic.design.component.AnswerGrid
import io.ntole.kvizic.design.component.AnswerTileState.DIMMED
import io.ntole.kvizic.design.component.AnswerTileState.LOCKED_IN
import io.ntole.kvizic.design.component.AvatarChip
import io.ntole.kvizic.design.component.AvatarStack
import io.ntole.kvizic.design.skin.Skin
import io.ntole.kvizic.design.skin.Skins
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A whole room on one answer, on the smallest phone the game is drawn for, in every skin, in a grid of
 * short answers and in a column of long ones: they stand on its tile's top edge, risen over it by the skin's
 * peek, inside its width, and never reach the tile above. Where a crowd of the room fits on every tile's face,
 * beside the answer, it stands there instead, clear of the letter and the answer, in two rows under a grid
 * tile's letter rather than closed up in one, and the answer is laid out as if it were not there.
 */
class CrowdTest {
    @Test
    fun `a crowd that fits on every tile stands on its face, clear of its answer`() {
        Skins.ALL.forEach { skin ->
            // Beside the letter of a tile in the grid, a room of four; past the answer's end on one across
            // the width, a room of eight.
            onTheFace(skin, listOf("Дунав", "Сава", "Тиса", "Морава"), ROOM.take(4), picked = 2)
            onTheFace(skin, listOf("Меркур", "Венера", "Марс"), ROOM, picked = 1)
            onTheFace(skin, listOf("Тачно", "Нетачно"), ROOM, picked = 0)
        }
    }

    @Test
    fun `a room of eight stands in two rows beside a grid tile's letter`() {
        Skins.ALL.forEach { skin -> onTheFace(skin, listOf("Дунав", "Сава", "Тиса", "Морава"), ROOM, 2, rows = 2) }
    }

    @Test
    fun `a crowd that does not fit on one tile stands on every tile's edge`() {
        Skins.ALL.forEach { skin ->
            // The last answer's line takes the width, so the short ones' crowd stands on their edge too.
            check(skin, listOf("Да", "Не", LONG[0]), picked = 1, above = 0, crowd = ROOM.size)
            check(skin, LONG, picked = 1, above = 0, crowd = ROOM.size)
        }
    }

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
        crowd: Int = 0,
    ) {
        val scene = crowdScene(skin, options, ROOM, picked, crowd)
        try {
            scene.renderUpTo(SETTLED)
            val inColumn = options.size < 4 || options == LONG
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
            assertEquals(tile.top - peek, crowd.top, SLACK, "$where: the crowd rises over its edge by the skin's peek")
        } finally {
            scene.close()
        }
    }

    private fun onTheFace(
        skin: Skin,
        options: List<String>,
        who: List<AvatarChip>,
        picked: Int,
        rows: Int = 1,
    ) {
        val behind = crowdScene(skin, options, who, picked, crowd = 0)
        val inside = crowdScene(skin, options, who, picked, crowd = who.size)
        try {
            behind.renderUpTo(SETTLED)
            inside.renderUpTo(SETTLED)
            val where = "${skin.id}, ${options.size} answers"
            val crowd = inside.everyNode().single { CROWD in it.descriptions }.boundsInRoot
            val tile = tileOf(inside, options[picked])
            assertTrue(
                crowd.left >= tile.left && crowd.right <= tile.right && crowd.top >= tile.top &&
                    crowd.bottom <= tile.bottom,
                "$where: the crowd stands on its tile's face: $crowd in $tile",
            )
            val avatar = skin.space.avatar.xs.value
            val tall = avatar * rows + skin.space.xxs.value * (rows - 1)
            assertEquals(tall, crowd.height, SLACK, "$where: the crowd stands in $rows rows")
            val answer = textOf(inside, options[picked])
            val letter = inside.everyNode().single { it.texts == listOf(LETTERS[picked]) }.boundsInRoot
            assertTrue(crowd.left >= letter.right, "$where: the crowd stands clear of the letter")
            val clear =
                crowd.bottom <= answer.boundsInRoot.top + SLACK ||
                    crowd.left >= answer.boundsInRoot.left + widest(answer.layout()) - SLACK
            assertTrue(clear, "$where: the crowd stands clear of the answer")
            options.forEach {
                assertEquals(
                    textOf(behind, it).boundsInRoot,
                    textOf(inside, it).boundsInRoot,
                    "$where: $it is laid out as if the crowd were not there",
                )
                assertEquals(
                    textOf(behind, it).layout().lineCount,
                    textOf(inside, it).layout().lineCount,
                    "$where: $it keeps its lines",
                )
            }
        } finally {
            behind.close()
            inside.close()
        }
    }

    private fun crowdScene(
        skin: Skin,
        options: List<String>,
        who: List<AvatarChip>,
        picked: Int,
        crowd: Int,
    ): ImageComposeScene =
        stageScene(skin, WIDTH, HEIGHT) {
            AnswerGrid(
                options,
                Modifier.fillMaxSize(),
                states = options.indices.map { if (it == picked) LOCKED_IN else DIMMED },
                pickers = { i -> if (i == picked) AvatarStack(who, contentDescription = CROWD) },
                crowd = crowd,
            )
        }

    private fun tileOf(
        scene: ImageComposeScene,
        answer: String,
    ): Rect = scene.nodes().single { node -> node.texts.any { it.equals(answer, ignoreCase = true) } }.boundsInRoot

    /** The answer's own text, unmerged. */
    private fun textOf(
        scene: ImageComposeScene,
        answer: String,
    ) = scene.everyNode().single { node -> node.texts.singleOrNull()?.equals(answer, ignoreCase = true) == true }

    private fun androidx.compose.ui.semantics.SemanticsNode.layout(): TextLayoutResult {
        val laid = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action?.invoke(laid)
        return laid.single()
    }

    private fun widest(laid: TextLayoutResult): Float = (0 until laid.lineCount).maxOf { laid.getLineRight(it) }

    private companion object {
        const val WIDTH = 328
        const val HEIGHT = 380
        const val SETTLED = 1_000L * MILLI
        const val SLACK = 1f
        const val CROWD = "crowd"
        val LETTERS = listOf("А", "Б", "В", "Г")

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
