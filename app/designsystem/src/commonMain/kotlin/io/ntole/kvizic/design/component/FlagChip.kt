package io.ntole.kvizic.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.cued

/**
 * A flag to pick and no word: a country. [flag] is drawn in its own colours, never a skin's, a hairline round it
 * so a white stripe stands off the page, and [selected] rings it in the colour a player's own choice glows in
 * (`primary`). It takes the width it is given and sizes the flag to it, every flag as high as a 2:1 one that
 * fills it would be, never past a medium icon's height, so a row of them shares out its width and never wraps.
 * It is a touch target's height at the least, and a screen reader hears it as [contentDescription], the country's
 * name, and whether it is the one picked.
 */
@Composable
fun FlagChip(
    flag: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val space = KvizicTheme.space
    val colors = KvizicTheme.colors
    val source = remember { MutableInteractionSource() }
    val ring = space.strokeThin
    val gap = space.xxs
    BoxWithConstraints(
        modifier =
            modifier
                .defaultMinSize(minHeight = space.touchTarget)
                .clickable(
                    interactionSource = source,
                    indication = null,
                    role = Role.Button,
                    onClick = cued(Cue.TAP_SOFT, onClick),
                ).semantics {
                    this.contentDescription = contentDescription
                    this.selected = selected
                },
        contentAlignment = Alignment.Center,
    ) {
        val height = min(space.icon.medium, (maxWidth - (ring + gap) * 2) / 2f).coerceAtLeast(Dp.Hairline)
        val corner = RoundedCornerShape(space.xxs)
        val around = RoundedCornerShape(space.xxs + ring + gap)
        Image(
            painter = rememberVectorPainter(flag),
            contentDescription = null,
            modifier =
                Modifier
                    .border(ring, if (selected) colors.primary else Color.Transparent, around)
                    .padding(ring + gap)
                    .size(width = height * (flag.defaultWidth / flag.defaultHeight), height = height)
                    .clip(corner)
                    .border(Dp.Hairline, colors.outline, corner),
        )
    }
}
