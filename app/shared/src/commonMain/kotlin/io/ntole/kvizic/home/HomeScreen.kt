package io.ntole.kvizic.home

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
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.failureText
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.loading.LoadingSpinner

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
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val strings = LocalStrings.current

    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize().padding(space.screen)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space.lg),
        ) {
            KvizicText(text = strings.gameName, color = colors.onPageAccent, style = type.headline)
            val profile = state.profile
            val failure = state.failure
            when {
                profile != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        KvizicText(text = profile.displayName, style = type.name)
                        KvizicText(
                            text = strings.homeScreen.avatar.fill(profile.avatarId),
                            color = colors.onPageMuted,
                            style = type.caption,
                        )
                    }
                }

                failure != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(space.sm),
                    ) {
                        KvizicText(
                            text = strings.failureText(failure.error, failure.retryAfter),
                            color = colors.loss,
                            textAlign = TextAlign.Center,
                        )
                        StageButton(
                            strings.tryAgain,
                            onClick = tapped("home.try_again", onClick = onRetry),
                            kind = ButtonKind.SECONDARY,
                        )
                    }
                }

                else -> {
                    LoadingSpinner(name = strings.loading)
                }
            }
            StageButton(
                strings.aboutScreen.title,
                onClick = tapped("home.about", onClick = onAbout),
                kind = ButtonKind.DARK,
            )
        }
    }
}
