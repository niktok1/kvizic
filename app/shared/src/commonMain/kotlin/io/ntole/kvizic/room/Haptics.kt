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

/** [onPick], with a tap of the phone as the answer locks in. */
@Composable
internal fun withLockInHaptic(onPick: (Int) -> Unit): (Int) -> Unit {
    val haptics = LocalHapticFeedback.current
    val action by rememberUpdatedState(onPick)
    return { index ->
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        action(index)
    }
}

/**
 * The phone says how the player's answer to question [questionId] went, once, as its reveal is first shown:
 * [right] a confirm, wrong a reject, and nothing when they did not answer ([right] null). Kept in saved state,
 * so an Android activity made anew mid-reveal, on a rotation, says nothing again.
 */
@Composable
internal fun RevealHaptic(
    questionId: String,
    right: Boolean?,
) {
    val haptics = LocalHapticFeedback.current
    var felt by rememberSaveable(questionId) { mutableStateOf(false) }
    LaunchedEffect(questionId) {
        if (felt || right == null) return@LaunchedEffect
        felt = true
        haptics.performHapticFeedback(if (right) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
    }
}
