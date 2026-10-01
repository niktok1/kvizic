package io.ntole.kvizic.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpSize
import io.ntole.kvizic.design.skin.KvizicTheme

/**
 * A switch for one setting, [checked] or not, which [onCheckedChange] flips: a pill lit in the skin's
 * primary colour while on, its knob at the end it stands at, sliding in the draw alone. The whole row,
 * [label] and pill, is the switch, of a touch target's height at the least, which a screen reader hears as
 * the label, a switch, and on or off.
 */
@Composable
fun Toggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val colors = skin.colors
    val on by animateFloatAsState(if (checked) 1f else 0f, tween(skin.motion.settle), label = "toggle")
    val track = DpSize(space.chip.height * TRACK_LENGTH, space.chip.height)
    Row(
        modifier =
            modifier
                .defaultMinSize(minHeight = space.touchTarget)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        KvizicText(label, Modifier.weight(1f), color = if (enabled) colors.onPage else colors.onPageMuted)
        Spacer(
            Modifier.size(track).drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(if (checked) colors.primary else colors.raised, cornerRadius = radius)
                drawRoundRect(colors.outline, cornerRadius = radius, style = Stroke(space.strokeThin.toPx()))
                val knob = size.height / 2 - space.xxs.toPx()
                val x = size.height / 2 + (size.width - size.height) * on
                drawCircle(if (checked) colors.onPrimary else colors.onRaisedMuted, knob, Offset(x, size.height / 2))
            },
        )
    }
}

/** How long a toggle's pill is, in its heights. */
private const val TRACK_LENGTH = 1.8f
