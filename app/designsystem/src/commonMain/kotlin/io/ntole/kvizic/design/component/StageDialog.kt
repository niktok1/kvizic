package io.ntole.kvizic.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.LocalCues
import io.ntole.kvizic.design.sound.cued

/**
 * A question put to the player over the screen, on a panel: its [title], a line of [text] under it, and its
 * [buttons] at the end, the one that goes ahead last. A tap outside or the system's back is [onDismiss].
 *
 * The buttons stand in a row at the panel's end while they fit it at their own width; where they do not, on a
 * narrow phone or with a long word, each takes the panel's width, one over another, the one that goes ahead
 * on top and the way back at the foot, so the main action is never the one squeezed ([DialogButtons]).
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
                DialogButtons(gap = space.sm, content = buttons)
            }
        }
    }
}

/**
 * A dialog's buttons: in a row at the end, each at its own width and centred on the row's line, while their
 * widths and the [gap]s between fit; otherwise stacked across the whole width, the last, the one that goes
 * ahead, on top. One child, as a column of choices, takes the width it asks for.
 */
@Composable
private fun DialogButtons(
    gap: Dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val space = gap.roundToPx()
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val wanted = measurables.sumOf { it.maxIntrinsicWidth(constraints.maxHeight) } + space * (measurables.size - 1)
        if (measurables.size <= 1 || !constraints.hasBoundedWidth || wanted <= width) {
            var left = width
            val placeables =
                measurables.map { measurable ->
                    measurable.measure(loose.copy(maxWidth = left.coerceAtLeast(0))).also { left -= it.width + space }
                }
            val height = placeables.maxOfOrNull { it.height } ?: 0
            val row = placeables.sumOf { it.width } + space * (placeables.size - 1).coerceAtLeast(0)
            val laid = if (constraints.hasBoundedWidth) width else row
            layout(laid, height) {
                var x = laid - row
                placeables.forEach {
                    it.place(x, (height - it.height) / 2)
                    x += it.width + space
                }
            }
        } else {
            val placeables = measurables.map { it.measure(loose.copy(minWidth = width, maxWidth = width)) }.reversed()
            val height = placeables.sumOf { it.height } + space * (placeables.size - 1)
            layout(width, height) {
                var y = 0
                placeables.forEach {
                    it.place(0, y)
                    y += it.height + space
                }
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
