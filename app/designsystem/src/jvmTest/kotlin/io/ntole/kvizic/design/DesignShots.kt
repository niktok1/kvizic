package io.ntole.kvizic.design

import androidx.compose.runtime.Composable
import io.ntole.kvizic.design.skin.Skin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * The game's screens as the owner approves them before any real screen is built: drawn off screen at an
 * iPhone SE's 375 by 667 at twice a pixel a dp, in Buzzers, three of them in Notebook, and Home and play
 * in each display face to compare. Each is drawn in every run; with `KVIZIC_DESIGN_DIR` set each is also
 * written there as a PNG:
 * `KVIZIC_DESIGN_DIR=../design-review ./gradlew :app:designsystem:jvmTest --tests '*DesignShots*' --rerun`.
 * A few classes share them out, as the test JVMs share out classes, never the tests of one.
 */

/** A screen to draw: its file's [name], in [skin], drawn up to [atMillis]. */
internal class Shot(
    val name: String,
    val skin: Skin,
    val atMillis: Long = SHOT_SETTLED,
    val content: @Composable () -> Unit,
)

/** Draws each of [shots], checks something is drawn over the page, and writes it for the owner to judge. */
internal fun drawShots(shots: List<Shot>) {
    shots.forEach { shot ->
        val scene = stageScene(shot.skin, SHOT_WIDTH, SHOT_HEIGHT, SHOT_DENSITY) { shot.content() }
        try {
            val image = scene.renderUpTo(shot.atMillis * MILLI)
            assertEquals(SHOT_WIDTH, image.width, shot.name)
            // Something is drawn over the page: more than one colour shows.
            val colours = pixelsOf(image).toSet().size
            assertTrue(colours > MANY_COLOURS, "${shot.name} draws $colours colours")
            writeDesign(shot.name, image)
        } finally {
            scene.close()
        }
    }
}

private const val SHOT_DENSITY = 2f
private const val SHOT_WIDTH = 750
private const val SHOT_HEIGHT = 1334
private const val SHOT_SETTLED = 1_000L
private const val MANY_COLOURS = 50
