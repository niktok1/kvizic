package io.ntole.kvizic.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.LocalCues
import io.ntole.kvizic.design.sound.cued

/**
 * A question put to the player over the screen, on a panel: its [title], a line of [text] under it, and its
 * [buttons] at the end, the one that goes ahead last. A tap outside or the system's back is [onDismiss].
 */
@Composable
fun StageDialog(
    onDismiss: () -> Unit,
    title: String,
    text: String? = null,
    buttons: @Composable () -> Unit,
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val cues = LocalCues.current
    LaunchedEffect(Unit) { cues.play(Cue.DIALOG_OPEN) }
    Dialog(onDismissRequest = cued(Cue.DIALOG_CLOSE, onDismiss)) {
        Panel(Modifier.widthIn(max = space.dialogWidth), padding = space.lg) {
            Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                KvizicText(title, style = type.bodyStrong)
                if (text != null) KvizicText(text, style = type.body)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(space.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) { buttons() }
            }
        }
    }
}

/** A line across the width, between groups of things. */
@Composable
fun Divider(modifier: Modifier = Modifier) {
    val skin = KvizicTheme.skin
    Spacer(modifier.fillMaxWidth().height(skin.space.strokeThin).background(skin.colors.outline))
}
