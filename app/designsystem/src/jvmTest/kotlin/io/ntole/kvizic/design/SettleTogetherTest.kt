package io.ntole.kvizic.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.ntole.kvizic.design.component.AnswerTile
import io.ntole.kvizic.design.component.AnswerTileState
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.Chip
import io.ntole.kvizic.design.component.ChipTone
import io.ntole.kvizic.design.component.Stage
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.skin.KvizicSkin
import io.ntole.kvizic.design.skin.Skin
import io.ntole.kvizic.design.skin.Skins
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What stands on a tile or a button turns with its face as the face settles into a new look, never ahead
 * of it: words already light on a face still light vanish a moment, a flicker at every tap. In every skin,
 * a pixel of each, the face's and what is on it, is followed a frame at a time from where it stood to where
 * it settles, and what is on the face is never further along than the face.
 */
class SettleTogetherTest {
    @Test
    fun `a tile's answer and letter go dark with its face as another is locked in`() {
        Skins.ALL.forEach { skin ->
            val tile = skin.parts.tile
            val idle = AnswerTileState.IDLE
            val dimmed = AnswerTileState.DIMMED
            var state by mutableStateOf(idle)
            assertTurnsWithFace(
                "${skin.id}: a tile going dark",
                skin,
                face = tile.surface(idle, 1).fill to tile.surface(dimmed, 1).fill,
                onIt =
                    mapOf(
                        "its answer" to (tile.content(idle) to tile.content(dimmed)),
                        "its mark" to (tile.markColor(idle, 1) to tile.markColor(dimmed, 1)),
                    ),
                change = { state = dimmed },
            ) {
                AnswerTile(1, "Сава", state, Modifier.fillMaxWidth().height(TILE_HEIGHT.dp))
            }
        }
    }

    @Test
    fun `a button's words go out with its face as it is turned off`() {
        Skins.ALL.forEach { skin ->
            val button = skin.parts.button
            var enabled by mutableStateOf(true)
            val on = button.content(ButtonKind.PRIMARY, enabled = true)
            val off = button.content(ButtonKind.PRIMARY, enabled = false)
            assertTurnsWithFace(
                "${skin.id}: a button turned off",
                skin,
                face =
                    button.surface(ButtonKind.PRIMARY, ButtonSize.HERO, enabled = true).fill to
                        button.surface(ButtonKind.PRIMARY, ButtonSize.HERO, enabled = false).fill,
                onIt = if (on == off) emptyMap() else mapOf("its words" to (on to off)),
                change = { enabled = false },
            ) {
                StageButton(
                    "Брза игра",
                    onClick = {},
                    Modifier.fillMaxWidth(),
                    size = ButtonSize.HERO,
                    enabled = enabled,
                )
            }
        }
    }

    @Test
    fun `a chip's word turns with its face as another is picked and back as it is picked again`() {
        Skins.ALL.forEach { skin ->
            val chip = skin.parts.chip
            listOf(true, false).forEach { was ->
                var selected by mutableStateOf(was)
                val before = chip.content(ChipTone.NEUTRAL, was)
                val after = chip.content(ChipTone.NEUTRAL, !was)
                assertTurnsWithFace(
                    "${skin.id}: a chip ${if (was) "left" else "picked"}",
                    skin,
                    face = chip.surface(ChipTone.NEUTRAL, was).fill to chip.surface(ChipTone.NEUTRAL, !was).fill,
                    onIt = if (before == after) emptyMap() else mapOf("its word" to (before to after)),
                    change = { selected = !was },
                ) {
                    Chip("Средње", selected = selected, onClick = {})
                }
            }
        }
    }

