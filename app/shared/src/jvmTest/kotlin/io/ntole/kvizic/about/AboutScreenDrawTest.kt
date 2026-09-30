package io.ntole.kvizic.about

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.RecordingClipboard
import io.ntole.kvizic.RecordingUris
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.descriptions
import io.ntole.kvizic.everyText
import io.ntole.kvizic.language.KvizicStrings
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.nodes
import io.ntole.kvizic.tap
import io.ntole.kvizic.texts
import io.ntole.kvizic.theme.ShellTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The About screen drawn off screen in each language: the game's name, its version and build number, 13+,
 * the site's four pages, which open in the browser in the language shown, the account id, the Statistics
 * switch, every library with its licence, and the deletion last. Links open through a handler of the
 * test's own, so nothing here reaches a browser or the site.
 */
class AboutScreenDrawTest {
    @Test
    fun `the screen shows the name the version the age the links and every licence`() {
        Language.entries.forEach { language ->
            val strings = stringsOf(language)
            val about = strings.aboutScreen
            val scene = scene(language)
            try {
                val shown = scene.everyText()
                val expected =
                    listOf(strings.gameName, about.version.fill("0.1.0 (100)"), AGE_RATING) +
                        listOf(about.privacy, about.terms, about.deleteAccountPage, about.contact, about.licences) +
                        listOf(about.accountId, ACCOUNT_ID, about.statistics) +
                        OPEN_SOURCE_LIBRARIES.flatMap { listOfNotNull(it.name, it.licence, it.notice) }
                expected.forEach { text -> assertTrue(text in shown, "$language: \"$text\" is not in $shown") }
            } finally {
                scene.close()
            }
        }
    }

    /**
     * The copy button puts the account id on the clipboard, whole, and the label says it is copied, in every
     * language; nothing else on the screen moves for it.
     */
    @Test
    fun `the account id is copied whole and the label says so`() {
        Language.entries.forEach { language ->
            val about = stringsOf(language).aboutScreen
            val clipboard = RecordingClipboard()
            val scene = scene(language, clipboard = clipboard)
            try {
                val before = scene.nodes().single { about.licences in it.texts }.boundsInRoot
                assertFalse(about.copied in scene.texts(), "$language: copied before any tap")

                scene.tap(about.copyAccountId)

                assertEquals(listOf(ACCOUNT_ID), clipboard.copied, "$language")
                assertTrue(about.copied in scene.texts(), "$language: ${scene.texts()}")
                assertEquals(before, scene.nodes().single { about.licences in it.texts }.boundsInRoot, "$language")
            } finally {
                scene.close()
            }
        }
    }

    /** With no session stored on the device there is no account to name, and the screen names none. */
    @Test
    fun `with no session stored no account id shows`() {
        val about = stringsOf(Language.DEFAULT).aboutScreen
        val scene = scene(Language.DEFAULT, accountId = null)
        try {
            val shown = scene.everyText()
            assertFalse(about.accountId in shown, "$shown")
            assertFalse(about.copyAccountId in scene.descriptions(), "${scene.descriptions()}")
        } finally {
            scene.close()
        }
    }

    /** Each link opens its page, the Serbian one for either script and the English one under /en/. */
    @Test
    fun `each link opens its page on the site in the language shown`() {
        Language.entries.forEach { language ->
            val about = stringsOf(language).aboutScreen
            val uris = RecordingUris()
            val scene = scene(language, uris = uris)
            try {
                listOf(about.privacy, about.terms, about.deleteAccountPage, about.contact).forEach(scene::tap)
            } finally {
                scene.close()
            }
            val pages = listOf(SitePage.PRIVACY, SitePage.TERMS, SitePage.DELETE_ACCOUNT, SitePage.CONTACT)
            assertEquals(pages.map { Site.url(it, language) }, uris.opened, "$language")
        }
    }

    /** A link nothing on the device opens, on a phone with no browser, does nothing: the screen stays. */
    @Test
    fun `a link nothing on the device opens does nothing`() {
        val about = stringsOf(Language.DEFAULT).aboutScreen
        val uris = RecordingUris(opens = false)
        val scene = scene(Language.DEFAULT, uris = uris)
        try {
            listOf(about.privacy, about.terms, about.deleteAccountPage, about.contact).forEach(scene::tap)
            scene.tap(OPEN_SOURCE_LIBRARIES.first().name)

            assertTrue(about.contact in scene.texts(), "${scene.texts()}")
        } finally {
            scene.close()
        }
        assertEquals(5, uris.opened.size, "${uris.opened}")
    }

