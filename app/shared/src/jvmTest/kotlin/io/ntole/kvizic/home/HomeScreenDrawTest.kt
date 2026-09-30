package io.ntole.kvizic.home

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.player.NameSource
import io.ntole.kvizic.core.domain.player.PlayerStats
import io.ntole.kvizic.core.domain.player.Profile
import io.ntole.kvizic.descriptions
import io.ntole.kvizic.language.KvizicStrings
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.tap
import io.ntole.kvizic.texts
import io.ntole.kvizic.theme.ShellTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The placeholder Home drawn off screen in each language: the name, the player, a spinner or a failure. */
class HomeScreenDrawTest {
    @Test
    fun `a profile read shows the player's name and avatar and the way to About`() {
        Language.entries.forEach { language ->
            val strings = stringsOf(language)
            var abouts = 0
            val scene = scene(HomeState(profile = PROFILE), language, onAbout = { abouts++ })
            try {
                assertEquals(
                    listOf(strings.gameName, PROFILE.displayName, strings.homeScreen.avatar.fill("hedgehog")) +
                        strings.aboutScreen.title,
                    scene.texts(),
                    "$language",
                )
                scene.tap(strings.aboutScreen.title)
            } finally {
                scene.close()
            }
            assertEquals(1, abouts, "$language")
        }
    }

    @Test
    fun `no profile yet is a spinner a screen reader can name`() {
        Language.entries.forEach { language ->
            val strings = stringsOf(language)
            val scene = scene(HomeState(loading = true), language)
            try {
                assertTrue(strings.loading in scene.descriptions(), "$language: ${scene.descriptions()}")
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun `a failed read says why and offers Try again`() {
        Language.entries.forEach { language ->
            val strings = stringsOf(language)
            var retries = 0
            val scene = scene(HomeState(failure = HomeFailure(CoreError.NETWORK)), language, onRetry = { retries++ })
            try {
                assertTrue(strings.offline in scene.texts(), "$language: ${scene.texts()}")
                scene.tap(strings.tryAgain)
            } finally {
                scene.close()
            }
            assertEquals(1, retries, "$language")
        }
    }

    private fun scene(
        state: HomeState,
        language: Language,
        onAbout: () -> Unit = {},
        onRetry: () -> Unit = {},
    ): ImageComposeScene =
        ImageComposeScene(width = SHORT_PHONE_WIDTH, height = SHORT_PHONE_HEIGHT, density = Density(1f)) {
            ShellTheme { KvizicStrings(language) { HomeScreen(state, onAbout = onAbout, onRetry = onRetry) } }
        }.also { it.render() }

    private companion object {
        const val SHORT_PHONE_WIDTH = 375
        const val SHORT_PHONE_HEIGHT = 599

        val PROFILE =
            Profile(
                playerId = "p1",
                displayName = "Брзи Јеж",
                nameSource = NameSource.GENERATED,
                avatarId = "hedgehog",
                playGamesLinked = false,
                stats = PlayerStats(),
            )
    }
}
