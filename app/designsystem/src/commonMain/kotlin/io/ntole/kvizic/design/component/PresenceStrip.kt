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
 * person and the players online, a magnifying glass and those searching, on a small board sunk into the page,
 * each count on flaps that turn as it moves. A screen reader is told [contentDescription] whole.
 */
@Composable
fun PresenceStrip(
    online: Int,
    searching: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val space = KvizicTheme.space
    val colors = KvizicTheme.colors
    Panel(
        modifier.clearAndSetSemantics { this.contentDescription = contentDescription },
        kind = PanelKind.WELL,
        padding = space.xs,
    ) {
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
            FlipNumber(online, size = FlapSize.SMALL)
            Spacer(Modifier.width(space.sm))
            KvizicIcon(KvizicIcons.Search, contentDescription = null, size = space.icon.small)
            FlipNumber(searching, size = FlapSize.SMALL)
        }
    }
}

/** How strong the glow round the lit bulb is: a quarter of its colour. */
private const val HALO = 0.25f
