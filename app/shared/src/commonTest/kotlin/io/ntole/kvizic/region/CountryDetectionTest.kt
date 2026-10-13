package io.ntole.kvizic.region

import io.ntole.kvizic.core.domain.region.Country
import kotlin.test.Test
import kotlin.test.assertEquals

/** The country a first launch takes from what the device says, case by case. */
class CountryDetectionTest {
    private data class Case(
        val what: String,
        val hints: RegionHints,
        val country: Country,
    )

    @Test
    fun `each device is taken to play from where it says`() {
        val cases =
            listOf(
                // Where the phone is.
                Case("a phone in Serbia", RegionHints("rs", "rs", "RS", listOf("sr-Cyrl-RS")), Country.RS),
                Case("a phone in Croatia", RegionHints("hr", "hr", "HR", listOf("hr-HR")), Country.HR),
                Case("a phone in Bosnia", RegionHints("ba", "ba", "BA", listOf("bs-BA")), Country.BA),
                Case("a phone in Slovenia", RegionHints("si", "si", "SI", listOf("sl-SI")), Country.SI),
                Case("a phone in North Macedonia", RegionHints("mk", "mk", "MK", listOf("mk-MK")), Country.MK),
                Case("a phone in Montenegro", RegionHints("me", "me", "ME", listOf("sr-Latn-ME")), Country.ME),
                // The network before the SIM, and both before the languages.
                Case("a Bosnian at home on a Serbian SIM", RegionHints("ba", "rs", "BA", listOf("bs-BA")), Country.BA),
                Case(
                    "a Bosnian in Serbia on a Serbian SIM",
                    RegionHints("rs", "rs", "BA", listOf("bs-BA")),
                    Country.RS,
                ),
                Case("a Croat in Serbia in English", RegionHints("rs", "hr", "US", listOf("en-US")), Country.RS),
                Case("no network, a Slovenian SIM", RegionHints(null, "si", "US", listOf("en")), Country.SI),
                Case("codes in capitals, as some phones say them", RegionHints("ME", null, null), Country.ME),
                // The diaspora: a network and a SIM of none of the six, and the owner's own language.
                Case(
                    "a Serbian phone in Germany",
                    RegionHints("de", "de", "DE", listOf("sr-Latn-RS", "de-DE")),
                    Country.RS,
                ),
                Case(
                    "a Croatian phone in Austria, German first",
                    RegionHints("at", "at", "AT", listOf("de-AT", "hr-HR")),
                    Country.HR,
                ),
                Case("a Montenegrin's own language", RegionHints("de", "de", "DE", listOf("cnr-ME")), Country.ME),
                Case("Macedonian in Switzerland", RegionHints("ch", "ch", "CH", listOf("mk")), Country.MK),
                Case(
                    "a tablet with no SIM goes by its owner's language",
                    RegionHints(null, null, "US", listOf("en-US", "sl-SI")),
                    Country.SI,
                ),
                // The locale's country when no language says one.
                Case(
                    "an English phone set to Bosnia",
                    RegionHints("de", "de", "BA", listOf("en-GB", "de-DE")),
                    Country.BA,
                ),
                // Else Serbia.
                Case("a phone in Germany in German", RegionHints("de", "de", "DE", listOf("de-DE")), Country.RS),
                Case("a device that says nothing", RegionHints(), Country.RS),
                Case("Kosovo is none of the six", RegionHints("xk", "xk", "XK", listOf("sq-XK")), Country.RS),
                Case("blanks are no code", RegionHints(" ", "", "", listOf("")), Country.RS),
            )

        cases.forEach { case -> assertEquals(case.country, CountryDetection.detect(case.hints), case.what) }
    }

    /** An iPhone says no network or SIM: its locale's region and its languages, as `NSLocale` gives them. */
    @Test
    fun `an iPhone goes by its languages then its region`() {
        fun iPhone(
            region: String?,
            languages: List<String>,
        ) = RegionHints(localeCountry = region, preferredLanguages = languages)

        assertEquals(Country.HR, CountryDetection.detect(iPhone("HR", listOf("hr-HR", "en-HR"))))
        assertEquals(Country.RS, CountryDetection.detect(iPhone("DE", listOf("sr-Latn-RS", "de-DE"))))
        assertEquals(Country.BA, CountryDetection.detect(iPhone("BA", listOf("en-BA"))))
        assertEquals(Country.RS, CountryDetection.detect(iPhone("US", listOf("en-US"))))
    }

    /** A browser says its languages alone, as `navigator.languages` lists them, and the region the first names. */
    @Test
    fun `a browser goes by its languages`() {
        fun browser(languages: List<String>) =
            RegionHints(
                localeCountry = languages.firstOrNull()?.let { LanguageTag.parse(it)?.region },
                preferredLanguages = languages,
            )

        assertEquals(Country.MK, CountryDetection.detect(browser(listOf("mk", "en-US", "en"))))
        assertEquals(Country.SI, CountryDetection.detect(browser(listOf("sl-SI", "sl", "en"))))
        assertEquals(Country.ME, CountryDetection.detect(browser(listOf("en-ME", "en"))))
        assertEquals(Country.RS, CountryDetection.detect(browser(listOf("en-US", "en"))))
        assertEquals(Country.RS, CountryDetection.detect(browser(emptyList())))
    }

    @Test
    fun `a tag's language and script and region are read in any spelling`() {
        assertEquals(LanguageTag("sr", "latn", "RS"), LanguageTag.parse("sr-Latn-RS"))
        assertEquals(LanguageTag("sr", null, "ME"), LanguageTag.parse("sr_ME"))
        assertEquals(LanguageTag("hr", null, "HR"), LanguageTag.parse("HR-hr"))
        assertEquals(LanguageTag("cnr", null, null), LanguageTag.parse("cnr"))
        assertEquals(LanguageTag("es", null, "419"), LanguageTag.parse("es-419"))
        assertEquals(LanguageTag("sr", "cyrl", null), LanguageTag.parse("sr-Cyrl-u-nu-latn"))
        assertEquals(null, LanguageTag.parse(""))
        assertEquals(null, LanguageTag.parse("x-private"))
    }
}
