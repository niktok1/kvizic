package io.ntole.kvizic.core.region

import io.ntole.kvizic.core.region.ContentLanguages.BOSNIAN
import io.ntole.kvizic.core.region.ContentLanguages.CROATIAN
import io.ntole.kvizic.core.region.ContentLanguages.MACEDONIAN
import io.ntole.kvizic.core.region.ContentLanguages.SERBIAN
import io.ntole.kvizic.core.region.ContentLanguages.SLOVENIAN
import kotlin.test.Test
import kotlin.test.assertEquals

class ContentLanguagesTest {
    @Test
    fun theTagsAreTheBanksInTheirOrder() {
        assertEquals(listOf("sr-Cyrl", "hr", "bs", "sl", "mk"), ContentLanguages.ALL)
    }

    @Test
    fun aUiLanguageReadsItsContentLanguage() {
        assertEquals(CROATIAN, ContentLanguages.of("hr"))
        assertEquals(BOSNIAN, ContentLanguages.of("bs"))
        assertEquals(SLOVENIAN, ContentLanguages.of("sl"))
        assertEquals(MACEDONIAN, ContentLanguages.of("mk"))
        assertEquals(SERBIAN, ContentLanguages.of("sr-Cyrl"))
    }

    @Test
    fun serbianLatinEnglishAndAnythingElseReadSerbianCyrillic() {
        listOf("sr-Latn", "en", null, "", "  ", "de", "sr").forEach { tag ->
            assertEquals(SERBIAN, ContentLanguages.of(tag), "$tag")
        }
    }

    @Test
    fun eachLanguageLooksNearestFirstAndThroughEveryOneOnce() {
        assertEquals(listOf(SERBIAN, BOSNIAN, CROATIAN, MACEDONIAN, SLOVENIAN), ContentLanguages.chain(SERBIAN))
        assertEquals(listOf(CROATIAN, BOSNIAN, SERBIAN, SLOVENIAN, MACEDONIAN), ContentLanguages.chain(CROATIAN))
        assertEquals(listOf(BOSNIAN, CROATIAN, SERBIAN, SLOVENIAN, MACEDONIAN), ContentLanguages.chain(BOSNIAN))
        assertEquals(listOf(SLOVENIAN, CROATIAN, BOSNIAN, SERBIAN, MACEDONIAN), ContentLanguages.chain(SLOVENIAN))
        assertEquals(listOf(MACEDONIAN, SERBIAN, BOSNIAN, CROATIAN, SLOVENIAN), ContentLanguages.chain(MACEDONIAN))
        ContentLanguages.ALL.forEach { lang ->
            assertEquals(ContentLanguages.ALL.toSet(), ContentLanguages.chain(lang).toSet(), lang)
            assertEquals(ContentLanguages.ALL.size, ContentLanguages.chain(lang).size, lang)
        }
    }

    @Test
    fun aTagThatIsNoContentLanguageLooksAsSerbian() {
        assertEquals(ContentLanguages.chain(SERBIAN), ContentLanguages.chain("sr-Latn"))
        assertEquals(ContentLanguages.chain(SERBIAN), ContentLanguages.chain(""))
    }

    @Test
    fun aCommonQuestionMustReadInEveryLanguage() {
        assertEquals(ContentLanguages.ALL.toSet(), ContentLanguages.required(emptySet()))
    }

    @Test
    fun aScopedQuestionMustReadInWhatItsCountriesRead() {
        assertEquals(setOf(SERBIAN), ContentLanguages.required(setOf(Country.RS)))
        assertEquals(setOf(CROATIAN), ContentLanguages.required(setOf(Country.HR)))
        assertEquals(setOf(BOSNIAN, CROATIAN, SERBIAN), ContentLanguages.required(setOf(Country.BA)))
        assertEquals(setOf(SLOVENIAN), ContentLanguages.required(setOf(Country.SI)))
        assertEquals(setOf(MACEDONIAN), ContentLanguages.required(setOf(Country.MK)))
        assertEquals(setOf(SERBIAN), ContentLanguages.required(setOf(Country.ME)))
        assertEquals(setOf(SERBIAN, CROATIAN), ContentLanguages.required(setOf(Country.RS, Country.HR)))
        assertEquals(setOf(SERBIAN, MACEDONIAN), ContentLanguages.required(setOf(Country.ME, Country.MK)))
    }

    @Test
    fun eachCountryReadsItsOwnLanguageByDefault() {
        assertEquals(
            mapOf(
                Country.RS to SERBIAN,
                Country.HR to CROATIAN,
                Country.BA to BOSNIAN,
                Country.SI to SLOVENIAN,
                Country.MK to MACEDONIAN,
                Country.ME to SERBIAN,
                Country.UNKNOWN to SERBIAN,
            ),
            Country.entries.associateWith(ContentLanguages::defaultFor),
        )
    }
}
