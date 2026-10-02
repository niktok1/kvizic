package io.ntole.kvizic.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme

/**
 * How many play and how many look for a game, in no words and no bigger than a line of text: a lit bulb, a
 * person and the players online, a magnifying glass and those searching, each count on flaps that turn as it
 * moves. On a small board sunk into the page while [framed]; without it, bare and in plain figures of the colour
 * of what it stands on, for where something carries it already, as the hero button's face does. A screen reader
 * is told [contentDescription] whole.
 */
@Composable
fun PresenceStrip(
    online: Int,
    searching: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
) {
    val space = KvizicTheme.space
    val colors = KvizicTheme.colors
    val counts: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space.xs),
        ) {
            // Lit, not pulsing: the flaps are what moves.
            Box(
                Modifier.size(space.sm).drawBehind {
                    drawCircle(colors.gain, radius = size.minDimension, alpha = HALO)
                    drawCircle(colors.gain)
                },
            )
            Spacer(Modifier.width(space.xxs))
            KvizicIcon(KvizicIcons.Person, contentDescription = null, size = space.icon.small)
            Count(online, flaps = framed)
            Spacer(Modifier.width(space.sm))
            KvizicIcon(KvizicIcons.Search, contentDescription = null, size = space.icon.small)
            Count(searching, flaps = framed)
        }
    }
    val said = modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
    if (framed) {
        Panel(said, kind = PanelKind.WELL, padding = space.xs) { counts() }
    } else {
        Box(said) { counts() }
    }
}

/** A count on flaps, or, [flaps] off, as plain figures in the colour of what they stand on. */
@Composable
private fun Count(
    value: Int,
    flaps: Boolean,
) {
    if (flaps) {
        FlipNumber(value, size = FlapSize.SMALL)
    } else {
        KvizicText(value.toString(), style = KvizicTheme.type.bodyStrong, maxLines = 1)
    }
}

/** How strong the glow round the lit bulb is: a quarter of its colour. */
private const val HALO = 0.25f
