package io.ntole.kvizic.room

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.ntole.kvizic.settle
import kotlin.test.Test
import kotlin.test.assertEquals

/** The phone says how an answer went once per question, and not at all when there was none. */
class HapticsTest {
    private val felt = mutableListOf<HapticFeedbackType>()
    private val haptics =
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                felt += hapticFeedbackType
            }
        }

    @Test
    fun `a reveal is felt once for each question, right or wrong`() {
        var question by mutableStateOf("q1")
        var right by mutableStateOf<Boolean?>(true)
        var tick by mutableStateOf(0)
        val scene =
            ImageComposeScene(width = 10, height = 10) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    tick.let { RevealHaptic(question, right) }
                }
            }
        try {
            scene.settle()
            assertEquals(listOf(HapticFeedbackType.Confirm), felt)
            tick++
            scene.settle()
            assertEquals(listOf(HapticFeedbackType.Confirm), felt, "a recomposition is felt again")
            question = "q2"
            right = false
            scene.settle()
            assertEquals(listOf(HapticFeedbackType.Confirm, HapticFeedbackType.Reject), felt)
            question = "q3"
            right = null
            scene.settle()
            assertEquals(2, felt.size, "no answer is felt")
        } finally {
            scene.close()
        }
    }
}
