package io.ntole.kvizic.server.player

import io.ntole.kvizic.core.api.KvizicApi
import java.text.Normalizer

/**
 * Cleans a name another service gave a player (their Play Games name) before strangers see it.
 *
 * Normalized to NFC; stripped of control and format characters, which is where the bidirectional
 * overrides and the zero-width characters that could disguise a name live, and of anything unassigned
 * or private; whitespace collapsed and trimmed; cut to [maxLength] code points, a player's name's by default.
 * A name left blank, or holding a word from a short list of slurs and vulgarities in any of the six countries'
 * languages or English, is refused (null): the player keeps their generated nickname instead, and a room goes
 * unnamed. The list must never refuse an ordinary name of those countries (`DisplayNamesTest`).
 */
object DisplayNames {
    fun clean(
        raw: String?,
        maxLength: Int = KvizicApi.Limits.MAX_DISPLAY_NAME_LENGTH,
    ): String? {
        if (raw == null) return null
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFC)
        val kept = StringBuilder()
        var lastWasSpace = true
        var codePoints = 0
        var i = 0
        while (i < normalized.length && codePoints < maxLength) {
            val cp = normalized.codePointAt(i)
            i += Character.charCount(cp)
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                if (!lastWasSpace) {
                    kept.append(' ')
                    codePoints++
                }
                lastWasSpace = true
                continue
            }
            if (Character.getType(cp).toByte() in DROPPED_TYPES) continue
            kept.appendCodePoint(cp)
            codePoints++
            lastWasSpace = false
        }
        val name = kept.toString().trim()
        if (name.isEmpty() || isBlocked(name)) return null
        return name
    }

    /**
     * Whether [name] holds a blocked word, compared folded to plain Latin: a word that starts with one of
     * [BLOCKED_STEMS], or is one of [BLOCKED_WORDS], digits after it aside (`nazi88` is `nazi`).
     */
    internal fun isBlocked(name: String): Boolean =
        fold(name)
            .split(NON_WORD)
            .filter { it.isNotEmpty() }
            .any { word ->
                BLOCKED_STEMS.any { stem -> word.startsWith(stem) } ||
                    word.trimEnd { it.isDigit() } in BLOCKED_WORDS
            }

    private val NON_WORD = Regex("[^a-z0-9]+")

    /**
     * The form a name is matched in. Compatibility forms read as their letters (fullwidth letters, the ǉ, ǌ and ǆ
     * ligatures); diacritics drop, which takes Croatian, Bosnian, Slovenian and Montenegrin Latin to plain letters
     * and Macedonian ѓ, ќ, ѐ and ѝ to г, к, е and и; then lower-cased; Serbian and Macedonian Cyrillic
     * transliterated, ѕ as dz, and ђ and đ both as dj, so a name folds alike in either script; digits read as the
     * letters they mimic.
     */
    internal fun fold(text: String): String {
        val bare = Normalizer.normalize(text, Normalizer.Form.NFKD).replace(MARKS, "").lowercase()
        return buildString { bare.forEach { append(FOLDED[it] ?: it.toString()) } }
    }

    private val MARKS = Regex("\\p{M}+")

    private val DROPPED_TYPES: Set<Byte> =
        setOf(
            Character.CONTROL,
            Character.FORMAT,
            Character.SURROGATE,
            Character.PRIVATE_USE,
            Character.UNASSIGNED,
            Character.LINE_SEPARATOR,
            Character.PARAGRAPH_SEPARATOR,
        )

    private val CYRILLIC: Map<Char, String> =
        mapOf(
            'а' to "a",
            'б' to "b",
            'в' to "v",
            'г' to "g",
            'д' to "d",
            'ђ' to "dj",
            'е' to "e",
            'ж' to "z",
            'з' to "z",
            'и' to "i",
            'ј' to "j",
            'к' to "k",
            'л' to "l",
            'љ' to "lj",
            'м' to "m",
            'н' to "n",
            'њ' to "nj",
            'о' to "o",
            'п' to "p",
            'р' to "r",
            'с' to "s",
            'т' to "t",
            'ћ' to "c",
            'у' to "u",
            'ф' to "f",
            'х' to "h",
            'ц' to "c",
            'ч' to "c",
            'џ' to "dz",
            'ш' to "s",
            // Macedonian's own (ѓ and ќ are г and к with an accent, which has dropped by now)
            'ѕ' to "dz",
        )

    private val LEET: Map<Char, String> =
        mapOf('0' to "o", '1' to "i", '3' to "e", '4' to "a", '5' to "s", '@' to "a")

    /** What [fold] writes for a character, after the diacritics have dropped: Cyrillic, đ, and digits. */
    private val FOLDED: Map<Char, String> = CYRILLIC + ('đ' to "dj") + LEET

    /**
     * Stems, folded: a word that starts with one is blocked. Each is as short as is safe, since a stem that starts
     * an ordinary word or name is worse than none; a slur that starts one is in [BLOCKED_WORDS] instead. A false
     * hit costs a player their own name for a generated nickname, or a room its name.
     */
    private val BLOCKED_STEMS: List<String> =
        listOf(
            // Serbian, Croatian, Bosnian and Montenegrin alike; Slovenian and Macedonian say most of them too
            "jeb",
            "kurac",
            "kurc",
            "picka",
            "pick",
            "pizd",
            "govn",
            "sranj",
            "peder",
            "kurv",
            "drolj",
            "supak", // šupak
            "supcin", // šupčina
            "seronj", // seronja
            // ...and their ethnic slurs, for Albanians, Jews, black people and Bosniaks (balija is a whole word)
            "siptar",
            "cifut",
            "civut",
            "crncug", // crnčuga
            "balijetin",
            // Bosnian: a Turkism, which Serbian and Macedonian say too
            "pezeven", // pezevenk
            // Croatian and Slovenian
            "buzeran", // buzerant
            // Slovenian
            "fuka", // fukati, fukač
            "cefur", // čefur, for those from the other republics
            // Macedonian
            "ebam", // ебам
            "ebac", // ебач
            "ebenj", // ебење
            "ebig", // ебига
            "kurot", // курот
            // English
            "fuck",
            "shit",
            "cunt",
            "nigg",
            "fagg",
            "whore",
            "bitch",
            "retard",
            // Nazism's and the wars' names, in every one of the languages
            "hitler",
            "nazis",
            "naziz", // nazizam
            "nacis", // nacist, nacista
            "naciz", // nacizam, nacizem
            "siegheil",
            "cetnik",
            "ustas",
        )

    /**
     * Whole words, folded, for slurs that start ordinary words or names: kurba but not Kurban or Kurbegović, nazi
     * but not naziv, Nazif or Nazim.
     */
    private val BLOCKED_WORDS: Set<String> =
        setOf(
            // Serbian, Croatian, Bosnian, Montenegrin and Macedonian: kučka (and Croatian kucka, taps, with it)
            "kucka",
            "kucko",
            // ...and the slur for Bosniaks, not Balijagić
            "balija",
            "balije",
            "baliji",
            "balijo",
            "baliju",
            // Croatian and Slovenian: not Kurban, Kurbalija, Kurbegović, or Курбе (Courbet)
            "kurba",
            "kurbo",
            // Slovenian
            "kurec",
            "drek", // not dreka, a din in Serbian
            "buzi",
            "fuk",
            // Nazism
            "nazi",
            "sieg",
        )
}
