package io.ntole.kvizic.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.ntole.kvizic.design.skin.KvizicTheme

/**
 * The winner of a game, named on a plate of the first step's colour, the podium's own: [caption] over it
 * between two rules, and [name] on it as large as the plate's width lets it be, never cut. The plate pops in
 * once, a little past its size, in its layer alone; coming back to the screen after the app was made again
 * does not pop it again. A heading to a screen reader, its two lines read as one.
 */
@Composable
fun WinnerBanner(
    caption: String,
    name: String,
    modifier: Modifier = Modifier,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val type = KvizicTheme.type
    val part = skin.parts.podium
    var popped by rememberSaveable(name) { mutableStateOf(false) }
    val pop = remember(name) { Animatable(if (popped) 1f else 0f) }
    LaunchedEffect(name) {
        if (!popped) {
            pop.animateTo(1f, tween(skin.motion.stage * POP_STAGES, easing = EaseOutBack))
            popped = true
        }
    }
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { heading() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.sm)) {
            Rule()
            KvizicText(caption, style = type.label, color = skin.colors.onPageAccent, maxLines = 1)
            Rule()
        }
        Spacer(Modifier.height(space.xs))
        Box(
            modifier =
                Modifier
                    .graphicsLayer {
                        // From a smaller plate, past its size on the way in: the easing overshoots 1.
                        val scale = POP_FROM + (1f - POP_FROM) * pop.value
                        scaleX = scale
                        scaleY = scale
                        alpha = (pop.value * POP_FADE).coerceIn(0f, 1f)
                    }.raised(part.surface(FIRST), skin.depth, skin.motion)
                    .padding(horizontal = space.lg, vertical = space.xs),
            contentAlignment = Alignment.Center,
        ) {
            KvizicText(
                name,
                style = type.hero,
                color = part.content(FIRST),
                maxLines = 1,
                autoSize =
                    TextAutoSize.StepBased(
                        minFontSize = type.name.style.fontSize,
                        maxFontSize = type.hero.style.fontSize,
                    ),
            )
        }
    }
}

/** A short line either side of the caption, in the accent. */
@Composable
private fun Rule() {
    val space = KvizicTheme.space
    Box(Modifier.width(space.xl).height(space.strokeThin).background(KvizicTheme.colors.onPageAccent))
}

private const val FIRST = 1

/** How many of a game's stages the plate takes to pop in, so a skin that moves slowly pops slowly. */
private const val POP_STAGES = 2

/** The plate starts at this much of its size, and is fully shown by the time it has come half of the way. */
private const val POP_FROM = 0.6f
private const val POP_FADE = 2f
