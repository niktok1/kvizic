package io.ntole.kvizic.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.RecordingClipboard
import io.ntole.kvizic.about.DeleteAccountButton
import io.ntole.kvizic.about.Deletion
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.descriptions
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.everyText
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.nodes
import io.ntole.kvizic.tap
import io.ntole.kvizic.texts
import io.ntole.kvizic.theme.GameTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Settings screen drawn off screen in each language: the Sound switch, the Statistics switch, the way to
 * About, and the player's account, its id and the deletion last.
 */
class SettingsScreenDrawTest {
    @Test
    fun `the screen shows its switches the way to About and the account in that order`() {
        Language.entries.forEach { language ->
            val strings = stringsOf(language)
            val scene = scene(language)
            try {
                val shown = scene.everyText()
                val order =
                    listOf(
                        strings.settingsScreen.sound,
                        strings.aboutScreen.statistics,
                        strings.aboutScreen.title,
                        strings.settingsScreen.account,
                        strings.aboutScreen.accountId,
                        ACCOUNT_ID,
                    )
                order.forEach { text -> assertTrue(text in shown, "$language: \"$text\" is not in $shown") }
                assertEquals(order, order.sortedBy(shown::indexOf), "$language: $shown")
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

    /** The Statistics switch, as it was on the About screen: its word and the switch are one control. */
    @Test
    fun `the Statistics switch shows the player's choice and a tap turns it the other way`() {
        Language.entries.forEach { language ->
            val about = stringsOf(language).aboutScreen
            listOf(true, false).forEach { on ->
                val changes = mutableListOf<Boolean>()
                val scene = scene(language, statisticsOn = on, onStatisticsChange = { changes += it })
                try {
                    val switch = scene.nodes().single { about.statistics in it.texts }
                    val shown = switch.config.getOrNull(SemanticsProperties.ToggleableState)
                    assertEquals(if (on) ToggleableState.On else ToggleableState.Off, shown, "$language")
                    assertEquals(Role.Switch, switch.config.getOrNull(SemanticsProperties.Role), "$language")

                    scene.tap(about.statistics)

                    assertEquals(listOf(!on), changes, "$language")
                } finally {
                    scene.close()
                }
            }
        }
    }

    /** The button under Statistics opens a dialog of what the switch sends, and its OK closes it. */
    @Test
    fun `the Statistics explanation opens and closes and changes nothing`() {
        val about = stringsOf(Language.DEFAULT).aboutScreen
        val changes = mutableListOf<Boolean>()
        val scene = scene(Language.DEFAULT, onStatisticsChange = { changes += it })
        try {
            assertFalse(about.statisticsInfo in scene.texts(), "explained before it is tapped")
            scene.tap(about.aboutStatistics)
            assertTrue(about.statisticsInfo in scene.texts(), "${scene.texts()}")
            scene.tap(about.ok)
            assertFalse(about.statisticsInfo in scene.texts(), "the dialog is gone")
        } finally {
            scene.close()
        }
        assertEquals(emptyList(), changes, "the switch is left as it was")
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
        assertEquals(1, opened)
    }

    /**
     * The account id is copied whole and the label says so, in every language; nothing else on the screen moves
     * for it. With no session stored on the device there is no account to name, and the screen names none.
     */
    @Test
    fun `the account id is copied whole and with none stored none shows`() {
        Language.entries.forEach { language ->
            val about = stringsOf(language).aboutScreen
            val clipboard = RecordingClipboard()
            val scene = scene(language, clipboard = clipboard)
            try {
                val before =
                    scene
                        .nodes()
                        .single {
                            stringsOf(
                                language,
                            ).settingsScreen.account in it.texts
                        }.boundsInRoot
                assertFalse(about.copied in scene.texts(), "$language: copied before any tap")

                scene.tap(about.copyAccountId)

                assertEquals(listOf(ACCOUNT_ID), clipboard.copied, "$language")
                assertTrue(about.copied in scene.texts(), "$language: ${scene.texts()}")
                val after = scene.nodes().single { stringsOf(language).settingsScreen.account in it.texts }.boundsInRoot
                assertEquals(before, after, "$language")
            } finally {
                scene.close()
            }
        }
        val scene = scene(Language.DEFAULT, accountId = null)
        try {
            val about = stringsOf(Language.DEFAULT).aboutScreen
            assertFalse(about.accountId in scene.everyText(), "${scene.everyText()}")
            assertFalse(about.copyAccountId in scene.descriptions(), "${scene.descriptions()}")
        } finally {
            scene.close()
        }
    }

    /** The deletion is the screen's last thing, and asks first, and says why one failed. */
    @Test
    fun `the deletion comes last asks before it deletes and says why one failed`() {
        Language.entries.forEach { language ->
            val strings = stringsOf(language)
            val words = strings.aboutScreen.deleteAccount
            var deletions = 0
            val scene =
                scene(language, deletion = {
                    DeleteAccountButton(deletion = Deletion.Failed(CoreError.NETWORK), onDelete = { deletions++ })
                })
            try {
                assertTrue(strings.offline in scene.everyText(), "$language: ${scene.everyText()}")
                assertEquals(words.button, scene.everyText().last(), "$language: ${scene.everyText()}")

                scene.tap(words.button)
                assertEquals(0, deletions, "$language: nothing deleted before the confirm")
                assertTrue(words.warning in scene.texts(), "$language: ${scene.texts()}")
                scene.tap(strings.cancel)
                assertFalse(words.warning in scene.texts(), "$language: the dialog is gone")

                scene.tap(words.button)
                scene.tap(words.confirm)
                assertEquals(1, deletions, "$language")
            } finally {
                scene.close()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun scene(
        language: Language,
        accountId: String? = ACCOUNT_ID,
        clipboard: ClipboardManager = RecordingClipboard(),
        soundOn: Boolean = true,
        onSoundChange: (Boolean) -> Unit = {},
        statisticsOn: Boolean = true,
        onStatisticsChange: (Boolean) -> Unit = {},
        onAbout: () -> Unit = {},
        deletion: @Composable () -> Unit = { KvizicText(DELETION) },
    ): ImageComposeScene =
        ImageComposeScene(width = SHORT_PHONE_WIDTH, height = TALL_ENOUGH, density = Density(1f)) {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                GameTheme(language) {
                    SettingsScreen(
                        accountId = accountId,
                        soundOn = soundOn,
                        onSoundChange = onSoundChange,
                        statisticsOn = statisticsOn,
                        onStatisticsChange = onStatisticsChange,
                        onAbout = onAbout,
                        deletion = deletion,
                    )
                }
            }
        }.also { it.render() }

    private companion object {
        /** An iPhone SE's width. */
        const val SHORT_PHONE_WIDTH = 375

        /** Tall enough for the whole screen to stand on it, so every control can be tapped. */
        const val TALL_ENOUGH = 800

        /** A player id as the server makes one, a UUID: the longest an account id is. */
        const val ACCOUNT_ID = "0f8fad5b-d9cb-469f-a165-70867728950e"

        /** What the test puts where the deletion goes. */
        const val DELETION = "the deletion"
    }
}
