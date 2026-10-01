package io.ntole.kvizic.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import io.ntole.kvizic.design.component.ScoreRow
import io.ntole.kvizic.design.component.StandingsBoard
import io.ntole.kvizic.design.skin.Skins
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reveal's board: its lines start where they stood before the question and, after a beat, slide to
 * where it put them, saying how far each moved; the slide composes nothing frame by frame.
 */
class StandingsBoardTest {
    @Test
    fun `the lines slide from where they stood to where they stand now and say how far they moved`() {
        Skins.ALL.forEach { skin ->
            val scene =
                stageScene(skin, WIDTH, HEIGHT) { StandingsBoard(ROWS, Modifier.fillMaxWidth(), reorderKey = "q1") }
            try {
                scene.renderAt(FRAME)
                assertTrue(top(scene, "Бојан") > top(scene, "Мудра Сова"), "${skin.id}: Бојан starts where he stood")
                assertTrue(moves(scene).isEmpty(), "${skin.id}: a move shows before the slide: ${moves(scene)}")
                var time = FRAME
                while (time < SLID) {
                    time += FRAME
                    scene.renderAt(time)
                }
                assertTrue(top(scene, "Бојан") < top(scene, "Мудра Сова"), "${skin.id}: Бојан did not rise")
                assertEquals(listOf("1", "1"), moves(scene), "${skin.id}: the moves")
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun `the slide composes nothing frame by frame`() {
        Skins.ALL.forEach { skin ->
            val recompositions = Recompositions()
            val scene =
                stageScene(skin, WIDTH, HEIGHT) {
                    CountedBy(recompositions) { StandingsBoard(ROWS, Modifier.fillMaxWidth(), reorderKey = "q1") }
                }
            try {
                var time = 0L
                // Past the beat, when the totals flap and the moves show: one composition, then none.
                while (time < SLIDING_FROM) {
                    time += FRAME
                    scene.renderAt(time)
                }
                val before = recompositions.scopesEntered
                while (time < SLID) {
                    time += FRAME
                    scene.renderAt(time)
                }
                assertEquals(before, recompositions.scopesEntered, "${skin.id}: a frame of the slide composed")
                recompositions.assertCounting(scene, time)
            } finally {
                scene.close()
            }
        }
    }

    private fun top(
        scene: androidx.compose.ui.ImageComposeScene,
        name: String,
    ): Float =
        scene
            .everyNode()
            .first { name in it.texts }
            .boundsInRoot.top

    /** The counts of the moves shown, top down. */
    private fun moves(scene: androidx.compose.ui.ImageComposeScene): List<String> =
        scene.everyNode().flatMap { it.texts }.filter { it == "1" }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 400

        /** Past the beat and the slide whole. */
        const val SLID = 2_000L * MILLI

        /** Into the slide, past the beat's end and the frame that flips the totals. */
        const val SLIDING_FROM = 800L * MILLI

        val ROWS =
            listOf(
                ScoreRow(1, "Нина", "fox", 0, 1328, delta = 88, id = "nina"),
                ScoreRow(2, "Бојан", "bear", 1, 1045, delta = 76, moved = 1, id = "bojan"),
                ScoreRow(3, "Мудра Сова", "owl", 2, 980, delta = -12, moved = -1, id = "sova"),
                ScoreRow(4, "Марко", "hedgehog", 3, 911, delta = -31, own = true, id = "marko"),
            )
    }
}
