package io.ntole.kvizic.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.failureText
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.loading.LoadingSpinner
import io.ntole.kvizic.theme.Shell

/**
 * A placeholder Home, until the game's is built: the game's name, the player's name and avatar id as the
 * server knows them, or a spinner while none is read, or why the read failed with Try again ([onRetry]),
 * and a button to the About screen ([onAbout]). No game yet.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    onAbout: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = Shell.dimens
    val colors = MaterialTheme.colorScheme
    val strings = LocalStrings.current

    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize().padding(dimens.screenPadding)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.spaceLg),
        ) {
            Text(
                text = strings.gameName,
                color = colors.primary,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
            )
            val profile = state.profile
            val failure = state.failure
            when {
                profile != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = profile.displayName,
                            color = colors.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = strings.homeScreen.avatar.fill(profile.avatarId),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                failure != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
                    ) {
                        Text(
                            text = strings.failureText(failure.error, failure.retryAfter),
                            color = colors.error,
                            textAlign = TextAlign.Center,
                        )
                        OutlinedButton(onClick = tapped("home.try_again", onClick = onRetry)) {
                            Text(strings.tryAgain)
                        }
                    }
                }

                else -> {
                    LoadingSpinner(name = strings.loading)
                }
            }
            Button(onClick = tapped("home.about", onClick = onAbout)) {
                Text(strings.aboutScreen.title)
            }
        }
    }
}
