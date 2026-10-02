package io.ntole.kvizic.settings

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.everyText
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.nodes
import io.ntole.kvizic.tap
import io.ntole.kvizic.texts
import io.ntole.kvizic.theme.GameTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Settings screen drawn off screen in each language: the Sound switch and the way to About. */
class AppSettingsScreenDrawTest {
    @Test
    fun `the screen shows the sound switch and the way to About`() {
        Language.entries.forEach { language ->
            val strings = stringsOf(language)
            val scene = scene(language)
            try {
                val shown = scene.everyText()
                assertEquals(listOf(strings.settingsScreen.sound, strings.aboutScreen.title), shown, "$language")
            } finally {
                scene.close()
            }
        }
    }

    /** The Sound switch: on or off as the player left it, a switch to a screen reader, and a tap turns it over. */
    @Test
    fun `the Sound switch shows the player's choice and a tap turns it the other way`() {
        Language.entries.forEach { language ->
            val words = stringsOf(language).settingsScreen
            listOf(true, false).forEach { on ->
                val changes = mutableListOf<Boolean>()
                val scene = scene(language, soundOn = on, onSoundChange = { changes += it })
                try {
                    val switch = scene.nodes().single { words.sound in it.texts }
                    val shown = switch.config.getOrNull(SemanticsProperties.ToggleableState)
                    assertEquals(if (on) ToggleableState.On else ToggleableState.Off, shown, "$language")
                    assertEquals(Role.Switch, switch.config.getOrNull(SemanticsProperties.Role), "$language")

                    scene.tap(words.sound)

                    assertEquals(listOf(!on), changes, "$language")
                } finally {
                    scene.close()
                }
            }
        }
    }

    @Test
    fun `the way to About does what it says`() {
        var opened = 0
        val scene = scene(Language.DEFAULT, onAbout = { opened++ })
        try {
            scene.tap(stringsOf(Language.DEFAULT).aboutScreen.title)
        } finally {
            scene.close()
        }
        assertTrue(opened == 1, "opened $opened times")
    }

    private fun scene(
        language: Language,
        soundOn: Boolean = true,
        onSoundChange: (Boolean) -> Unit = {},
        onAbout: () -> Unit = {},
    ): ImageComposeScene =
        ImageComposeScene(width = SHORT_PHONE_WIDTH, height = SHORT_PHONE_HEIGHT, density = Density(1f)) {
            GameTheme(language) {
                AppSettingsScreen(soundOn = soundOn, onSoundChange = onSoundChange, onAbout = onAbout)
            }
        }.also { it.render() }

    private companion object {
        /** An iPhone SE's size less its status bar and the top bar above the screen. */
        const val SHORT_PHONE_WIDTH = 375
        const val SHORT_PHONE_HEIGHT = 599
    }
}
