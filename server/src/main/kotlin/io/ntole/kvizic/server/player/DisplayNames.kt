package io.ntole.kvizic.server.player

import io.ntole.kvizic.core.api.KvizicApi
import java.text.Normalizer

/**
 * Cleans a name another service gave a player (their Play Games name) before strangers see it.
 *
 * Normalized to NFC; stripped of control and format characters, which is where the bidirectional
 * overrides and the zero-width characters that could disguise a name live, and of anything unassigned
 * or private; whitespace collapsed and trimmed; cut to [maxLength] code points, a player's name's by default. A name left blank, or holding a word from a short list of slurs and vulgarities, is refused
 * (null), and the player keeps their generated nickname instead.
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

    /** Whether [name] holds a word that starts with a blocked stem, compared folded to plain Latin. */
    internal fun isBlocked(name: String): Boolean =
        fold(name)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }
            .any { word -> BLOCKED_STEMS.any { stem -> word.startsWith(stem) } }

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

    /** Stems of Serbian and English slurs and vulgarities. A false hit only means a generated nickname. */
    private val BLOCKED_STEMS: List<String> =
        listOf(
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
            "fuck",
            "shit",
            "cunt",
            "nigg",
            "fagg",
            "whore",
            "bitch",
            "retard",
            "hitler",
            "nazi",
            "cetnik",
            "ustas",
            "sieg",
        )
}
