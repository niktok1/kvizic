package io.ntole.kvizic.region

import io.ntole.kvizic.core.domain.region.Country
import io.ntole.kvizic.language.Language

/**
 * The language a first launch shows the game in, picked once and kept ([RegionStore]). Pure.
 */
internal object FirstLaunch {
    /**
     * The language [country]'s flag lands on ([RegionLanguages.picked]) for one who reads the first of the
     * device's languages ([RegionHints.preferredLanguages]) the game knows: Serbian in Latin for a tag that says
     * the Latin script (`sr-Latn-RS`), in Cyrillic for any other Serbian, then Croatian, Bosnian, Slovenian and
     * Macedonian, each as itself. So the country decides, as its flag does on the Settings screen (the owner,
     * 2026-10-14), and the device's language only which script of two: Serbian in Latin in Serbia for a phone in
     * Latin, Serbian in Cyrillic in Bosnia for one in Cyrillic, and the country's own for one in neither. While
     * the country's is not offered yet, Serbian in that one's script, so nobody who reads Latin is shown Cyrillic
     * for want of their own; and [Language.DEFAULT] should [offered] hold neither.
     */
    fun language(
        hints: RegionHints,
        country: Country,
        offered: List<Language> = Language.OFFERED,
    ): Language = RegionLanguages.picked(country, hints.preferredLanguages.firstNotNullOfOrNull(::languageOf), offered)

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
