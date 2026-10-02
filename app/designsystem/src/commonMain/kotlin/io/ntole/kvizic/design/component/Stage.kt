package io.ntole.kvizic.design.component

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.LocalContentColor

/**
 * Whether the window the game is drawn in is wide: on its side, or at least the skin's `wideWindow` across, as
 * a tablet's or a desktop's. Four answers stand in a grid of two by two on a wide window and in a column on a
 * phone held upright, every question alike ([AnswerGrid]). [Stage] says it to everything on it.
 */
val LocalWideWindow = staticCompositionLocalOf { false }

/**
 * A screen's page: the skin's backdrop drawn under [content], and the page's text colour given to it, and
 * whether its window is wide ([LocalWideWindow]). The backdrop is a layer of its own, recorded once and drawn
 * again from that record whatever moves over it, until its size or the skin changes. It says nothing to a
 * screen reader.
 */
@Composable
fun Stage(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val skin = KvizicTheme.skin
    val backdrop = skin.backdrop
    BoxWithConstraints(modifier) {
        val wide = maxWidth > maxHeight || maxWidth >= skin.space.wideWindow
        Spacer(Modifier.matchParentSize().graphicsLayer().drawBehind { with(backdrop) { draw() } })
        CompositionLocalProvider(LocalContentColor provides skin.colors.onPage, LocalWideWindow provides wide) {
            content()
        }
    }
}
