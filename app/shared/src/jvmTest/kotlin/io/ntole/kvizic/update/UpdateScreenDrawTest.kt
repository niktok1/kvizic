package io.ntole.kvizic.update

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.sizeNeeded
import io.ntole.kvizic.tap
import io.ntole.kvizic.texts
import io.ntole.kvizic.theme.GameTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The screen shown once the server serves this build nothing more, drawn off screen in each language: that
 * a new version is available, and the platform's one button, or none.
 */
class UpdateScreenDrawTest {
    @Test
    fun `the screen says a new version is available and its button leads to it`() {
        Language.entries.forEach { language ->
            val strings = stringsOf(language).updateScreen
            mapOf(UpdateWay.STORE to strings.update, UpdateWay.RELOAD to strings.reload).forEach { (way, label) ->
                var went = 0
                val scene = scene(UpdateButton(way) { went++ }, language)
                try {
                    assertEquals(listOf(strings.newVersion, label), scene.texts(), "$language, $way")
                    scene.tap(label)
                } finally {
                    scene.close()
                }
                assertEquals(1, went, "$language, $way")
            }
        }
    }

    /** On the desktop and iOS there is no store to open yet: the screen says so much and no more. */
    @Test
    fun `a platform with no way to the new version shows no button`() {
        Language.entries.forEach { language ->
            val scene = scene(button = null, language = language)
            try {
                assertEquals(listOf(stringsOf(language).updateScreen.newVersion), scene.texts(), "$language")
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun `the screen fits a short phone in every language`() {
        Language.entries.forEach { language ->
            val (_, height) =
                sizeNeeded(SHORT_PHONE_WIDTH, SHORT_PHONE_HEIGHT) {
                    GameTheme(language) { UpdateScreen(UpdateButton(UpdateWay.STORE) {}) }
                }
            assertTrue(height <= SHORT_PHONE_HEIGHT, "$language needs $height of $SHORT_PHONE_HEIGHT")
        }
    }

    private fun scene(
        button: UpdateButton?,
        language: Language,
    ): ImageComposeScene =
        ImageComposeScene(width = SHORT_PHONE_WIDTH, height = SHORT_PHONE_HEIGHT, density = Density(1f)) {
            GameTheme(language) { UpdateScreen(button) }
        }.also { it.render() }

    private companion object {
        /** An iPhone SE (667 high) less its status bar (20) and 48 more, as every screen is held. */
        const val SHORT_PHONE_WIDTH = 375
        const val SHORT_PHONE_HEIGHT = 599
    }
}
