package io.ntole.kvizic.region

import io.ntole.kvizic.core.domain.region.Country
import io.ntole.kvizic.language.Language
import kotlin.test.Test
import kotlin.test.assertEquals

/** The language a first launch shows the game in, with the six the release offers and with today's two. */
class FirstLaunchTest {
    @Test
    fun `the device's first language the game offers is the one`() {
        val cases =
            listOf(
                Triple(listOf("sr-Cyrl-RS"), Country.RS, Language.SERBIAN_CYRILLIC),
                Triple(listOf("sr-Latn-RS"), Country.RS, Language.SERBIAN_LATIN),
                Triple(listOf("sr_Latn_ME"), Country.ME, Language.SERBIAN_LATIN),
                // Serbian with no script is the Cyrillic it is written in first, wherever it is.
                Triple(listOf("sr"), Country.ME, Language.SERBIAN_CYRILLIC),
                Triple(listOf("sr-BA"), Country.BA, Language.SERBIAN_CYRILLIC),
                Triple(listOf("hr-HR"), Country.HR, Language.CROATIAN),
                Triple(listOf("bs-BA"), Country.BA, Language.BOSNIAN),
                Triple(listOf("sl-SI"), Country.SI, Language.SLOVENIAN),
                Triple(listOf("mk-MK"), Country.MK, Language.MACEDONIAN),
                // A Serb in Croatia reads Serbian, a Croat in Serbia Croatian: the language, never the country.
                Triple(listOf("sr-Cyrl"), Country.HR, Language.SERBIAN_CYRILLIC),
                Triple(listOf("hr"), Country.RS, Language.CROATIAN),
                // The first the game has, past those it has not.
                Triple(listOf("de-DE", "en-US", "sl-SI"), Country.RS, Language.SLOVENIAN),
            )

        cases.forEach { (languages, country, expected) ->
            assertEquals(expected, FirstLaunch.language(hints(languages), country, RELEASE), "$languages in $country")
        }
    }

    /** English is offered to nobody, so a device in English, or in any other, goes by its country. */
    @Test
    fun `a device in none of the game's languages takes its country's`() {
        val inEnglish = hints(listOf("en-US", "de-DE"))
        val expected =
            mapOf(
                Country.RS to Language.SERBIAN_CYRILLIC,
                Country.HR to Language.CROATIAN,
                Country.BA to Language.BOSNIAN,
                Country.SI to Language.SLOVENIAN,
                Country.MK to Language.MACEDONIAN,
                // Montenegro reads Serbian, shown in Latin.
                Country.ME to Language.SERBIAN_LATIN,
                Country.UNKNOWN to Language.SERBIAN_CYRILLIC,
            )

        Country.entries.forEach { country ->
            assertEquals(expected.getValue(country), FirstLaunch.language(inEnglish, country, RELEASE), "$country")
            assertEquals(expected.getValue(country), FirstLaunch.language(RegionHints(), country, RELEASE), "$country")
        }
    }

    /**
     * While a country's own language is not offered yet, its player reads Serbian in the script theirs is written
     * in, never Cyrillic for want of Latin; and a device's language not offered yet is passed over.
     */
    @Test
    fun `a language not offered yet gives way to Serbian in its script`() {
        val expected =
            mapOf(
                Country.RS to Language.SERBIAN_CYRILLIC,
                Country.HR to Language.SERBIAN_LATIN,
                Country.BA to Language.SERBIAN_LATIN,
                Country.SI to Language.SERBIAN_LATIN,
                Country.MK to Language.SERBIAN_CYRILLIC,
                Country.ME to Language.SERBIAN_LATIN,
            )

        expected.forEach { (country, language) ->
            assertEquals(language, FirstLaunch.language(hints(listOf("en")), country, SERBIAN_ONLY), "$country")
        }
        assertEquals(
            Language.SERBIAN_LATIN,
            FirstLaunch.language(hints(listOf("hr-HR", "sr-Latn-HR")), Country.HR, SERBIAN_ONLY),
        )
        assertEquals(Language.DEFAULT, FirstLaunch.language(hints(listOf("hr")), Country.HR, offered = emptyList()))
    }

    private fun hints(languages: List<String>) = RegionHints(preferredLanguages = languages)

    private companion object {
        /** The six the release offers. */
        val RELEASE =
            listOf(
                Language.SERBIAN_CYRILLIC,
                Language.SERBIAN_LATIN,
                Language.CROATIAN,
                Language.BOSNIAN,
                Language.SLOVENIAN,
                Language.MACEDONIAN,
            )

        /** What is offered until the four are reviewed. */
        val SERBIAN_ONLY = listOf(Language.SERBIAN_CYRILLIC, Language.SERBIAN_LATIN)
    }
}
