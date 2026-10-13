package io.ntole.kvizic.core.region

/**
 * The languages a question is written in, by tag: what the bank stores, a draft names and a player reads.
 * A content language is not a UI language: Serbian Latin and English read the Serbian Cyrillic text, which a
 * client makes Latin by transliteration, so the bank never holds Serbian twice.
 *
 * A question's own text is in its source language; its translations are in the others. Each player reads the
 * one [chain] finds first for theirs, the same question with its answers in the same order on every screen.
 */
public object ContentLanguages {
    public const val SERBIAN: String = "sr-Cyrl"
    public const val CROATIAN: String = "hr"
    public const val BOSNIAN: String = "bs"
    public const val SLOVENIAN: String = "sl"
    public const val MACEDONIAN: String = "mk"

    /** Every content language, Serbian first. */
    public val ALL: List<String> = listOf(SERBIAN, CROATIAN, BOSNIAN, SLOVENIAN, MACEDONIAN)

    private val CHAINS: Map<String, List<String>> =
        mapOf(
            SERBIAN to listOf(SERBIAN, BOSNIAN, CROATIAN, MACEDONIAN, SLOVENIAN),
            CROATIAN to listOf(CROATIAN, BOSNIAN, SERBIAN, SLOVENIAN, MACEDONIAN),
            BOSNIAN to listOf(BOSNIAN, CROATIAN, SERBIAN, SLOVENIAN, MACEDONIAN),
            SLOVENIAN to listOf(SLOVENIAN, CROATIAN, BOSNIAN, SERBIAN, MACEDONIAN),
            MACEDONIAN to listOf(MACEDONIAN, SERBIAN, BOSNIAN, CROATIAN, SLOVENIAN),
        )

    /**
     * The content language a client's UI language [uiTag] reads: hr, bs, sl and mk their own, in any case,
     * trimmed; Serbian in either script, English, none, a blank tag or one this build does not know, Serbian
     * Cyrillic. An old client says none.
     */
    public fun of(uiTag: String?): String = ALL.firstOrNull { it.equals(uiTag?.trim(), ignoreCase = true) } ?: SERBIAN

    /**
     * Where to look for a text for a reader of [lang], nearest first, every content language once. A tag
     * that is not a content language looks as [of] maps it. Whatever the chain finds nothing in, the source
     * text is the last resort.
     */
    public fun chain(lang: String): List<String> = CHAINS.getValue(of(lang))

    /**
     * The languages a question scoped to [countries] must be readable in, its source's included: every one
     * for a common question (none scoped), else those its countries read. Bosnia reads three. [Country.UNKNOWN]
     * reads none, being a country no question is scoped to.
     */
    public fun required(countries: Set<Country>): Set<String> {
        if (countries.isEmpty()) return ALL.toSet()
        val needed = countries.flatMap(::readIn).toSet()
        return ALL.filter { it in needed }.toSet()
    }

    /**
     * The content language a player in [country] reads by default, which a client's first launch copies.
     * Montenegro reads Serbian, shown in Latin by the client; a country this build does not know, Serbian.
     */
    public fun defaultFor(country: Country): String =
        when (country) {
            Country.HR -> CROATIAN
            Country.BA -> BOSNIAN
            Country.SI -> SLOVENIAN
            Country.MK -> MACEDONIAN
            Country.RS, Country.ME, Country.UNKNOWN -> SERBIAN
        }

    private fun readIn(country: Country): List<String> =
        when (country) {
            Country.RS, Country.ME -> listOf(SERBIAN)
            Country.HR -> listOf(CROATIAN)
            Country.BA -> listOf(BOSNIAN, CROATIAN, SERBIAN)
            Country.SI -> listOf(SLOVENIAN)
            Country.MK -> listOf(MACEDONIAN)
            Country.UNKNOWN -> emptyList()
        }
}
