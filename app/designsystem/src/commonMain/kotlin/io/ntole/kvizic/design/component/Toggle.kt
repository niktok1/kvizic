package io.ntole.kvizic.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpSize
import io.ntole.kvizic.design.skin.KvizicTheme

/**
 * A switch for one setting, [checked] or not, which [onCheckedChange] flips: a key in a well, as on a game
 * show's desk. The key stands at the well's start while off, a plain key with its lamp out, and at its end
 * while on, in the skin's primary colour with its lamp lit; it sinks under the finger as a button does, and slides and lights in
 * the draw alone. The whole row, [label] and switch, is the switch, of a touch target's height at the
 * least, which a screen reader hears as the label, a switch, and on or off.
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
    val presses = remember { MutableInteractionSource() }
    val wellHeight = space.chip.height + space.sm
    val well = DpSize(wellHeight * WELL_LENGTH, wellHeight)
    val inset = space.xxs
    val keyHeight = wellHeight - inset * 2
    val key = DpSize(keyHeight * KEY_LENGTH, keyHeight)
    val travel = well.width - inset * 2 - key.width
    val keyLook =
        skin.parts.button.surface(
            kind = if (checked) ButtonKind.PRIMARY else ButtonKind.SECONDARY,
            size = ButtonSize.SMALL,
            enabled = enabled,
        )
    Row(
        modifier =
            modifier
                .defaultMinSize(minHeight = space.touchTarget)
                .toggleable(
                    value = checked,
                    interactionSource = presses,
                    indication = null,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        KvizicText(label, Modifier.weight(1f), color = if (enabled) colors.onPage else colors.onPageMuted)
        Box(
            Modifier.size(well).raised(skin.parts.panel.surface(PanelKind.WELL), skin.depth, skin.motion),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(start = inset)
                    .size(key)
                    .graphicsLayer { translationX = travel.toPx() * on }
                    .raised(keyLook, skin.depth, skin.motion, interactionSource = presses),
            ) {
                // The key's lamp, out while off and lit while on, a halo round it as it lights.
                Spacer(
                    Modifier.fillMaxSize().drawBehind {
                        val radius = size.minDimension * LAMP
                        val lamp = lerp(colors.bulbOff, colors.bulbOn, if (enabled) on else 0f)
                        if (on > 0f && enabled) {
                            drawCircle(
                                Brush.radialGradient(
                                    0f to colors.bulbOn.copy(alpha = HALO * on),
                                    1f to Color.Transparent,
                                    center = center,
                                    radius = radius * HALO_REACH,
                                ),
                                radius * HALO_REACH,
                            )
                        }
                        drawCircle(lamp, radius)
                        drawCircle(colors.outline, radius, style = Stroke(space.strokeThin.toPx()))
                    },
                )
            }
        }
    }
}

/** How long a switch's well is, in its heights, and its key, in the key's. */
private const val WELL_LENGTH = 2.1f
private const val KEY_LENGTH = 1.15f

/** The lamp's radius, a share of the key's face, and its halo's reach and strength when lit. */
private const val LAMP = 0.18f
private const val HALO_REACH = 2.4f
private const val HALO = 0.6f