    /**
     * Draws [content], makes [change] and draws every frame of its settle as a phone does, the effects a
     * frame launches run only after it is drawn ([AfterTheFrame]); then finds the pixel that best stands for
     * the [face] and for each colour [onIt], each from its first colour to its second, and holds each to go
     * on its way and never back, a frame shown ahead and taken back being a flicker, and what is on the face
     * to go along with the face. With nothing on it that changes, as a skin may keep a chip's word, there is
     * nothing to hold.
     */
    private fun assertTurnsWithFace(
        what: String,
        skin: Skin,
        face: Pair<Color, Color>,
        onIt: Map<String, Pair<Color, Color>>,
        change: () -> Unit,
        content: @Composable () -> Unit,
    ) {
        if (onIt.isEmpty()) return
        val effects = AfterTheFrame()
        val scene =
            ImageComposeScene(WIDTH, HEIGHT, Density(DENSITY), coroutineContext = effects) {
                KvizicSkin(skin) { Stage(Modifier.fillMaxSize()) { Box(Modifier.padding(PADDING.dp)) { content() } } }
            }
        try {
            var time = 0L
            val frame = {
                time += FRAME
                Snapshot.sendApplyNotifications()
                effects.drain()
                pixelsOf(scene.render(time))
            }
            var start = frame()
            while (time < SETTLE * MILLI) start = frame()
            change()
            val frames = List((SETTLE * MILLI / FRAME).toInt()) { frame() }
            val end = frames.last()
            val pixels =
                mapOf("its face" to pixelFor(face, start, end, "$what, its face")) +
                    onIt.mapValues { (name, colours) -> pixelFor(colours, start, end, "$what, $name") }
            val along = pixels.mapValues { (_, pixel) -> frames.map { along(pixel, it, start, end) } }
            along.forEach { (name, steps) ->
                steps.forEachIndexed { i, step ->
                    val furthest = steps.take(i).maxOrNull() ?: 0f
                    assertTrue(
                        step >= furthest - BACK,
                        "$what: $name went back from ${percent(furthest)} to ${percent(step)} at frame $i: $steps",
                    )
                }
            }
            val faceAlong = along.getValue("its face")
            onIt.keys.forEach { name ->
                along.getValue(name).forEachIndexed { i, step ->
                    assertTrue(
                        abs(step - faceAlong[i]) <= APART,
                        "$what: $name is ${percent(step)} along at frame $i, its face ${percent(faceAlong[i])}",
                    )
                }
            }
        } finally {
            scene.close()
        }
    }

    private fun percent(along: Float): String = "${(along * 100).toInt()}%"

    /** The pixel that stands in [colours]' first in [start] and in their second in [end], as nearly as any does. */
    private fun pixelFor(
        colours: Pair<Color, Color>,
        start: IntArray,
        end: IntArray,
        what: String,
    ): Int {
        val (from, to) = colours.first.toArgb() to colours.second.toArgb()
        val best = start.indices.minBy { i -> distance(start[i], from) + distance(end[i], to) }
        assertTrue(distance(start[best], from) + distance(end[best], to) < NEAR, "$what: no pixel stands in it")
        assertTrue(distance(start[best], end[best]) > NEAR, "$what: does not change")
        return best
    }

    /** How far [pixel] of [frame] is along its way from [start] to [end], 0 to 1. */
    private fun along(
        pixel: Int,
        frame: IntArray,
        start: IntArray,
        end: IntArray,
    ): Float = distance(frame[pixel], start[pixel]).toFloat() / distance(end[pixel], start[pixel])

    private fun distance(
        a: Int,
        b: Int,
    ): Int = (0..2).sumOf { channel -> abs((a shr channel * 8 and 0xFF) - (b shr channel * 8 and 0xFF)) }

    private companion object {
        const val WIDTH = 360
        const val HEIGHT = 160
        const val DENSITY = 2f
        const val PADDING = 12
        const val TILE_HEIGHT = 56
        const val SETTLE = 500L

        /** How near, in the three channels' differences together, a pixel's colour stands for one. */
        const val NEAR = 24

        /** How far apart, either way, what is on a face and the face may be along their way: a frame's give. */
        const val APART = 0.25f

        /** How far back a pixel may seem to go, its colour's way not quite a line in the pixels' channels. */
        const val BACK = 0.1f
    }
}
