package io.ntole.kvizic.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.ntole.kvizic.design.skin.KvizicTheme
import kotlin.math.PI
import kotlin.math.sin

/**
 * Something is on its way: the skin's spinner, turning on the frame clock for as long as it is shown,
 * in the draw alone. [contentDescription] says what is awaited.
 */
@Composable
fun Spinner(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val skin = KvizicTheme.skin
    val part = skin.parts.spinner
    val turnMillis = skin.motion.spinnerTurn.coerceAtLeast(1)
    val elapsed = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var start = -1L
        while (true) {
            withFrameNanos { now ->
                if (start < 0) start = now
                elapsed.longValue = (now - start) / NANOS_PER_MILLI
            }
        }
    }
    val described =
        if (contentDescription !=
            null
        ) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        }
    Spacer(
        modifier =
            modifier
                .size(skin.space.spinner)
                .then(described)
                .graphicsLayer()
                .drawBehind {
                    val turn = (elapsed.longValue % turnMillis).toFloat() / turnMillis
                    with(part) { drawSpinner(turn) }
                },
    )
}

/**
 * A reaction sent: [icon] bursts out of its place, rises and fades, once for each new [burstKey], and
 * shows nothing between bursts. All of it in its layer and draw alone. A placeholder for the reactions
 * to come; a screen reader is told of a reaction by the screen, not by this.
 */
@Composable
fun ReactionBurst(
    icon: ImageVector,
    burstKey: Any?,
    modifier: Modifier = Modifier,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val part = skin.parts.burst
    val progress = remember { Animatable(1f) }
    LaunchedEffect(burstKey) {
        if (burstKey == null) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(skin.motion.burst.coerceAtLeast(1), easing = LinearEasing))
    }
    val rise = space.burst
    Box(
        modifier =
            modifier
                .size(space.burst)
                .graphicsLayer {
                    val p = progress.value
                    // A pop past its size in the first fifth, then a slow rise as it fades.
                    val pop =
                        if (p < POP_END) {
                            sin(p / POP_END * PI.toFloat() / 2) * POP_OVER
                        } else {
                            POP_OVER - (POP_OVER - 1f) * ((p - POP_END) / (1f - POP_END))
                        }
                    scaleX = pop
                    scaleY = pop
                    translationY = -rise.toPx() * RISE * p
                    alpha = if (p >= 1f) 0f else (1f - ((p - FADE_FROM) / (1f - FADE_FROM)).coerceIn(0f, 1f))
                }.drawBehind { with(part) { drawBurst(progress.value) } },
        contentAlignment = Alignment.Center,
    ) {
        KvizicIcon(icon, contentDescription = null, tint = part.icon, size = space.icon.large)
    }
}

/**
 * The game's name on the skin's sign, set in its logo style: marquee lights round it on the stage,
 * ink on a sticky note in a notebook. A heading to a screen reader.
 */
@Composable
fun Wordmark(
    text: String,
    modifier: Modifier = Modifier,
) {
    val skin = KvizicTheme.skin
    val part = skin.parts.logo
    Box(
        modifier =
            modifier
                .height(skin.space.logo.signHeight)
                .semantics { heading() }
                .drawBehind { with(part) { drawSign() } },
        contentAlignment = Alignment.Center,
    ) {
        KvizicText(text, style = KvizicTheme.type.logo, color = part.text, maxLines = 1)
    }
}

private const val NANOS_PER_MILLI = 1_000_000L
private const val POP_END = 0.2f
private const val POP_OVER = 1.15f
private const val RISE = 0.9f
private const val FADE_FROM = 0.55f
