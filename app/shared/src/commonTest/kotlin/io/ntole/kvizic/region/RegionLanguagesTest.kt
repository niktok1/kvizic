package io.ntole.kvizic.region

import io.ntole.kvizic.core.domain.region.Country
import io.ntole.kvizic.language.Language
import kotlin.test.Test
import kotlin.test.assertEquals

/** What each country's flag reads in, and the language a flag lands on for one reading another. */
class RegionLanguagesTest {
    @Test
    fun `Serbia Montenegro and Bosnia read two scripts and the rest their own language`() {
        assertEquals(listOf(Language.SERBIAN_CYRILLIC, Language.SERBIAN_LATIN), RegionLanguages.of(Country.RS))
        assertEquals(listOf(Language.SERBIAN_LATIN, Language.SERBIAN_CYRILLIC), RegionLanguages.of(Country.ME))
        assertEquals(listOf(Language.BOSNIAN, Language.SERBIAN_CYRILLIC), RegionLanguages.of(Country.BA))
        assertEquals(listOf(Language.CROATIAN), RegionLanguages.of(Country.HR))
        assertEquals(listOf(Language.SLOVENIAN), RegionLanguages.of(Country.SI))
        assertEquals(listOf(Language.MACEDONIAN), RegionLanguages.of(Country.MK))
    }

    @Test
    fun `a flag keeps the language it reads or else its script or else the country's own`() {
        val cases =
            listOf(
                Triple(Country.RS, Language.SERBIAN_LATIN, Language.SERBIAN_LATIN),
                Triple(Country.RS, Language.CROATIAN, Language.SERBIAN_LATIN),
                Triple(Country.RS, Language.MACEDONIAN, Language.SERBIAN_CYRILLIC),
                Triple(Country.ME, Language.SERBIAN_CYRILLIC, Language.SERBIAN_CYRILLIC),
                Triple(Country.ME, null, Language.SERBIAN_LATIN),
                Triple(Country.BA, Language.SERBIAN_LATIN, Language.BOSNIAN),
                Triple(Country.BA, Language.SERBIAN_CYRILLIC, Language.SERBIAN_CYRILLIC),
                Triple(Country.BA, null, Language.BOSNIAN),
                Triple(Country.HR, Language.SERBIAN_CYRILLIC, Language.CROATIAN),
                Triple(Country.MK, Language.SLOVENIAN, Language.MACEDONIAN),
                Triple(Country.UNKNOWN, Language.SLOVENIAN, Language.SLOVENIAN),
            )
        cases.forEach { (country, reading, expected) ->
            assertEquals(expected, RegionLanguages.picked(country, reading), "$country reading $reading")
        }
    }

    /** Before a country's own language is offered, Serbian in its script stands in: Latin for Latin readers. */
    @Test
    fun `a language not offered yet gives way to Serbian in its script`() {
        val serbian = listOf(Language.SERBIAN_CYRILLIC, Language.SERBIAN_LATIN)
        assertEquals(Language.SERBIAN_LATIN, RegionLanguages.picked(Country.HR, null, serbian))
        assertEquals(Language.SERBIAN_LATIN, RegionLanguages.picked(Country.BA, null, serbian))
        assertEquals(Language.SERBIAN_CYRILLIC, RegionLanguages.picked(Country.BA, Language.SERBIAN_CYRILLIC, serbian))
        assertEquals(Language.SERBIAN_CYRILLIC, RegionLanguages.picked(Country.MK, null, serbian))
        assertEquals(listOf(Language.SERBIAN_CYRILLIC), RegionLanguages.choices(Country.BA, serbian))
    }
}
