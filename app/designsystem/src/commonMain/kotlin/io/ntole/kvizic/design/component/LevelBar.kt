package io.ntole.kvizic.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.ntole.kvizic.design.skin.KvizicTheme

/**
 * How far through its level a player is: a thin line on the page as wide as the [modifier] makes it, [progress]
 * from 0 to 1 of it filled in the accent, over a track of the page's muted colour. It settles to a new [progress] as the skin's motion says,
 * drawn and never composed. [contentDescription] says it to a screen reader.
 */
@Composable
fun LevelBar(
    progress: Float,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val skin = KvizicTheme.skin
    val colors = skin.colors
    val shown = animateFloatAsState(progress.coerceIn(0f, 1f), tween(skin.motion.settle), label = "level")
    Spacer(
        modifier
            .height(skin.space.xs)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ).drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(colors.onPageMuted, cornerRadius = radius, alpha = TRACK_ALPHA)
                val share = shown.value
                if (share > 0f) {
                    // Never narrower than its own height: a sliver of progress is still a dot.
                    val width = (size.width * share).coerceAtLeast(size.height)
                    drawRoundRect(colors.onPageAccent, size = Size(width, size.height), cornerRadius = radius)
                }
            },
    )
}

/** How much of the muted colour the empty track is. */
private const val TRACK_ALPHA = 0.3f
