package io.ntole.kvizic.room

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.LocalCues
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** [onPick], with a tap of the phone and the buzzer's hit as the answer locks in. */
@Composable
internal fun withLockInFeedback(onPick: (Int) -> Unit): (Int) -> Unit {
    val haptics = LocalHapticFeedback.current
    val cues = LocalCues.current
    val action by rememberUpdatedState(onPick)
    return { index ->
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        cues.play(Cue.LOCK)
        action(index)
    }
}

/**
 * The phone and the game say how the player's answer to question [questionId] went, once, as its reveal is
 * first shown: [right] a confirm and a ding, and when [bonus] (one of the first three right) the bonus's
 * sparkle after it; wrong a reject and a low buzz; and nothing when they did not answer ([right] null),
 * for whose time running out the question's timer has sounded. Kept in saved state, so an Android activity
 * made anew mid-reveal, on a rotation, says nothing again.
 */
@Composable
internal fun RevealFeedback(
    questionId: String,
    right: Boolean?,
    bonus: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val cues = LocalCues.current
    var felt by rememberSaveable(questionId) { mutableStateOf(false) }
    LaunchedEffect(questionId) {
        if (felt || right == null) return@LaunchedEffect
        felt = true
        haptics.performHapticFeedback(if (right) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
        cues.play(if (right) Cue.RIGHT else Cue.WRONG)
        if (right && bonus) {
            delay(BONUS_AFTER)
            cues.play(Cue.BONUS)
        }
    }
}

/** The ding is said before the bonus's sparkle starts over it. */
private val BONUS_AFTER = 380.milliseconds
