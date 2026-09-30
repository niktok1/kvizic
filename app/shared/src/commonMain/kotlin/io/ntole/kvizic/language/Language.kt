package io.ntole.kvizic.language

/**
 * The languages the game's words come in. Serbian Cyrillic is the source; Serbian Latin is made from it.
 *
 * [ownName] is the language as a picker names it, in itself, whatever language the screen is in: a player
 * who picked one they cannot read still finds their own. So it is not one of [Strings]. [tag] is what the
 * device keeps for it, a BCP 47 tag, which never changes with the enum's names.
 */
enum class Language(
    val ownName: String,
    val tag: String,
) {
    SERBIAN_CYRILLIC(ownName = "Ћирилица", tag = "sr-Cyrl"),
    SERBIAN_LATIN(ownName = "Latinica", tag = "sr-Latn"),
    ENGLISH(ownName = "English", tag = "en"),
    ;

    companion object {
        /** The language of a first launch, whatever the device's own: the game is Serbian first. */
        val DEFAULT: Language = SERBIAN_CYRILLIC

        /** The language [tag] names, or [DEFAULT] for none or one this build does not know. */
        fun ofTag(tag: String?): Language = entries.firstOrNull { it.tag == tag } ?: DEFAULT

        /**
         * The languages a picker offers, in its order: the one list that decides it. English is kept, and a
         * device that kept it shows it still, but the launch offers Serbian alone, in both scripts.
         */
        val OFFERED: List<Language> = listOf(SERBIAN_CYRILLIC, SERBIAN_LATIN)
    }
}
