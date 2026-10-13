package io.ntole.kvizic.core.domain.region

/**
 * The languages the server's words are written in, by tag: a question, a topic's name. The wire's, copied,
 * since the domain cannot see them: `ContentLanguagesTest` in `:core:data` pins the tags and [chain]. A UI
 * language is not one of them: Serbian Latin and English read [SERBIAN], which the client makes Latin.
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
     * Where to look for a word for a reader of [lang], nearest first, every content language once: a topic's
     * name the server has in none of the nearer ones is read in the next. A tag that is no content language
     * looks as Serbian does.
     */
    public fun chain(lang: String): List<String> = CHAINS[lang] ?: CHAINS.getValue(SERBIAN)
}
