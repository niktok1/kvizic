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
 * The Settings screen drawn off screen in each language: the Sound switch, the region (a globe and the six
 * flags, with Ћирилица and Latinica where the country reads both) and the way to About.
 */
class AppSettingsScreenDrawTest {
    @Test
    fun `the screen shows the sound switch, the flags, Serbia's two scripts and the way to About`() {
        Language.entries.forEach { language ->
            val strings = stringsOf(language)
            val scene = scene(language)
            try {
                assertEquals(
                    listOf(strings.settingsScreen.sound, CYRILLIC, LATIN, strings.aboutScreen.title),
                    scene.everyText(),
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

    /** A flag shows no word: a screen reader hears its country, named in the screen's language, and the one picked. */
    @Test
    fun `a flag is heard as its country and the one played from is picked`() {
        Language.entries.forEach { language ->
            val names = stringsOf(language).settingsScreen.countries
            PLAYABLE.forEach { from ->
                val scene = scene(language, country = from)
                try {
                    PLAYABLE.forEach { country ->
                        val flag = scene.nodes().single { checkNotNull(names.of(country)) in it.descriptions }
                        assertEquals(emptyList(), flag.texts, "$language: $country")
                        assertEquals(
                            country == from,
                            flag.config.getOrNull(SemanticsProperties.Selected) == true,
                            "$language from $from: $country",
                        )
                    }
                } finally {
                    scene.close()
                }
            }
        }
    }

    /**
     * A flag picks its country and the language with it: the country's own, or the one of its two in the script
     * the game is in now, so a player in Latin stays in Latin.
     */
    @Test
    fun `a flag tapped picks its country and the language it reads`() {
        val lands =
            mapOf(
                Language.SERBIAN_CYRILLIC to
                    listOf(
                        Language.SERBIAN_CYRILLIC,
                        Language.CROATIAN,
                        Language.SERBIAN_CYRILLIC,
                        Language.SLOVENIAN,
                        Language.MACEDONIAN,
                        Language.SERBIAN_CYRILLIC,
                    ),
                Language.SERBIAN_LATIN to
                    listOf(
                        Language.SERBIAN_LATIN,
                        Language.CROATIAN,
                        Language.BOSNIAN,
                        Language.SLOVENIAN,
                        Language.MACEDONIAN,
                        Language.SERBIAN_LATIN,
                    ),
                Language.CROATIAN to
                    listOf(
                        Language.SERBIAN_LATIN,
                        Language.CROATIAN,
                        Language.BOSNIAN,
                        Language.SLOVENIAN,
                        Language.MACEDONIAN,
                        Language.SERBIAN_LATIN,
                    ),
                Language.MACEDONIAN to
                    listOf(
                        Language.SERBIAN_CYRILLIC,
                        Language.CROATIAN,
                        Language.SERBIAN_CYRILLIC,
                        Language.SLOVENIAN,
                        Language.MACEDONIAN,
                        Language.SERBIAN_CYRILLIC,
                    ),
            )
        lands.forEach { (from, expected) ->
            val names = stringsOf(from).settingsScreen.countries
            val countries = mutableListOf<Country>()
            val languages = mutableListOf<Language>()
            val scene =
                scene(
                    from,
                    country = Country.UNKNOWN,
                    onCountryChange = { countries += it },
                    onLanguageChange = { languages += it },
                )
            try {
                PLAYABLE.forEach { country -> scene.tap(checkNotNull(names.of(country))) }
            } finally {
                scene.close()
            }
            assertEquals(PLAYABLE, countries, "from $from")
            assertEquals(expected, languages, "from $from")
        }
    }

    /** Ћирилица and Latinica stand only under a country that reads both, Serbia, Montenegro and Bosnia. */
    @Test
    fun `the scripts stand only where the country reads two`() {
        PLAYABLE.forEach { country ->
            val language = if (country == Country.HR) Language.CROATIAN else Language.SERBIAN_CYRILLIC
            val scene = scene(language, country = country)
            try {
                val shown = scene.everyText()
                val two = country in listOf(Country.RS, Country.ME, Country.BA)
                assertEquals(two, CYRILLIC in shown && LATIN in shown, "$country")
                assertEquals(two, CYRILLIC in shown || LATIN in shown, "$country")
            } finally {
                scene.close()
            }
        }
    }

    /**
     * A script tapped is the language the game is shown in: Serbian in that script in Serbia and Montenegro,
     * Bosnian in Latin and Serbian in Cyrillic in Bosnia; the one shown is picked.
     */
    @Test
    fun `a script tapped is the language the game is shown in`() {
        mapOf(
            Country.RS to (Language.SERBIAN_CYRILLIC to Language.SERBIAN_LATIN),
            Country.ME to (Language.SERBIAN_CYRILLIC to Language.SERBIAN_LATIN),
            Country.BA to (Language.SERBIAN_CYRILLIC to Language.BOSNIAN),
        ).forEach { (country, scripts) ->
            val (cyrillic, latin) = scripts
            listOf(cyrillic, latin).forEach { shownIn ->
                val picked = mutableListOf<Language>()
                val scene = scene(shownIn, country = country, onLanguageChange = { picked += it })
                try {
                    listOf(CYRILLIC to cyrillic, LATIN to latin).forEach { (name, language) ->
                        val chip = scene.nodes().single { name in it.texts }
                        assertEquals(
                            language == shownIn,
                            chip.config.getOrNull(SemanticsProperties.Selected) == true,
                            "$country in $shownIn: $name",
                        )
                    }
                    scene.tap(CYRILLIC)
                    scene.tap(LATIN)
                } finally {
                    scene.close()
                }
                assertEquals(listOf(cyrillic, latin), picked, "$country in $shownIn")
            }
        }
    }

    /** A script alone does not say whether Bosnia's Latin is Bosnian: a screen reader hears the language with it. */
    @Test
    fun `a screen reader hears each script with its language`() {
        mapOf(
            Country.RS to listOf("Српски, ћирилица", "Srpski, latinica"),
            Country.BA to listOf("Српски, ћирилица", "Bosanski, latinica"),
        ).forEach { (country, said) ->
            val scene = scene(Language.SERBIAN_CYRILLIC, country = country)
            try {
                val chips = listOf(CYRILLIC, LATIN).map { name -> scene.nodes().single { name in it.texts } }
                assertEquals(said, chips.map { it.descriptions.single() }, "$country")
            } finally {
                scene.close()
            }
        }
    }

    /**
     * The flags have no heading: a screen reader hears what they are for from the row they stand in, beside the
     * globe, which is no button itself.
     */
    @Test
    fun `a screen reader hears what the flags are for`() {
        Language.entries.forEach { language ->
            val words = stringsOf(language).settingsScreen
            val scene = scene(language)
            try {
                val nodes = scene.nodes()
                val row = nodes.single { words.countrySaid in it.descriptions }
                assertNull(row.config.getOrNull(SemanticsActions.OnClick), "$language: the row is a button")
                PLAYABLE.forEach { country ->
                    val flag =
                        nodes
                            .single {
                                checkNotNull(
                                    words.countries.of(country),
                                ) in it.descriptions
                            }.boundsInRoot
                    assertTrue(row.boundsInRoot.contains(flag.center), "$language: $country stands outside its row")
                }
            } finally {
                scene.close()
            }
        }
    }

    /** A flag is reported by its country's name, never by the words a screen reader hears. */
    @Test
    fun `a country tapped is reported by its name`() {
        val analytics = RecordingAnalytics()
        val names = stringsOf(Language.SLOVENIAN).settingsScreen.countries
        val scene =
            ImageComposeScene(width = SMALL_PHONE_WIDTH, height = SMALL_PHONE_HEIGHT, density = Density(1f)) {
                CompositionLocalProvider(LocalAnalytics provides analytics) {
                    GameTheme(Language.SLOVENIAN) {
                        AppSettingsScreen(language = Language.SLOVENIAN, country = Country.SI)
                    }
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
     * The six flags and Bosnia's two scripts, whole, on a small phone in every skin, at the usual font and at a
     * phone's large one (130%): a flag or a chip too long for its row would run off or be cut, never wrapped. With
     * `KVIZIC_DESIGN_DIR` set, a still of each is written there.
     */
    @Test
    fun `the flags and the scripts fit on a small phone at a large font`() {
        Language.entries.forEach { language ->
            Skins.ALL.forEach { skin ->
                listOf(1f, LARGE_FONT).forEach { fontScale ->
                    val where = "$language, ${skin.id} at $fontScale"
                    val scene =
                        scene(
                            language,
                            country = Country.BA,
                            skin = skin,
                            fontScale = fontScale,
                            width = SMALL_PHONE_WIDTH,
                            height = SMALL_PHONE_HEIGHT,
                        )
                    try {
                        write("settings-${skin.id}-$fontScale-${language.tag}") { scene.renderSettled() }
                        assertEquals(emptyList(), scene.cutTexts(), "$where: cut")
                        val names = stringsOf(language).settingsScreen.countries
                        val parts =
                            PLAYABLE.map { country ->
                                val name = checkNotNull(names.of(country))
                                name to scene.nodes().single { name in it.descriptions }.boundsInRoot
                            } +
                                listOf(CYRILLIC, LATIN).map { name ->
                                    name to scene.nodes().single { name in it.texts }.boundsInRoot
                                }
                        parts.forEach { (name, bounds) ->
                            assertTrue(bounds.left >= 0f && bounds.right <= SMALL_PHONE_WIDTH, "$where: $name runs off")
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
                )
            }
        }.also { it.render() }

    private companion object {
        /** The two scripts' chips, each written in itself. */
        const val CYRILLIC = "Ћирилица"
        const val LATIN = "Latinica"

        /** An iPhone SE's size less its status bar and the top bar above the screen. */
        const val SHORT_PHONE_WIDTH = 375
        const val SHORT_PHONE_HEIGHT = 599

        /** A small phone, 360 by 640, less its status bar and the top bar. */
        const val SMALL_PHONE_WIDTH = 360
        const val SMALL_PHONE_HEIGHT = 560

        /** A phone's large font, 130%, a common setting. */
        const val LARGE_FONT = 1.3f

        /** The six countries, in the screen's order. */
        val PLAYABLE = listOf(Country.RS, Country.HR, Country.BA, Country.SI, Country.MK, Country.ME)
    }
}
