package io.ntole.kvizic.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.StageIconButton
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.language.LocalStrings

/**
 * A screen's top bar: a button back to the screen before, [onBack], the screen's [title] in the middle, or
 * what [titleContent] draws there, and what else the screen offers at the end, [actions], one round button
 * for the title to stand in the bar's middle.
 */
@Composable
fun BackTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
) {
    val space = KvizicTheme.space
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.sm),
        modifier = modifier.fillMaxWidth().heightIn(min = space.touchTarget).padding(horizontal = space.screen),
    ) {
        StageIconButton(
            KvizicIcons.Back,
            contentDescription = LocalStrings.current.back,
            onClick = tapped("top_bar.back", onClick = onBack),
            small = true,
            cue = Cue.BACK,
        )
        if (titleContent != null) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { titleContent() }
        } else if (title != null) {
            KvizicText(
                title,
                Modifier.weight(1f),
                style = KvizicTheme.type.label,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (actions != null) {
            actions()
        } else {
            // As wide as the back button, so a title stands in the bar's middle.
            Spacer(Modifier.size(space.button.roundSmall, space.xxs))
        }
    }
}
