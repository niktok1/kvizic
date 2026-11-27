package io.ntole.kvizic.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.LocalCues
import io.ntole.kvizic.design.sound.cued
import kotlin.math.roundToInt

/**
 * One of a room's settings on a tile: its [name] across the tile's top, lit while [explained], its [art] under it,
 * and its [control] across the tile's foot, a [StepKey] or a [TileSwitch]. A tap on the name or the art asks
 * [onExplain] to say what the setting is, which a screen reader hears as [name] and [said]. The name shrinks to
 * stay on one line rather than break.
 */
@Composable
fun SettingTile(
    name: String,
    said: String,
    onExplain: () -> Unit,
    control: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    explained: Boolean = false,
    art: @Composable BoxScope.() -> Unit,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val label = KvizicTheme.type.label
    val words =
        rememberSettlingColor(if (explained) skin.colors.onPageAccent else skin.parts.panel.muted(PanelKind.PLAIN))
    Panel(modifier.fillMaxWidth(), padding = space.xs) {
        Column(verticalArrangement = Arrangement.spacedBy(space.xs)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = cued(Cue.TAP_SOFT, onExplain))
                    .semantics(mergeDescendants = true) { contentDescription = "$name, $said" },
            ) {
                KvizicText(
                    name,
                    Modifier.fillMaxWidth().padding(top = space.xxs),
                    style = label,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(label.style.fontSize * NAME_LEAST, label.style.fontSize),
                    colorInDraw = words,
                )
                Box(
                    Modifier.fillMaxWidth().height(space.timer.small + space.sm),
                    contentAlignment = Alignment.Center,
                    content = art,
                )
            }
            control()
        }
    }
}

/**
 * A key across a tile split in two, − and +, which call [onLess] and [onMore]: a half whose call is null is off,
 * where the setting can go no further. The whole key sinks under either half. A screen reader hears the halves as
 * [lessLabel] and [moreLabel].
 */
@Composable
fun StepKey(
    onLess: (() -> Unit)?,
    onMore: (() -> Unit)?,
    lessLabel: String,
    moreLabel: String,
    modifier: Modifier = Modifier,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val presses = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .height(space.touchTarget)
            .raised(
                skin.parts.button.surface(ButtonKind.DARK, ButtonSize.SMALL, true),
                skin.depth,
                skin.motion,
                presses,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(Triple(false, onLess, lessLabel), Triple(true, onMore, moreLabel)).forEach { (plus, onStep, label) ->
            if (plus) {
                Spacer(
                    Modifier
                        .width(space.strokeThin)
                        .fillMaxHeight(DIVIDER)
                        .drawBehind { drawRect(skin.colors.outline) },
                )
            }
            val glyph = rememberSettlingColor(skin.parts.button.content(ButtonKind.DARK, onStep != null))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = presses,
                        indication = null,
                        enabled = onStep != null,
                        role = Role.Button,
                        onClick = cued(Cue.KEY) { onStep?.invoke() },
                    ).semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                StepGlyph(plus, glyph)
            }
        }
    }
}

@Composable
private fun StepGlyph(
    plus: Boolean,
    color: ColorProducer,
) {
    val space = KvizicTheme.space
    Spacer(
        Modifier.size(space.icon.small).drawBehind {
            val width = space.stroke.toPx()
            val ink = color()
            drawLine(ink, Offset(width / 2, center.y), Offset(size.width - width / 2, center.y), width, StrokeCap.Round)
            if (plus) {
                drawLine(
                    ink,
                    Offset(center.x, width / 2),
                    Offset(center.x, size.height - width / 2),
                    width,
                    StrokeCap.Round,
                )
            }
        },
    )
}

/**
 * A switch across a tile, [checked] or not, which [onCheckedChange] flips: a well with [offIcon] engraved at its
 * start and [onIcon] at its end, and a key that slides from one to the other, lit and its lamp on while checked.
 * It slides and lights in the draw alone. A screen reader hears [label], a switch, and on or off.
 */
