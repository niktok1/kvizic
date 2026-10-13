package io.ntole.kvizic.language

import io.ntole.kvizic.core.domain.region.ContentLanguages

/**
 * The languages the game's words come in. Serbian Cyrillic is the source; Serbian Latin is made from it.
 *
 * [ownName] is the language as a picker names it, in itself, whatever language the screen is in: a player
 * who picked one they cannot read still finds their own. So it is not one of [Strings]. [tag] is what the
 * device keeps for it, a BCP 47 tag, which never changes with the enum's names, and what its text is shaped
 * for. [contentTag] is the content language it reads the server's words in (`ContentLanguages`): Serbian
 * Latin and English read the Serbian Cyrillic, which the client shows in Latin. [isCyrillic] is whether it is
 * written in Cyrillic, which decides the script the server's words are shown in and its answers' letters.
 */
enum class Language(
    val ownName: String,
    val tag: String,
    val contentTag: String,
    val isCyrillic: Boolean,
) {
    SERBIAN_CYRILLIC(ownName = "Српски", tag = "sr-Cyrl", contentTag = ContentLanguages.SERBIAN, isCyrillic = true),
    SERBIAN_LATIN(ownName = "Srpski", tag = "sr-Latn", contentTag = ContentLanguages.SERBIAN, isCyrillic = false),
    CROATIAN(ownName = "Hrvatski", tag = "hr", contentTag = ContentLanguages.CROATIAN, isCyrillic = false),
    BOSNIAN(ownName = "Bosanski", tag = "bs", contentTag = ContentLanguages.BOSNIAN, isCyrillic = false),
    SLOVENIAN(ownName = "Slovenščina", tag = "sl", contentTag = ContentLanguages.SLOVENIAN, isCyrillic = false),
    MACEDONIAN(ownName = "Македонски", tag = "mk", contentTag = ContentLanguages.MACEDONIAN, isCyrillic = true),
    ENGLISH(ownName = "English", tag = "en", contentTag = ContentLanguages.SERBIAN, isCyrillic = false),
    ;

    companion object {
        /**
         * The language when none is kept: Serbian Cyrillic, the source. A first launch picks one for itself
         * (from the device's language, else the country's) and keeps it; an install from before keeps its own.
         */
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
