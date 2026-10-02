package io.ntole.kvizic.design.sound

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * [onClick], playing [cue] first: what every control that takes a tap hands its `onClick`, so a tap is
 * heard the moment it counts, a press that slides off or becomes a scroll never. The cue is the control's
 * kind of sound, which the skin gives its own voice, and a screen drawn alone has [Cues.None], silent.
 */
@Composable
fun cued(
    cue: Cue,
    onClick: () -> Unit,
): () -> Unit {
    val cues = LocalCues.current
    val action by rememberUpdatedState(onClick)
    return remember(cues, cue) {
        {
            cues.play(cue)
            action()
        }
    }
}
