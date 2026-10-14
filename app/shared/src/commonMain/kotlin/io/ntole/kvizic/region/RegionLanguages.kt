package io.ntole.kvizic.region

import io.ntole.kvizic.core.domain.region.Country
import io.ntole.kvizic.language.Language

/**
 * The languages each country's flag reads in, its own first (the owner, 2026-10-14: a flag picks the country
 * and the language with it). Serbia and Montenegro read Serbian in either script, Montenegro in Latin first;
 * Bosnia in Latin reads Bosnian and in Cyrillic Serbian, ijekavian there (the content repo's STYLE.md);
 * Croatia, Slovenia and North Macedonia read their own language alone. UNKNOWN, a country this build does not
 * know, reads any of them. Pure.
 */
internal object RegionLanguages {
    fun of(country: Country): List<Language> =
        when (country) {
            Country.RS -> listOf(Language.SERBIAN_CYRILLIC, Language.SERBIAN_LATIN)
            Country.ME -> listOf(Language.SERBIAN_LATIN, Language.SERBIAN_CYRILLIC)
            Country.BA -> listOf(Language.BOSNIAN, Language.SERBIAN_CYRILLIC)
            Country.HR -> listOf(Language.CROATIAN)
            Country.SI -> listOf(Language.SLOVENIAN)
            Country.MK -> listOf(Language.MACEDONIAN)
            Country.UNKNOWN -> ANY
        }

    /** The languages of [country] among [offered], its own first: a choice of script where there are two. */
    fun choices(
        country: Country,
        offered: List<Language> = Language.OFFERED,
    ): List<Language> = of(country).filter { it in offered }

    /**
     * The language [country]'s flag lands on for a player who reads [reading]: that one, when the country reads
     * it; else the country's in the same script, so a player in Latin stays in Latin where there is a choice
     * (Srpski from Hrvatski, Bosanski from Srpski); else the country's own. Among [offered]: while the country's
     * own is not offered yet, Serbian in its script stands in for it, so nobody who reads Latin is shown Cyrillic
     * for want of their own (Bosnia's Latin before Bosnian is offered), then any of the country's, then
     * [Language.DEFAULT].
     */
    fun picked(
        country: Country,
        reading: Language?,
        offered: List<Language> = Language.OFFERED,
    ): Language {
        val choices = choices(country, offered)
        if (reading != null) {
            choices.firstOrNull { it == reading }?.let { return it }
            choices.firstOrNull { it.isCyrillic == reading.isCyrillic }?.let { return it }
        }
        val own = of(country).first()
        val serbian = if (own.isCyrillic) Language.SERBIAN_CYRILLIC else Language.SERBIAN_LATIN
        return listOf(own, serbian).firstOrNull { it in offered } ?: choices.firstOrNull() ?: Language.DEFAULT
    }

    private val ANY =
        listOf(
            Language.SERBIAN_CYRILLIC,
            Language.SERBIAN_LATIN,
            Language.CROATIAN,
            Language.BOSNIAN,
            Language.SLOVENIAN,
            Language.MACEDONIAN,
        )
}
