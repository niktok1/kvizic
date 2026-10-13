package io.ntole.kvizic.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.analytics.LocalAnalytics
import io.ntole.kvizic.analytics.RecordingAnalytics
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.core.domain.region.Country
import io.ntole.kvizic.cutTexts
import io.ntole.kvizic.descriptions
import io.ntole.kvizic.design.skin.Skin
import io.ntole.kvizic.design.skin.Skins
import io.ntole.kvizic.everyText
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.nodes
import io.ntole.kvizic.renderSettled
import io.ntole.kvizic.tap
import io.ntole.kvizic.texts
import io.ntole.kvizic.theme.GameTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Settings screen drawn off screen in each language: the Sound switch, the languages, the countries and the
 * way to About. The languages are those offered, and the six the release offers where it matters what fits.
 */
class AppSettingsScreenDrawTest {
    @Test
    fun `the screen shows the sound switch, the languages, the countries and the way to About`() {
        Language.entries.forEach { language ->
            val strings = stringsOf(language)
            val countries = strings.settingsScreen.countries
            val scene = scene(language)
            try {
                val shown = scene.everyText()
                assertEquals(
                    listOf(strings.settingsScreen.sound) + Language.OFFERED.map { it.ownName } +
                        listOf(
                            countries.serbia,
                            countries.croatia,
                            countries.bosnia,
                            countries.slovenia,
                            countries.macedonia,
                            countries.montenegro,
                        ) +
                        strings.aboutScreen.title,
                    shown,
                    "$language",
                )
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

    /** Each language is named in itself whatever the screen is in, the one shown is picked, and a tap picks another. */
    @Test
    fun `a language tapped is the one the game is shown in`() {
        listOf(Language.OFFERED, RELEASE).forEach { offered ->
            Language.entries.forEach { shownIn ->
                val picked = mutableListOf<Language>()
                val scene = scene(shownIn, onLanguageChange = { picked += it }, offered = offered)
                try {
                    offered.forEach { language ->
                        val chip = scene.nodes().single { language.ownName in it.texts }
                        assertEquals(
                            language == shownIn,
                            chip.config.getOrNull(SemanticsProperties.Selected) == true,
                            "$shownIn: ${language.ownName}",
                        )
                        scene.tap(language.ownName)
                    }
                } finally {
                    scene.close()
                }
                assertEquals(offered, picked, "$shownIn")
            }
        }
    }

    /**
     * Both Serbian chips say Serbian, in their own script; a screen reader hears which script each is, and every
     * other language as its name.
     */
    @Test
    fun `a screen reader tells Serbian's two chips apart by their script`() {
        val scene = scene(Language.CROATIAN, offered = RELEASE)
        try {
            val said = RELEASE.associateWith { language -> scene.nodes().single { language.ownName in it.texts } }
            assertEquals(listOf("Српски, ћирилица"), said.getValue(Language.SERBIAN_CYRILLIC).descriptions)
            assertEquals(listOf("Srpski, latinica"), said.getValue(Language.SERBIAN_LATIN).descriptions)
            (RELEASE - Language.SERBIAN_CYRILLIC - Language.SERBIAN_LATIN).forEach { language ->
                assertEquals(emptyList(), said.getValue(language).descriptions, "${language.ownName}")
            }
        } finally {
            scene.close()
        }
    }

    /** Each country is named in the screen's language, the one played from is picked, and a tap picks another. */
    @Test
    fun `a country tapped is the one played from`() {
        Language.entries.forEach { language ->
            val names = stringsOf(language).settingsScreen.countries
            PLAYABLE.forEach { from ->
                val picked = mutableListOf<Country>()
                val scene = scene(language, country = from, onCountryChange = { picked += it })
                try {
                    PLAYABLE.forEach { country ->
                        val name = checkNotNull(names.of(country))
                        val chip = scene.nodes().single { name in it.texts }
                        assertEquals(
                            country == from,
                            chip.config.getOrNull(SemanticsProperties.Selected) == true,
                            "$language from $from: $name",
                        )
                        scene.tap(name)
                    }
                } finally {
                    scene.close()
                }
                assertEquals(PLAYABLE, picked, "$language from $from")
            }
        }
    }

    /**
     * The country chips have no heading, as the language chips have none: a screen reader hears what they are for
     * from the row they stand in, which is no button itself.
     */
    @Test
    fun `a screen reader hears what the country chips are for`() {
        Language.entries.forEach { language ->
            val words = stringsOf(language).settingsScreen
            val scene = scene(language)
            try {
                val nodes = scene.nodes()
                val row = nodes.single { words.countrySaid in it.descriptions }
                assertNull(row.config.getOrNull(SemanticsActions.OnClick), "$language: the row is a button")
                PLAYABLE.forEach { country ->
                    val chip = nodes.single { checkNotNull(words.countries.of(country)) in it.texts }.boundsInRoot
                    assertTrue(row.boundsInRoot.contains(chip.center), "$language: $country stands outside its row")
                }
            } finally {
                scene.close()
            }
        }
    }

    /** A country's chip is reported by the country's name, never by the words it shows. */
    @Test
    fun `a country tapped is reported by its name`() {
        val analytics = RecordingAnalytics()
        val names = stringsOf(Language.SLOVENIAN).settingsScreen.countries
        val scene =
            ImageComposeScene(width = SMALL_PHONE_WIDTH, height = SMALL_PHONE_HEIGHT, density = Density(1f)) {
                CompositionLocalProvider(LocalAnalytics provides analytics) {
                    GameTheme(Language.SLOVENIAN) { AppSettingsScreen(language = Language.SLOVENIAN) }
                }
            }.also { it.renderSettled() }
        try {
            scene.tap(checkNotNull(names.of(Country.BA)))
        } finally {
            scene.close()
        }
        assertEquals(
            listOf(mapOf(AnalyticsProperty.ELEMENT to "settings.country", AnalyticsProperty.COUNTRY to "BA")),
            analytics.named(AnalyticsEvent.TAP).map { it.properties },
        )
    }

    /**
     * The six languages the release offers and the six countries, whole, on a small phone in every skin, at the
     * usual font and at a phone's large one (130%): a chip too long for its row would be cut, never wrapped. With
     * `KVIZIC_DESIGN_DIR` set, a still of each is written there.
     */
    @Test
    fun `six languages and six countries fit on a small phone at a large font`() {
        Language.entries.forEach { language ->
            Skins.ALL.forEach { skin ->
                listOf(1f, LARGE_FONT).forEach { fontScale ->
                    val where = "$language, ${skin.id} at $fontScale"
                    val scene =
                        scene(
                            language,
                            country = Country.BA,
                            offered = RELEASE,
                            skin = skin,
                            fontScale = fontScale,
                            width = SMALL_PHONE_WIDTH,
                            height = SMALL_PHONE_HEIGHT,
                        )
                    try {
                        write("settings-${skin.id}-$fontScale-${language.tag}") { scene.renderSettled() }
                        assertEquals(emptyList(), scene.cutTexts(), "$where: cut")
                        val words = stringsOf(language).settingsScreen
                        val chips =
                            RELEASE.map { it.ownName } + PLAYABLE.map { checkNotNull(words.countries.of(it)) }
                        chips.forEach { text ->
                            val chip = scene.nodes().single { text in it.texts }.boundsInRoot
                            assertTrue(chip.left >= 0f && chip.right <= SMALL_PHONE_WIDTH, "$where: $text runs off")
                        }
                    } finally {
                        scene.close()
                    }
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

    /**
     * With `KVIZIC_DESIGN_DIR` set, a still of the screen is written there as a PNG to look at; [image] is drawn
     * only then.
     */
    private fun write(
        name: String,
        image: () -> Image,
    ) {
        val directory = System.getenv("KVIZIC_DESIGN_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        directory.mkdirs()
        File(directory, "$name.png").writeBytes(checkNotNull(image().encodeToData()).bytes)
    }

    private fun scene(
        language: Language,
        soundOn: Boolean = true,
        onSoundChange: (Boolean) -> Unit = {},
        onLanguageChange: (Language) -> Unit = {},
        country: Country = Country.RS,
        onCountryChange: (Country) -> Unit = {},
        onAbout: () -> Unit = {},
        offered: List<Language> = Language.OFFERED,
        skin: Skin = Skins.Default,
        fontScale: Float = 1f,
        width: Int = SHORT_PHONE_WIDTH,
        height: Int = SHORT_PHONE_HEIGHT,
    ): ImageComposeScene =
        ImageComposeScene(width = width, height = height, density = Density(1f, fontScale)) {
            GameTheme(language, skin) {
                AppSettingsScreen(
                    soundOn = soundOn,
                    onSoundChange = onSoundChange,
                    language = language,
                    onLanguageChange = onLanguageChange,
                    country = country,
                    onCountryChange = onCountryChange,
                    onAbout = onAbout,
                    offered = offered,
                )
            }
        }.also { it.render() }

    private companion object {
        /** An iPhone SE's size less its status bar and the top bar above the screen. */
        const val SHORT_PHONE_WIDTH = 375
        const val SHORT_PHONE_HEIGHT = 599

        /** A small phone, 360 by 640, less its status bar and the top bar. */
        const val SMALL_PHONE_WIDTH = 360
        const val SMALL_PHONE_HEIGHT = 560

        /** A phone's large font, 130%, a common setting. */
        const val LARGE_FONT = 1.3f

        /** The six languages the release offers (`Language.OFFERED` once the four are reviewed). */
        val RELEASE =
            listOf(
                Language.SERBIAN_CYRILLIC,
                Language.SERBIAN_LATIN,
                Language.CROATIAN,
                Language.BOSNIAN,
                Language.SLOVENIAN,
                Language.MACEDONIAN,
            )

        /** The six countries, in the screen's order. */
        val PLAYABLE = listOf(Country.RS, Country.HR, Country.BA, Country.SI, Country.MK, Country.ME)
    }
}
