package io.ntole.kvizic.language

import io.ntole.kvizic.core.domain.region.ContentLanguages
import kotlin.test.Test
import kotlin.test.assertEquals

/** What each language is written in, which of the server's languages it reads, and what its chip says. */
class LanguageTest {
    @Test
    fun `Serbian Cyrillic and Macedonian are written in Cyrillic and the rest in Latin`() {
        assertEquals(
            setOf(Language.SERBIAN_CYRILLIC, Language.MACEDONIAN),
            Language.entries.filter { it.isCyrillic }.toSet(),
        )
    }

    @Test
    fun `Serbian in either script and English read the Serbian and the rest their own`() {
        assertEquals(
            mapOf(
                Language.SERBIAN_CYRILLIC to ContentLanguages.SERBIAN,
                Language.SERBIAN_LATIN to ContentLanguages.SERBIAN,
                Language.CROATIAN to ContentLanguages.CROATIAN,
                Language.BOSNIAN to ContentLanguages.BOSNIAN,
                Language.SLOVENIAN to ContentLanguages.SLOVENIAN,
                Language.MACEDONIAN to ContentLanguages.MACEDONIAN,
                Language.ENGLISH to ContentLanguages.SERBIAN,
            ),
            Language.entries.associateWith { it.contentTag },
        )
    }

    @Test
    fun `each language is kept by its tag and an unknown tag is the default`() {
        Language.entries.forEach { assertEquals(it, Language.ofTag(it.tag)) }
        assertEquals(Language.DEFAULT, Language.ofTag("de"))
        assertEquals(Language.DEFAULT, Language.ofTag(null))
    }

    @Test
    fun `the chips name each language in itself`() {
        assertEquals(
            listOf("Српски", "Srpski", "Hrvatski", "Bosanski", "Slovenščina", "Македонски", "English"),
            Language.entries.map { it.ownName },
        )
    }
}
