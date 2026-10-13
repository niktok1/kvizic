package io.ntole.kvizic.region

import io.ntole.kvizic.core.domain.region.Country
import io.ntole.kvizic.language.Language

/**
 * The language a first launch shows the game in, picked once and kept ([RegionStore]). Pure.
 */
internal object FirstLaunch {
    /**
     * The first of the device's languages ([RegionHints.preferredLanguages]) the game offers: Serbian in Latin
     * for a tag that says the Latin script (`sr-Latn-RS`), in Cyrillic for any other Serbian, then Croatian,
     * Bosnian, Slovenian and Macedonian, each as itself. Else the language of [country]
     * ([Country.defaultFor]), Montenegro's Serbian in Latin. Else, while that one is not offered yet, Serbian in
     * that language's script, Latin for Croatia, Bosnia, Slovenia and Montenegro, so nobody who reads Latin is
     * shown Cyrillic for want of their own; and [Language.DEFAULT] should [offered] hold neither.
     */
    fun language(
        hints: RegionHints,
        country: Country,
        offered: List<Language> = Language.OFFERED,
    ): Language {
        hints.preferredLanguages
            .firstNotNullOfOrNull { tag -> languageOf(tag)?.takeIf { it in offered } }
            ?.let { return it }
        val own = if (country == Country.ME) Language.SERBIAN_LATIN else Language.ofTag(Country.defaultFor(country))
        val serbian = if (own.isCyrillic) Language.SERBIAN_CYRILLIC else Language.SERBIAN_LATIN
        return listOf(own, serbian).firstOrNull { it in offered } ?: Language.DEFAULT
    }

    /**
     * The language a first launch would show were every one of the game's offered: the one [language] gives in a
     * build that offers it, and waits for in one that does not yet ([RegionStore]). Never English, never a
     * stand-in.
     */
    fun wanted(
        hints: RegionHints,
        country: Country,
    ): Language = language(hints, country, offered = Language.entries)

    /** The game's language [tag] is, or none: English is not one a first launch takes, being offered to nobody. */
    private fun languageOf(tag: String): Language? {
        val parsed = LanguageTag.parse(tag) ?: return null
        return when (parsed.language) {
            "sr" -> if (parsed.script == LATIN) Language.SERBIAN_LATIN else Language.SERBIAN_CYRILLIC
            "hr" -> Language.CROATIAN
            "bs" -> Language.BOSNIAN
            "sl" -> Language.SLOVENIAN
            "mk" -> Language.MACEDONIAN
            else -> null
        }
    }

    /** The Latin script's subtag, as [LanguageTag] keeps it. */
    private const val LATIN = "latn"
}