    /** The links and the account id with its copy button show before any scrolling, on a short phone. */
    @Test
    fun `the links and the account id show before any scrolling`() {
        Language.entries.forEach { language ->
            val about = stringsOf(language).aboutScreen
            val scene = scene(language)
            try {
                val contact = scene.nodes().single { about.contact in it.texts }
                assertTrue(contact.boundsInRoot.bottom <= SHORT_PHONE_HEIGHT, "$language: ${contact.boundsInRoot}")
                val copy = scene.nodes().single { about.copyAccountId in it.descriptions }
                assertTrue(copy.boundsInRoot.bottom <= SHORT_PHONE_HEIGHT, "$language: ${copy.boundsInRoot}")
            } finally {
                scene.close()
            }
        }
    }

    /** Deleting the account is the screen's last thing, after every licence. */
    @Test
    fun `the deletion comes last after every licence`() {
        Language.entries.forEach { language ->
            val scene = scene(language, deletion = { Text(DELETION) })
            try {
                val shown = scene.everyText()
                assertEquals(DELETION, shown.last(), "$language: $shown")
                assertTrue(OPEN_SOURCE_LIBRARIES.last().name in shown, "$language: $shown")
            } finally {
                scene.close()
            }
        }
    }

    /**
     * The Statistics switch: under the account id and before the licences, on or off as the player left it,
     * a switch to a screen reader, its word and all one control, and a tap on it turns it the other way.
     */
    @Test
    fun `the Statistics switch shows the player's choice and a tap turns it the other way`() {
        Language.entries.forEach { language ->
            val about = stringsOf(language).aboutScreen
            listOf(true, false).forEach { on ->
                val changes = mutableListOf<Boolean>()
                val scene = scene(language, statisticsOn = on, onStatisticsChange = { changes += it })
                try {
                    val texts = scene.everyText()
                    val at = texts.indexOf(about.statistics)
                    assertTrue(at > texts.indexOf(about.accountId), "$language: under the account id")
                    assertTrue(at < texts.indexOf(about.licences), "$language: before the licences")
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
        Language.entries.forEach { language ->
            val about = stringsOf(language).aboutScreen
            val changes = mutableListOf<Boolean>()
            val scene = scene(language, onStatisticsChange = { changes += it })
            try {
                assertFalse(about.statisticsInfo in scene.texts(), "$language: explained before it is tapped")
                scene.tap(about.aboutStatistics)
                assertTrue(about.statisticsInfo in scene.texts(), "$language: ${scene.texts()}")
                scene.tap(about.ok)
                assertFalse(about.statisticsInfo in scene.texts(), "$language: the dialog is gone")
            } finally {
                scene.close()
            }
            assertEquals(emptyList(), changes, "$language: the switch is left as it was")
        }
    }

    /** The deletion asks first, deletes only on its confirm, and says why one failed. */
    @Test
    fun `the deletion asks before it deletes and says why one failed`() {
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
        uris: UriHandler = RecordingUris(),
        accountId: String? = ACCOUNT_ID,
        clipboard: ClipboardManager = RecordingClipboard(),
        deletion: @Composable () -> Unit = {},
        statisticsOn: Boolean = true,
        onStatisticsChange: (Boolean) -> Unit = {},
    ): ImageComposeScene =
        ImageComposeScene(width = SHORT_PHONE_WIDTH, height = SHORT_PHONE_HEIGHT, density = Density(1f)) {
            CompositionLocalProvider(LocalUriHandler provides uris, LocalClipboardManager provides clipboard) {
                ShellTheme {
                    KvizicStrings(language) {
                        AboutScreen(
                            AppVersion("0.1.0", 100),
                            accountId = accountId,
                            statisticsOn = statisticsOn,
                            onStatisticsChange = onStatisticsChange,
                            deletion = deletion,
                        )
                    }
                }
            }
        }.also { it.render() }

    private companion object {
        /** An iPhone SE (667 high) less its status bar (20) and the top bar above the screen (48). */
        const val SHORT_PHONE_WIDTH = 375
        const val SHORT_PHONE_HEIGHT = 599

        /** A player id as the server makes one, a UUID: the longest an account id is. */
        const val ACCOUNT_ID = "0f8fad5b-d9cb-469f-a165-70867728950e"

        /** What the test puts where the deletion goes. */
        const val DELETION = "the deletion"
    }
}
