package io.ntole.kvizic.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.lerp
import io.ntole.kvizic.design.skin.KvizicTheme

/**
 * [target], and once it changes, the colour on its way there from the one shown, over the skin's `settle`
 * as a raised surface's own colours go ([raised]): what stands on a tile or a button turns with its face,
 * never ahead of it, as light words would on a face still light. Read in the draw alone.
 */
@Composable
internal fun rememberSettlingColor(target: Color): ColorProducer {
    val settle = KvizicTheme.skin.motion.settle
    val colour = remember { SettlingColor(target) }
    LaunchedEffect(target, settle) { colour.settleTo(target, settle) }
    return colour
}

/**
 * A colour settling from the one it showed to the last it was given, a [ColorProducer] whose every frame
 * is read in the draw: plain fields but for the fraction, which is state, so a frame composes nothing.
 */
internal class SettlingColor(
    initial: Color,
) : ColorProducer {
    private var from = initial
    private var to = initial
    private val mix = Animatable(1f)

    override fun invoke(): Color = if (mix.value >= 1f) to else lerp(from, to, mix.value)

    /** Settles on [target] over [millis], from whatever shows now, so a change cut short goes on from there. */
    suspend fun settleTo(
        target: Color,
        millis: Int,
    ) {
        if (target != to) {
            from = invoke()
            to = target
            mix.snapTo(0f)
        }
        mix.animateTo(1f, tween(millis.coerceAtLeast(1), easing = FastOutSlowInEasing))
    }
}
