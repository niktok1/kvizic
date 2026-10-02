package io.ntole.kvizic.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.max
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.LocalContentColor
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.cued

/**
 * A button on the stage: [text], and under it [supportingText] if given, on the skin's surface for
 * [kind], [size] high at the least and never under a touch target. It sinks under the finger and
 * springs back, in the draw alone. [icon] stands before the text, or over it when [iconAbove], a
 * console's key; [trailing] stands after it, in a row: a count, a chevron; [footer] stands at the foot of the
 * button's face, over it and never moving the words from its middle: a small sign it carries, as the hero button
 * carries how many play. A tap plays [cue], by default the
 * sound of its [kind] and [size]: the hero's and the primary's heavy, a plate's a click, a quiet word's a tick.
 */
@Composable
fun StageButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    size: ButtonSize = ButtonSize.REGULAR,
    icon: ImageVector? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    iconAbove: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    trailing: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    cue: Cue? = null,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val type = KvizicTheme.type
    val part = skin.parts.button
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val tap = cued(cue ?: cueOf(kind, size), onClick)
    // Its words turn with its face as it is turned on or off, never ahead of it.
    val content = rememberSettlingColor(part.content(kind, enabled))
    val supporting = rememberSettlingColor(part.supporting(kind, enabled))
    val height =
        when (size) {
            ButtonSize.HERO -> space.button.hero
            ButtonSize.REGULAR -> space.button.regular
            ButtonSize.SMALL -> space.button.small
        }
    val label =
        when (size) {
            ButtonSize.HERO -> type.hero
            ButtonSize.REGULAR -> type.button
            ButtonSize.SMALL -> type.buttonSmall
        }
    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = max(height, space.touchTarget))
                .clickable(
                    interactionSource = source,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = tap,
                ).raised(
                    look = part.surface(kind, size, enabled),
                    depth = skin.depth,
                    motion = skin.motion,
                    interactionSource = source,
                    focus = skin.colors.focus,
                    focusWidth = space.strokeThin,
                ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides part.content(kind, enabled)) {
            val words: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    KvizicText(
                        text,
                        style = label,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        colorInDraw = content,
                        // A long word in a narrow button shrinks rather than being cut.
                        autoSize =
                            TextAutoSize.StepBased(
                                minFontSize = type.answerMin.style.fontSize,
                                maxFontSize = label.style.fontSize,
                            ),
                    )
                    if (supportingText != null) {
                        KvizicText(
                            supportingText,
                            style = type.caption,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            colorInDraw = supporting,
                        )
                    }
                }
            }
            val iconSize = if (size == ButtonSize.HERO || iconAbove) space.icon.large else space.icon.medium
            if (iconAbove) {
                // A tile's words stand under its icon with less room to the sides: two of them share a row.
                Column(
                    modifier = Modifier.padding(horizontal = space.sm, vertical = space.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(space.xs),
                ) {
                    if (icon != null) KvizicIcon(icon, contentDescription = null, size = iconSize, tintInDraw = content)
                    words()
                }
            } else {
                val row: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier.padding(horizontal = space.button.paddingHorizontal),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(space.sm),
                    ) {
                        if (icon != null) {
                            KvizicIcon(icon, contentDescription = null, size = iconSize, tintInDraw = content)
                        }
                        words()
                        trailing?.invoke()
                    }
                }
                row()
                // On the face's foot, over it: the words stay in the middle of the button, as without it.
                if (footer != null) {
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = space.sm)) { footer() }
                }
            }
        }
    }
}

/**
 * A round button of one [icon], named [contentDescription] for a screen reader: back, share, a
 * reaction. Raised and pressed as a [StageButton] is; a tap plays [cue].
 */
@Composable
fun StageIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.DARK,
    small: Boolean = false,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    cue: Cue = Cue.TAP_SOFT,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val part = skin.parts.button
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val tap = cued(cue, onClick)
    val look = part.surface(kind, ButtonSize.SMALL, enabled).copy(shape = skin.shapes.roundButton)
    val tint = rememberSettlingColor(part.content(kind, enabled))
    val whole = if (small) space.button.roundSmall else space.button.round
    Box(
        modifier =
            modifier
                .size(max(whole, space.touchTarget))
                .semantics { this.contentDescription = contentDescription }
                .clickable(
                    interactionSource = source,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = tap,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(whole)
                    .raised(look, skin.depth, skin.motion, source, skin.colors.focus, space.strokeThin),
            contentAlignment = Alignment.Center,
        ) {
            KvizicIcon(
                icon,
                contentDescription = null,
                size = if (small) space.icon.small else space.icon.medium,
                tintInDraw = tint,
            )
        }
    }
}

/** The sound of a tap on a button of [kind] and [size]: the hero's and the primary's heavy, a plate's a click, a quiet word's a tick. */
internal fun cueOf(
    kind: ButtonKind,
    size: ButtonSize,
): Cue =
    when {
        size == ButtonSize.HERO || kind == ButtonKind.PRIMARY -> Cue.TAP_PRIMARY
        kind == ButtonKind.QUIET -> Cue.TAP_SOFT
        else -> Cue.TAP
    }
