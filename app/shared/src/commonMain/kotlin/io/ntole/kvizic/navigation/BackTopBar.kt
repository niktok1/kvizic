package io.ntole.kvizic.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.theme.Shell

/**
 * The shell's top bar: one button back to the screen before, [onBack]. Plain words rather than an arrow
 * icon, since the shell draws no icons of its own; the design system's bar replaces it.
 */
@Composable
fun BackTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = Shell.dimens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().height(dimens.topBarHeight).padding(horizontal = dimens.spaceXs),
    ) {
        TextButton(onClick = tapped("top_bar.back", onClick = onBack)) {
            Text(LocalStrings.current.back)
        }
    }
}
