package io.ntole.kvizic.region

import io.ntole.kvizic.core.domain.region.Country

/**
 * What a device says of where it is and what its owner reads, as a first launch reads it to pick the country
 * ([CountryDetection]) and the language ([FirstLaunch]). Each country is an ISO 3166 code in any case, or none
 * where the device says none; each language a BCP 47 tag, the owner's first first. What a platform has no way to
 * know it leaves out: only an Android phone knows its network's and its SIM's, a browser only its languages.
 */
data class RegionHints(
    /** The country of the mobile network the phone is on, an Android phone's. */
    val networkCountry: String? = null,
    /** The country of the SIM's operator, an Android phone's. */
    val simCountry: String? = null,
    /** The country of the device's locale, its region setting. */
    val localeCountry: String? = null,
    /** The languages the owner prefers, first first: `sr-Latn-RS`, `hr-HR`, `en`. */
    val preferredLanguages: List<String> = emptyList(),
)

/** The countries a player may play from, in the order the Settings screen offers them: every one but UNKNOWN. */
internal val PLAYABLE_COUNTRIES: List<Country> = Country.entries.filter { it != Country.UNKNOWN }

/**
 * The parts of a BCP 47 tag that say a language and where it is spoken: [language] and [script] in lower case,
 * [region] in capitals, as `sr-Latn-RS` is `sr`, `latn` and `RS`. A platform's own spelling with underscores
 * (`sr_RS`) reads the same. What follows the region, a variant or an extension, is left out.
 */
internal data class LanguageTag(
    val language: String,
    val script: String? = null,
    val region: String? = null,
) {
    companion object {
        /** The parts of [tag], or none when it does not begin with a language. */
        fun parse(tag: String): LanguageTag? {
            val parts = tag.trim().split('-', '_').filter { it.isNotEmpty() }
            val language = parts.firstOrNull()?.lowercase()?.takeIf { it.length in 2..3 && it.all(Char::isLetter) }
            if (language == null) return null
            var script: String? = null
            var region: String? = null
            for (part in parts.drop(1)) {
                when {
                    // A singleton begins an extension or a private use: nothing after it says where.
                    part.length == 1 -> {
                        break
                    }

                    script == null && region == null && part.length == 4 && part.all(Char::isLetter) -> {
                        script = part.lowercase()
                    }

                    region == null && part.length == 2 && part.all(Char::isLetter) -> {
                        region = part.uppercase()
                    }

                    region == null && part.length == 3 && part.all(Char::isDigit) -> {
                        region = part
                    }

                    else -> {
                        break
                    }
                }
            }
            return LanguageTag(language, script, region)
        }
    }
}
