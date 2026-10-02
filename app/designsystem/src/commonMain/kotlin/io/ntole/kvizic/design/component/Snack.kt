package io.ntole.kvizic.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.ntole.kvizic.design.skin.KvizicTheme

/**
 * A line said over a screen for a moment, on a card outlined in the skin's accent, so it is told from the
 * screen it stands over: what changed in a room, what is about to, what was refused. [icon] names its kind
 * before the words. With an [action], the one word that answers it, as a small button ([onAction]); a snack
 * that asks something stays until it is answered, which the screen decides.
 *
 * It comes down from the top edge once, in its layer alone, and a screen reader hears it as it comes.
 */
@Composable
fun Snack(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val type = KvizicTheme.type
    val entered = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entered.animateTo(1f, tween(skin.motion.stage, easing = FastOutSlowInEasing)) }
    Panel(
        modifier =
            modifier
                .graphicsLayer {
                    translationY = -(1f - entered.value) * (size.height + space.md.toPx())
                    alpha = entered.value
                }.semantics { liveRegion = LiveRegionMode.Polite },
        kind = PanelKind.OWN,
        padding = space.sm,
    ) {
        // The word that answers stands under the words it answers, so they have the whole width.
        Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                if (icon != null) KvizicIcon(icon, contentDescription = null, size = space.icon.medium)
                KvizicText(text, Modifier.weight(1f), style = type.body, maxLines = MAX_LINES)
            }
            if (action != null) {
                StageButton(
                    action,
                    onClick = onAction,
                    modifier = Modifier.align(Alignment.End),
                    kind = ButtonKind.SECONDARY,
                    size = ButtonSize.SMALL,
                )
            }
        }
    }
}

/** A snack's words are kept short, and never cut before this many lines. */
private const val MAX_LINES = 3
