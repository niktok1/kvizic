package io.ntole.kvizic.region

import io.ntole.kvizic.core.domain.region.Country

/**
 * The country a first launch takes for the player, from what the device says ([RegionHints]), in this order:
 *
 * 1. the mobile network's country, then the SIM's, the first that is one of the six: where the phone is;
 * 2. else the first of the owner's languages that one of the six speaks (`sr` Serbia, `hr` Croatia, `bs` Bosnia,
 *    `sl` Slovenia, `mk` North Macedonia, `cnr` Montenegro), so a Serbian phone abroad on a German SIM stays
 *    Serbian, and a tablet with no SIM goes by its owner's language; a tag whose region is one of the six
 *    gives that region instead (the owner, 2026-10-14): `sr-Latn-ME` Montenegro, `hr-BA` and `sr-Cyrl-BA`
 *    Bosnia, while `sr-DE` stays Serbia;
 * 3. else the locale's country, if it is one of the six;
 * 4. else Serbia.
 *
 * Never UNKNOWN. Pure: the platforms only read the hints.
 */
internal object CountryDetection {
    fun detect(hints: RegionHints): Country =
        countryOfCode(hints.networkCountry)
            ?: countryOfCode(hints.simCountry)
            ?: hints.preferredLanguages.firstNotNullOfOrNull(::countryOfLanguage)
            ?: countryOfCode(hints.localeCountry)
            ?: Country.RS

    /** The country one of the six's ISO code names, in any case; none for any other, or for none. */
    fun countryOfCode(code: String?): Country? {
        val name = code?.trim()?.uppercase() ?: return null
        return PLAYABLE_COUNTRIES.firstOrNull { it.name == name }
    }

    /** The country whose language [tag] is: its region when that is one of the six, else its language's. */
    private fun countryOfLanguage(tag: String): Country? {
        val parsed = LanguageTag.parse(tag) ?: return null
        val spoken = SPOKEN_IN[parsed.language] ?: return null
        return countryOfCode(parsed.region) ?: spoken
    }

    /** Each of the six's own language, by its ISO 639 code: Montenegrin has its own since 2017. */
    private val SPOKEN_IN: Map<String, Country> =
        mapOf(
            "sr" to Country.RS,
            "hr" to Country.HR,
            "bs" to Country.BA,
            "sl" to Country.SI,
            "mk" to Country.MK,
            "cnr" to Country.ME,
        )
}
