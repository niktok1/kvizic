package io.ntole.kvizic.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.TimeMark

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
 *
 * [startedAt] is when the reaction arrived: a burst composed anew, after an Android activity is made again
 * or the screen is come back to, goes on from where it had got to, and one already over stays gone,
 * rather than every member's last reaction bursting again at once.
 */
@Composable
fun ReactionBurst(
    icon: ImageVector,
    burstKey: Any?,
    modifier: Modifier = Modifier,
    startedAt: TimeMark? = null,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val part = skin.parts.burst
    val progress = remember { Animatable(1f) }
    LaunchedEffect(burstKey) {
        if (burstKey == null) return@LaunchedEffect
        val total = skin.motion.burst.coerceAtLeast(1)
        val done =
            startedAt
                ?.elapsedNow()
                ?.inWholeMilliseconds
                ?.let { it.toFloat() / total }
                ?.coerceIn(0f, 1f) ?: 0f
        if (done >= 1f) return@LaunchedEffect
        progress.snapTo(done)
        progress.animateTo(1f, tween(((1f - done) * total).toInt().coerceAtLeast(1), easing = LinearEasing))
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
 * ink on a sticky note in a notebook, smaller where the sign is too narrow to hold it inside its panel
 * (`LogoSizes.wordInset`). A heading to a screen reader.
 *
 * Every few seconds a bulb or two of the sign flickers, as an old marquee's do: dims, catches, dims again
 * and comes back, for under a second. In the draw alone, with no frame between flickers; a skin with no
 * lights draws none of it.
 */
@Composable
fun Wordmark(
    text: String,
    modifier: Modifier = Modifier,
) {
    val skin = KvizicTheme.skin
    val part = skin.parts.logo
    val flicker = remember { SignFlicker() }
    LaunchedEffect(flicker) { flicker.run() }
    Box(
        modifier =
            modifier
                .height(skin.space.logo.signHeight)
                .semantics { heading() }
                .drawBehind { with(part) { drawSign(flicker::glow) } },
        contentAlignment = Alignment.Center,
    ) {
        val logo = KvizicTheme.type.logo
        // On a narrow sign the name shrinks to stay inside its panel, never past the bulbs (Nunito is wide).
        KvizicText(
            text,
            Modifier.padding(horizontal = skin.space.logo.wordInset),
            style = logo,
            color = part.text,
            maxLines = 1,
            autoSize =
                TextAutoSize.StepBased(
                    minFontSize = KvizicTheme.type.answerMin.style.fontSize,
                    maxFontSize = logo.style.fontSize,
                ),
        )
    }
}

private const val NANOS_PER_MILLI = 1_000_000L
private const val POP_END = 0.2f
private const val POP_OVER = 1.15f
private const val RISE = 0.9f
private const val FADE_FROM = 0.55f

/**
 * Which of a sign's bulbs flicker now and how far through their flicker: a new pair every
 * [FLICKER_EVERY_MIN_MS] to [FLICKER_EVERY_MAX_MS], each going through [FLICKER_GLOW] over
 * [FLICKER_MILLIS]. Read in the draw alone.
 */
private class SignFlicker {
    private val progress = Animatable(1f)
    private var round by mutableIntStateOf(0)

    suspend fun run() {
        while (true) {
            delay(Random.nextLong(FLICKER_EVERY_MIN_MS, FLICKER_EVERY_MAX_MS))
            round++
            progress.snapTo(0f)
            progress.animateTo(1f, tween(FLICKER_MILLIS, easing = LinearEasing))
        }
    }

    /** How bright [bulb] of [of] is now: 1 but for this round's pair, mid-flicker. */
    fun glow(
        bulb: Int,
        of: Int,
    ): Float {
        val p = progress.value
        if (p >= 1f || of < 2) return 1f
        val picked = Random(round)
        val first = picked.nextInt(of)
        val second = (first + 1 + picked.nextInt(of - 1)) % of
        if (bulb != first && bulb != second) return 1f
        // The second of the pair a little behind the first, as two loose bulbs would not dim together.
        val at = if (bulb == first) p else (p - SECOND_LAG).coerceAtLeast(0f) / (1f - SECOND_LAG)
        return glowAt(at)
    }

    private fun glowAt(p: Float): Float {
        val steps = FLICKER_GLOW
        for (i in 1 until steps.size) {
            val (fromAt, fromGlow) = steps[i - 1]
            val (toAt, toGlow) = steps[i]
            if (p <= toAt) return fromGlow + (toGlow - fromGlow) * ((p - fromAt) / (toAt - fromAt))
        }
        return 1f
    }
}

private const val FLICKER_EVERY_MIN_MS = 2_500L
private const val FLICKER_EVERY_MAX_MS = 5_500L
private const val FLICKER_MILLIS = 900
private const val SECOND_LAG = 0.15f

/** A loose bulb's flicker, as (how far through, how bright): out, a catch, out again, back. */
private val FLICKER_GLOW =
    listOf(
        0f to 1f,
        0.08f to 0.1f,
        0.2f to 0.15f,
        0.28f to 0.85f,
        0.38f to 0.2f,
        0.5f to 0.25f,
        0.62f to 1f,
        0.74f to 0.55f,
        0.84f to 1f,
        1f to 1f,
    )
