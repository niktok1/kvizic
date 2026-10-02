package io.ntole.kvizic.room

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.Cues
import io.ntole.kvizic.design.sound.LocalCues
import io.ntole.kvizic.settle
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

/** The phone and the game say how an answer went once per question, and not at all when there was none. */
class FeedbackTest {
    private val felt = CopyOnWriteArrayList<HapticFeedbackType>()
    private val heard = CopyOnWriteArrayList<Cue>()
    private val haptics =
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                felt += hapticFeedbackType
            }
        }
    private val cues =
        object : Cues {
            override fun play(
                cue: Cue,
                volume: Float,
            ) {
                heard += cue
            }
        }

    private fun scene(content: @Composable () -> Unit) =
        ImageComposeScene(width = 10, height = 10) {
            CompositionLocalProvider(LocalHapticFeedback provides haptics, LocalCues provides cues) { content() }
        }

    @Test
    fun `a reveal is felt and heard once for each question and not when there was no answer`() {
        var question by mutableStateOf("q1")
        var right by mutableStateOf<Boolean?>(true)
        var tick by mutableStateOf(0)
        val scene = scene { tick.let { RevealFeedback(question, right) } }
        try {
            scene.settle()
            assertEquals(listOf(HapticFeedbackType.Confirm), felt)
            assertEquals(listOf(Cue.RIGHT), heard)
            tick++
            scene.settle()
            assertEquals(listOf(HapticFeedbackType.Confirm), felt, "a recomposition is felt again")
            assertEquals(listOf(Cue.RIGHT), heard, "a recomposition is heard again")
            question = "q2"
            right = false
            scene.settle()
            assertEquals(listOf(HapticFeedbackType.Confirm, HapticFeedbackType.Reject), felt)
            assertEquals(listOf(Cue.RIGHT, Cue.WRONG), heard)
            question = "q3"
            right = null
            scene.settle()
            assertEquals(2, felt.size, "no answer is felt")
            assertEquals(2, heard.size, "no answer is heard")
        } finally {
            scene.close()
        }
    }

    /** One of the first three right answers sparkles after the ding. */
    @Test
    fun `a bonus sparkles after the ding`() {
        val scene = scene { RevealFeedback("q1", right = true, bonus = true) }
        try {
            scene.settle()
            assertEquals(listOf(Cue.RIGHT), heard, "the ding is first")
            val until = System.nanoTime() + 3_000_000_000
            while (Cue.BONUS !in heard && System.nanoTime() < until) Thread.sleep(10)
            assertEquals(listOf(Cue.RIGHT, Cue.BONUS), heard)
        } finally {
            scene.close()
        }
    }
}
