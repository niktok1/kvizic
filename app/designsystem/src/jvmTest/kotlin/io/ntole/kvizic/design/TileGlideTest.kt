package io.ntole.kvizic.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.ntole.kvizic.design.component.AnswerGrid
import io.ntole.kvizic.design.component.AnswerTileState
import io.ntole.kvizic.design.component.Stage
import io.ntole.kvizic.design.component.rememberTilePlaces
import io.ntole.kvizic.design.skin.KvizicSkin
import io.ntole.kvizic.design.skin.Skins
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A question's tiles go from one grid to the next of the same question, as its answers give way to its
 * reveal, by gliding: in every skin, drawn a frame at a time as a phone draws them, the next grid's tiles
 * stand where the last's stood the frame they come, move on to their own places and never back, and a tile
 * is never drawn twice, though both grids stand a while as the screens give way.
 */
class TileGlideTest {
    @Test
    fun `the next grid's tiles glide from where the last's stood and never stand twice`() {
        Skins.ALL.forEach { skin ->
            var first by mutableStateOf(false)
            var next by mutableStateOf(false)
            val states = listOf(AnswerTileState.LOCKED_IN) + List(3) { AnswerTileState.DIMMED }
            val effects = AfterTheFrame()
            val scene =
                ImageComposeScene(WIDTH, HEIGHT, Density(1f), coroutineContext = effects) {
                    KvizicSkin(skin) {
                        Stage(Modifier.fillMaxSize()) {
                            val places = rememberTilePlaces()
                            Box(Modifier.fillMaxSize().padding(PADDING.dp)) {
                                if (first) {
                                    AnswerGrid(
                                        OPTIONS,
                                        Modifier.fillMaxWidth().height(FIRST_HEIGHT.dp),
                                        states = states,
                                        places = places,
                                        placesKey = QUESTION,
                                    )
                                }
                                if (next) {
                                    AnswerGrid(
                                        OPTIONS,
                                        Modifier.fillMaxWidth().padding(top = NEXT_TOP.dp).height(NEXT_HEIGHT.dp),
                                        states = states,
                                        places = places,
                                        placesKey = QUESTION,
                                    )
                                }
                            }
                        }
                    }
                }
            try {
                var time = 0L
                val frame = {
                    time += FRAME
                    Snapshot.sendApplyNotifications()
                    effects.drain()
                    pixelsOf(scene.render(time))
                }
                val settle = {
                    var last = frame()
                    repeat(SETTLE_FRAMES) { last = frame() }
                    last
                }
                val stage = settle()
                first = true
                val before = settle()
                // Both stand as the screens give way, the first going after a while.
                next = true
                val frames = List(GLIDE_FRAMES) { i -> frame().also { if (i == BOTH_FRAMES) first = false } }
                val after = settle()

                val firstRows = PADDING until PADDING + FIRST_HEIGHT
                val nextRows = PADDING + NEXT_TOP until PADDING + NEXT_TOP + NEXT_HEIGHT
                val drawn = { pixels: IntArray, rows: IntRange -> tilePixels(pixels, stage, rows) }
                val whole = 0 until HEIGHT
                assertTrue(drawn(before, firstRows) > 0, "${skin.id}: the first grid is not drawn")
                assertTrue(drawn(after, nextRows) > 0, "${skin.id}: the next grid is not drawn")
                assertTrue(
                    drawn(frames[0], nextRows.last - NEXT_HEIGHT / 3..nextRows.last) == 0,
                    "${skin.id}: the next grid's tiles stood in their own places the frame they came",
                )
                val most = maxOf(drawn(before, whole), drawn(after, whole))
                frames.forEachIndexed { i, pixels ->
                    assertTrue(
                        drawn(pixels, whole) <= most * DOUBLE_WITHIN,
                        "${skin.id}: a tile drawn twice at frame $i: ${drawn(pixels, whole)} of $most",
                    )
                }
                val middles = frames.map { middleRow(it, stage) }
                middles.forEachIndexed { i, middle ->
                    val furthest = middles.take(i).maxOrNull() ?: middle
                    assertTrue(middle >= furthest - BACK_ROWS, "${skin.id}: the tiles went back at frame $i: $middles")
                }
                assertTrue(
                    abs(middles.last() - middleRow(after, stage)) <= BACK_ROWS,
                    "${skin.id}: the glide ended away from the tiles' places",
                )
            } finally {
                scene.close()
            }
        }
    }

    /** How many pixels in [rows] of [pixels] are not the stage's own, [stage] drawn bare. */
    private fun tilePixels(
        pixels: IntArray,
        stage: IntArray,
        rows: IntRange,
    ): Int {
        var count = 0
        for (y in rows.first.coerceAtLeast(0)..rows.last.coerceAtMost(HEIGHT - 1)) {
            for (x in 0 until WIDTH) {
                val i = y * WIDTH + x
                if (distance(pixels[i], stage[i]) > OFF_STAGE) count++
            }
        }
        return count
    }

    /** The middle row of what is drawn over the stage. */
    private fun middleRow(
        pixels: IntArray,
        stage: IntArray,
    ): Float {
        var sum = 0L
        var count = 0
        for (i in pixels.indices) {
            if (distance(pixels[i], stage[i]) > OFF_STAGE) {
                sum += i / WIDTH
                count++
            }
        }
        return if (count == 0) 0f else sum.toFloat() / count
    }

    private fun distance(
        a: Int,
        b: Int,
    ): Int = (0..2).sumOf { channel -> abs((a shr channel * 8 and 0xFF) - (b shr channel * 8 and 0xFF)) }

    private companion object {
        val OPTIONS = listOf("Дунав", "Сава", "Тиса", "Морава")
        const val QUESTION = "question"
        const val WIDTH = 360
        const val HEIGHT = 900
        const val PADDING = 12
        const val FIRST_HEIGHT = 420
        const val NEXT_TOP = 470
        const val NEXT_HEIGHT = 300
        const val SETTLE_FRAMES = 40
        const val GLIDE_FRAMES = 40

        /** How long both grids stand, in frames, as the answers' screen fades out under the reveal's. */
        const val BOTH_FRAMES = 10

        /** How far off the stage's own colour a pixel is to be something drawn over it. */
        const val OFF_STAGE = 30

        /** How much more may be drawn mid-glide than either grid at rest, the tiles grown on their way. */
        const val DOUBLE_WITHIN = 1.2f

        /** How far back, in rows, the drawing's middle may seem to go, its tiles' colours changing on the way. */
        const val BACK_ROWS = 4f
    }
}
