package io.ntole.kvizic.design

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.design.component.AnswerTile
import io.ntole.kvizic.design.component.AnswerTileState
import io.ntole.kvizic.design.component.CodeDisplay
import io.ntole.kvizic.design.component.FlipNumber
import io.ntole.kvizic.design.component.QuestionTimer
import io.ntole.kvizic.design.component.ReactionBurst
import io.ntole.kvizic.design.component.Spinner
import io.ntole.kvizic.design.component.Stage
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.component.TimerPhase
import io.ntole.kvizic.design.component.Wordmark
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicSkin
import io.ntole.kvizic.design.skin.Skin
import io.ntole.kvizic.design.skin.Skins
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The motion rule, in every skin: a frame of any animation composes nothing. The press of a tile and a
 * button, a tile locking in and lighting up, digits flapping, a running timer, the spinner and a burst:
 * each stepped a frame at a time on the scene's clock at 60 a second while the runtime's own observer
 * counts every scope composed, which must not move once the change that started the motion is composed;
 * and each frame drawn, so a motion that moved nothing does not pass for one that composed nothing.
 */
class MotionCompositionTest {
    @Test
    fun `a press sinks a tile and a button and composes nothing`() {
        Skins.ALL.forEach { skin ->
            val tile = MutableInteractionSource()
            val button = MutableInteractionSource()
            motion(skin, {
                Box(Modifier.fillMaxSize()) {
                    AnswerTile(0, "Дунав", AnswerTileState.IDLE, onClick = {}, interactionSource = tile)
                    StageButton(
                        "Брза игра",
                        onClick = {},
                        Modifier.align(Alignment.BottomCenter),
                        interactionSource = button,
                    )
                }
            }) {
                listOf(tile, button).forEach { source ->
                    val press = PressInteraction.Press(Offset.Zero)
                    step("a press", start = { source.tryEmit(press) }, millis = SHORT)
                    step("its release", start = { source.tryEmit(PressInteraction.Release(press)) }, millis = LONG)
                }
            }
        }
    }

    @Test
    fun `a tile locking in and lighting up composes nothing past the change`() {
        Skins.ALL.forEach { skin ->
            var state by mutableStateOf(AnswerTileState.IDLE)
            motion(skin, { AnswerTile(1, "Сава", state, onClick = {}) }) {
                step("locking in", start = { state = AnswerTileState.LOCKED_IN }, millis = LONG)
                step("the reveal", start = { state = AnswerTileState.CORRECT }, millis = LONG)
                step("going dark", start = { state = AnswerTileState.DIMMED }, millis = LONG)
            }
        }
    }

    @Test
    fun `digits flap and compose nothing past the new value`() {
        Skins.ALL.forEach { skin ->
            var score by mutableIntStateOf(1_240)
            var code by mutableStateOf("482915")
            motion(skin, {
                Box(Modifier.fillMaxSize()) {
                    FlipNumber(score)
                    CodeDisplay(code, Modifier.align(Alignment.BottomCenter))
                }
            }) {
                step("a score flapping on", start = { score = 1_328 }, millis = LONG)
                step("a code flapping on", start = { code = "107364" }, millis = LONG)
            }
        }
    }

    @Test
    fun `a running timer and the spinner compose nothing`() {
        Skins.ALL.forEach { skin ->
            var phase by mutableStateOf<TimerPhase>(TimerPhase.Waiting(15_000))
            motion(skin, {
                Box(Modifier.fillMaxSize()) {
                    QuestionTimer(phase, contentDescription = "15")
                    Spinner(Modifier.align(Alignment.BottomEnd))
                }
            }) {
                step(
                    "the lights coming up",
                    start = { phase = TimerPhase.Reading(15_000, 2_000, 2_000) },
                    millis = LONG,
                )
                step("the time running", start = { phase = TimerPhase.Running(15_000, 3_000) }, millis = TIMER)
            }
        }
    }

    @Test
    fun `a reaction's burst composes nothing`() {
        Skins.ALL.forEach { skin ->
            var burst by mutableIntStateOf(0)
            motion(skin, {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ReactionBurst(
                        KvizicIcons.Flame,
                        burst.takeIf {
                            it >
                                0
                        },
                    )
                }
            }) {
                step("a burst", start = { burst++ }, millis = LONG)
            }
        }
    }

    /** Only the stage's sign has lights; the notebook's sticky note has none to flicker. */
    @Test
    fun `the sign's bulbs flicker now and then and compose nothing`() {
        motion(Skins.Buzzers, { Wordmark("КВИЗИЋ", Modifier.fillMaxWidth()) }) {
            step("a bulb's flicker", start = {}, millis = FLICKER_WITHIN)
        }
    }

    /** A scene of [content] in [skin], its composition counted, and [steps] run on it. */
    private fun motion(
        skin: Skin,
        content: @Composable () -> Unit,
        steps: Steps.() -> Unit,
    ) {
        val recompositions = Recompositions()
        val scene =
            ImageComposeScene(width = SIZE, height = SIZE, density = Density(1f)) {
                CountedBy(recompositions) { KvizicSkin(skin) { Stage(Modifier.fillMaxSize()) { content() } } }
            }
        try {
            Steps(skin, scene, recompositions).apply {
                until(SETTLE)
                steps()
                recompositions.assertCounting(scene, time)
            }
        } finally {
            scene.close()
        }
    }

    private class Steps(
        val skin: Skin,
        val scene: ImageComposeScene,
        val recompositions: Recompositions,
    ) {
        var time = 0L

        fun until(millis: Long) {
            val end = time + millis * MILLI
            while (time < end) {
                time += FRAME
                scene.renderAt(time)
            }
        }

        /**
         * Starts a motion with [start], lets the frame that composes the change go by, then steps the
         * motion through [millis], each frame drawn and none composing anything, and something drawn
         * differently along the way.
         */
        fun step(
            what: String,
            start: () -> Unit,
            millis: Long,
        ) {
            start()
            until(CHANGE)
            val before = recompositions.scopesEntered
            val first = pixelsOf(scene.render(time))
            var moved = false
            val end = time + millis * MILLI
            while (time < end) {
                time += FRAME
                scene.renderAt(time)
                if (!moved) moved = !pixelsOf(scene.render(time)).contentEquals(first)
            }
            assertEquals(before, recompositions.scopesEntered, "${skin.id}: a frame of $what composed")
            assertTrue(moved, "${skin.id}: $what drew nothing new")
        }
    }

    private companion object {
        /** Longer than the longest wait between two flickers of the sign, and one flicker. */
        const val FLICKER_WITHIN = 7_000L

        const val SIZE = 420
        const val SETTLE = 300L

        /** Two frames: the one that composes a change and the one it first draws in. */
        const val CHANGE = 34L
        const val SHORT = 200L
        const val LONG = 900L
        const val TIMER = 2_500L
    }
}