@Composable
fun TileSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    offIcon: ImageVector? = null,
    onIcon: ImageVector? = null,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val colors = skin.colors
    val cues = LocalCues.current
    val on = animateFloatAsState(if (checked) 1f else 0f, tween(skin.motion.settle), label = "tile switch")
    val presses = remember { MutableInteractionSource() }
    val engraved = skin.parts.panel.muted(PanelKind.WELL)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(space.touchTarget)
            .toggleable(
                value = checked,
                interactionSource = presses,
                indication = null,
                role = Role.Switch,
                onValueChange = { value ->
                    cues.play(if (value) Cue.TOGGLE_ON else Cue.TOGGLE_OFF)
                    onCheckedChange(value)
                },
            ).semantics { contentDescription = label }
            .raised(skin.parts.panel.surface(PanelKind.WELL), skin.depth, skin.motion)
            .padding(space.xxs),
    ) {
        val key = maxWidth * KEY_SHARE
        val travel = maxWidth - key
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(offIcon, onIcon).forEach { icon ->
                Box(Modifier.width(travel).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (icon !=
                        null
                    ) {
                        KvizicIcon(icon, contentDescription = null, tint = engraved, size = space.icon.small)
                    }
                }
            }
        }
        Box(
            Modifier
                .width(key)
                .fillMaxHeight()
                .graphicsLayer { translationX = travel.toPx() * on.value }
                .raised(
                    skin.parts.button.surface(
                        if (checked) ButtonKind.PRIMARY else ButtonKind.SECONDARY,
                        ButtonSize.SMALL,
                        true,
                    ),
                    skin.depth,
                    skin.motion,
                    presses,
                ),
        ) {
            Spacer(
                Modifier.fillMaxSize().drawBehind {
                    val radius = size.minDimension * LAMP
                    drawCircle(lerp(colors.bulbOff, colors.bulbOn, on.value), radius)
                    drawCircle(colors.outline, radius, style = Stroke(space.strokeThin.toPx()))
                },
            )
        }
    }
}

/**
 * A bubble saying what a setting is, its [title] over its [text] on a plate like an answer's tile, standing over a row of [count] tiles of equal
 * width, [gap] apart, its tail at the one at [index]; kept inside the row's width. Laid out at the row's top and of
 * no height, so it moves nothing; it comes in rising in the draw alone. A tap on it calls [onClose].
 */
@Composable
fun SettingBubble(
    title: String,
    text: String,
    index: Int,
    count: Int,
    gap: Dp,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val colors = skin.colors
    val shown = remember(index) { Animatable(0f) }
    LaunchedEffect(index) { shown.animateTo(1f, tween(skin.motion.settle)) }
    val look = skin.parts.tile.surface(AnswerTileState.IDLE, 0)
    val words = skin.parts.tile.content(AnswerTileState.IDLE)
    val rising =
        Modifier.graphicsLayer {
            alpha = shown.value
            translationY = (1f - shown.value) * space.sm.toPx()
        }
    Layout(
        modifier = modifier,
        content = {
            Column(
                rising
                    .raised(look, skin.depth, skin.motion)
                    .clickable(role = Role.Button, onClick = cued(Cue.TAP_SOFT, onClose))
                    .padding(horizontal = space.md, vertical = space.sm),
            ) {
                KvizicText(title, style = KvizicTheme.type.bodyStrong, color = words)
                KvizicText(text, style = KvizicTheme.type.body, color = words)
            }
            Spacer(
                rising.size(space.md, space.xs).drawBehind {
                    val tail =
                        Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2, size.height)
                            close()
                        }
                    drawPath(tail, look.fill)
                    val line = space.strokeThin.toPx()
                    drawLine(colors.outline, Offset.Zero, Offset(size.width / 2, size.height), line)
                    drawLine(colors.outline, Offset(size.width, 0f), Offset(size.width / 2, size.height), line)
                },
            )
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val body = measurables[0].measure(Constraints(maxWidth = (width * BUBBLE_SHARE).roundToInt()))
        val tail = measurables[1].measure(Constraints())
        val cell = (width - gap.roundToPx() * (count - 1)) / count
        val centre = index * (cell + gap.roundToPx()) + cell / 2
        val left = (centre - body.width / 2).coerceIn(0, (width - body.width).coerceAtLeast(0))
        layout(width, 0) {
            tail.place(centre - tail.width / 2, -tail.height)
            body.place(left, -tail.height - body.height + space.strokeThin.roundToPx())
        }
    }
}

private const val DIVIDER = 0.5f

private const val NAME_LEAST = 0.6f

private const val KEY_SHARE = 0.55f

private const val LAMP = 0.18f

private const val BUBBLE_SHARE = 0.8f
