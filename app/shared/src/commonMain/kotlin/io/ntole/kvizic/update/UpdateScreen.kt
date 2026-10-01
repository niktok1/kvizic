package io.ntole.kvizic.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.language.LocalStrings

/**
 * The one screen the game shows once the server serves this build nothing more: that a new version is
 * available, and [button], this platform's way to it, where it has one. Nothing else, no top bar and no way
 * back: every call would be refused again.
 */
@Composable
fun UpdateScreen(
    button: UpdateButton?,
    modifier: Modifier = Modifier,
) {
    val space = KvizicTheme.space
    val strings = LocalStrings.current.updateScreen

    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize().padding(space.screen)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space.xl),
        ) {
            KvizicText(
                text = strings.newVersion,
                color = KvizicTheme.colors.onPageAccent,
                style = KvizicTheme.type.headline,
                textAlign = TextAlign.Center,
            )
            if (button != null) {
                val (label, element) =
                    when (button.way) {
                        UpdateWay.STORE -> strings.update to "update.store"
                        UpdateWay.RELOAD -> strings.reload to "update.reload"
                    }
                StageButton(label, onClick = tapped(element, onClick = button.go))
            }
        }
    }
}
