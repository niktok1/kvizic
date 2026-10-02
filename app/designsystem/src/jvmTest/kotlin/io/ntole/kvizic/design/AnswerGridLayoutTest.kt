package io.ntole.kvizic.design

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.design.component.AnswerGrid
import io.ntole.kvizic.design.component.Stage
import io.ntole.kvizic.design.skin.KvizicSkin
import io.ntole.kvizic.design.skin.Script
import io.ntole.kvizic.design.skin.Skins
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A question's answers laid out by how many there are, in every skin, never as if there were always
 * four: two stacked tall across the whole width, three in a column, and four in a column on a phone held
 * upright, short or long; on a wide window, four in a grid of two by two and five in rows of two. Each is
 * named by the letter of its index, in the script shown.
 */
class AnswerGridLayoutTest {
    @Test
    fun `two answers stand tall across the width, one over the other`() {
        grid(listOf("Тачно", "Нетачно")) { tiles ->
            assertColumn(tiles)
            tiles.forEach {
                assertTrue(
                    it.height >= Skins.Buzzers.space.tile.tallMinHeight.value,
                    "a true or false tile stands tall",
                )
            }
        }
    }

    @Test
    fun `three answers stand in a column`() {
        grid(listOf("Меркур", "Венера", "Марс")) { tiles -> assertColumn(tiles) }
    }

    @Test
    fun `four answers stand in a column on a phone held upright, short or long`() {
        grid(RIVERS) { tiles -> assertColumn(tiles) }
        grid(listOf("Јосиф Маринковић", "Корнелије Станковић", "Петар Коњовић", "Стеван Мокрањац")) { tiles ->
            assertColumn(tiles)
        }
    }

    @Test
    fun `four answers stand two by two on a wide window, on its side or a tablet's held upright`() {
        listOf(WIDE_WIDTH to HEIGHT, TABLET_WIDTH to TABLET_HEIGHT).forEach { (width, height) ->
            grid(RIVERS, width, height) { tiles -> assertTwoByTwo(tiles, width) }
        }
    }

    private fun assertTwoByTwo(
        tiles: List<Rect>,
        width: Int,
    ) {
        val (a, b, c, d) = tiles
        assertEquals(a.top, b.top, EDGE, "А and Б share a row")
        assertEquals(c.top, d.top, EDGE, "В and Г share a row")
        assertTrue(c.top > a.bottom - EDGE, "the second row is under the first")
        assertTrue(b.left > a.right - EDGE && d.left > c.right - EDGE, "each row's second is to the right")
        tiles.forEach { assertEquals(a.width, it.width, EDGE, "every tile of the grid is as wide") }
        assertTrue(a.width < width / 2f, "a tile of the grid takes half the width")
    }

    @Test
    fun `five answers stand in rows of two on a wide window, the last alone and as wide as the rest`() {
        grid(listOf("Један", "Два", "Три", "Четири", "Пет"), WIDE_WIDTH) { tiles ->
            assertEquals(tiles[0].top, tiles[1].top, EDGE)
            assertTrue(tiles[4].top > tiles[2].bottom - EDGE, "the fifth is in a row of its own")
            assertEquals(tiles[0].width, tiles[4].width, EDGE, "the last is as wide as the rest")
        }
    }

    @Test
    fun `each answer is named by the letter of its index in the script shown`() {
        val scripts = listOf(Script.CYRILLIC to listOf("А", "Б", "В", "Г"), Script.LATIN to listOf("A", "B", "C", "D"))
        Skins.ALL.forEach { skin ->
            scripts.forEach { (script, letters) ->
                val scene =
                    ImageComposeScene(WIDTH, HEIGHT, Density(1f)) {
                        KvizicSkin(skin, script) {
                            Stage(Modifier.fillMaxSize()) { AnswerGrid(RIVERS, Modifier.fillMaxSize(), onPick = {}) }
                        }
                    }
                try {
                    scene.renderAt(0)
                    RIVERS.forEachIndexed { i, river ->
                        val tile = scene.nodes().single { river in it.texts }
                        assertEquals(listOf(letters[i], river), tile.texts, "${skin.id} in $script names answer $i")
                    }
                } finally {
                    scene.close()
                }
            }
        }
    }

    @Test
    fun `a question of one answer is refused`() {
        val refused = runCatching { grid(listOf("Само")) { } }.exceptionOrNull()
        assertTrue(refused is IllegalArgumentException, "one answer laid out: $refused")
    }

    /**
     * [options] laid out in each skin's grid on a window of [width] by [height], a phone held upright unless
     * told, [check]ed by the bounds of each answer's tile, in order.
     */
    private fun grid(
        options: List<String>,
        width: Int = WIDTH,
        height: Int = HEIGHT,
        check: (List<Rect>) -> Unit,
    ) {
        Skins.ALL.forEach { skin ->
            val scene =
                ImageComposeScene(width, height, Density(1f)) {
                    KvizicSkin(
                        skin,
                    ) { Stage(Modifier.fillMaxSize()) { AnswerGrid(options, Modifier.fillMaxSize(), onPick = {}) } }
                }
            try {
                scene.renderAt(0)
                val tiles = options.map { option -> scene.nodes().single { option in it.texts }.boundsInRoot }
                check(tiles)
            } finally {
                scene.close()
            }
        }
    }

    /** Every tile across the whole width, one under another, and all of one height. */
    private fun assertColumn(tiles: List<Rect>) {
        tiles.forEach { assertEquals(WIDTH.toFloat(), it.width, EDGE, "a tile stands across the whole width") }
        tiles.zipWithNext().forEach { (upper, lower) ->
            assertTrue(lower.top >= upper.bottom - EDGE, "each under the one before")
        }
        tiles.forEach { assertTrue(abs(it.height - tiles[0].height) <= EDGE, "all of one height") }
    }

    private companion object {
        const val WIDTH = 360
        const val HEIGHT = 520

        /** A phone on its side, and a tablet held upright. */
        const val WIDE_WIDTH = 720
        const val TABLET_WIDTH = 640
        const val TABLET_HEIGHT = 900
        const val EDGE = 1.5f
        val RIVERS = listOf("Дунав", "Сава", "Тиса", "Морава")
    }
}
