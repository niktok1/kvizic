package io.ntole.kvizic.about

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.RecordingUris
import io.ntole.kvizic.everyText
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.nodes
import io.ntole.kvizic.tap
import io.ntole.kvizic.texts
import io.ntole.kvizic.theme.GameTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The About screen drawn off screen in each language: the game's name, its version and build number, 13+,
 * the site's four pages, which open in the browser in the language shown, and every library with its
 * licence. Links open through a handler of the test's own, so nothing here reaches a browser or the site.
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
                        OPEN_SOURCE_LIBRARIES.flatMap { listOfNotNull(it.name, it.licence, it.notice) }
                expected.forEach { text -> assertTrue(text in shown, "$language: \"$text\" is not in $shown") }
            } finally {
                scene.close()
            }
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

    /** The links show before any scrolling, on a short phone. */
    @Test
    fun `the links show before any scrolling`() {
        Language.entries.forEach { language ->
            val about = stringsOf(language).aboutScreen
            val scene = scene(language)
            try {
                val contact = scene.nodes().single { about.contact in it.texts }
                assertTrue(contact.boundsInRoot.bottom <= SHORT_PHONE_HEIGHT, "$language: ${contact.boundsInRoot}")
            } finally {
                scene.close()
            }
        }
    }

    private fun scene(
        language: Language,
        uris: UriHandler = RecordingUris(),
    ): ImageComposeScene =
        ImageComposeScene(width = SHORT_PHONE_WIDTH, height = SHORT_PHONE_HEIGHT, density = Density(1f)) {
            CompositionLocalProvider(LocalUriHandler provides uris) {
                GameTheme(language) { AboutScreen(AppVersion("0.1.0", 100)) }
            }
        }.also { it.render() }

    private companion object {
        /** An iPhone SE (667 high) less its status bar (20) and the top bar above the screen (48). */
        const val SHORT_PHONE_WIDTH = 375
        const val SHORT_PHONE_HEIGHT = 599
    }
}
