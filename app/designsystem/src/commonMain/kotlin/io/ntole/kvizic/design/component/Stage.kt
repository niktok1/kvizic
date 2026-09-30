package io.ntole.kvizic.design.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.LocalContentColor

/**
 * A screen's page: the skin's backdrop drawn under [content], and the page's text colour given to it.
 * The backdrop is a layer of its own, recorded once and drawn again from that record whatever moves
 * over it, until its size or the skin changes. It says nothing to a screen reader.
 */
@Composable
fun Stage(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val skin = KvizicTheme.skin
    val backdrop = skin.backdrop
    Box(modifier) {
        Spacer(Modifier.matchParentSize().graphicsLayer().drawBehind { with(backdrop) { draw() } })
        CompositionLocalProvider(LocalContentColor provides skin.colors.onPage) { content() }
    }
}
