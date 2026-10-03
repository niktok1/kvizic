package io.ntole.kvizic.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.LocalContentColor
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.cued

/**
 * A surface to group things on, of [kind]: a seat, a scoreboard, the question's screen, the board a
 * code is shown on. Its [content] is set in the panel's text colour, [padding] in from its edges, and
 * fills the panel whenever the panel is given a size.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    kind: PanelKind = PanelKind.PLAIN,
    padding: Dp = Dp.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    val skin = KvizicTheme.skin
    val part = skin.parts.panel
    Box(
        modifier = modifier.raised(part.surface(kind), skin.depth, skin.motion),
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(LocalContentColor provides part.content(kind)) {
            Box(
                modifier = Modifier.padding(if (padding.isSpecified) padding else skin.space.md),
                content = content,
            )
        }
    }
}

/**
 * A small label on a pill, of [tone], with an [icon] before it: a setting, a topic, points won. With an
 * [onClick] it is a button, of a touch target's height at the least, and [selected] marks it chosen, which a
 * screen reader is told.
 */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: ChipTone = ChipTone.NEUTRAL,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val part = skin.parts.chip
    val source = remember { MutableInteractionSource() }
    // Its word turns with its face as it is picked or left, never ahead of it.
    val content = rememberSettlingColor(part.content(tone, selected))
    val tap =
        if (onClick != null) {
            Modifier
                .defaultMinSize(minHeight = space.touchTarget)
                .clickable(
                    interactionSource = source,
                    indication = null,
                    role = Role.Button,
                    onClick = cued(Cue.TAP_SOFT, onClick),
                ).semantics { this.selected = selected }
        } else {
            Modifier
        }
    Box(modifier = modifier.then(tap), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .height(space.chip.height)
                    .raised(part.surface(tone, selected), skin.depth, skin.motion, source),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides part.content(tone, selected)) {
                Row(
                    modifier = Modifier.padding(horizontal = space.chip.paddingHorizontal),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space.xs),
                ) {
                    if (icon != null) {
                        KvizicIcon(icon, contentDescription = null, size = space.icon.small, tintInDraw = content)
                    }
                    KvizicText(text, style = KvizicTheme.type.chip, maxLines = 1, colorInDraw = content)
                }
            }
        }
    }
}
