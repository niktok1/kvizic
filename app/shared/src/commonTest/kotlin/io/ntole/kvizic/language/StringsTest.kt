package io.ntole.kvizic.language

import io.ntole.kvizic.core.domain.language.SerbianScript
import io.ntole.kvizic.core.domain.region.Country
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The game's words in each language. [Strings] is a data class, so its `toString` holds every text by
 * name: comparing those checks every text, the ones added later included, without a list of them here to
 * forget one from. A language still in [PENDING_LANGUAGES] is a placeholder of another's words, so only what
 * holds of every language holds of it: its letters' script, no blank text, the placeholders.
 */
class StringsTest {
    @Test
    fun `Serbian Latin is the Serbian Cyrillic transliterated text for text`() {
        assertEquals(SerbianScript.toLatin(SerbianCyrillicStrings.toString()), SerbianLatinStrings.toString())
    }

    @Test
    fun `every text of a language written in Cyrillic is in Cyrillic`() {
        Language.entries.filter { it.isCyrillic }.forEach { language ->
            textsOf(stringsOf(language)).forEach { text ->
                assertTrue(text.any(::isCyrillic), "$language: \"$text\" has no Cyrillic letter")
                assertFalse(text.any(::isLatin), "$language: \"$text\" has a Latin letter")
            }
        }
    }

    @Test
    fun `no text of a language written in Latin has a Cyrillic letter`() {
        Language.entries.filterNot { it.isCyrillic }.forEach { language ->
            val strings = stringsOf(language)
            assertFalse(strings.toString().any(::isCyrillic), "$language: $strings")
        }
    }

    /** Macedonian writes ѓ and ќ where Serbian writes ђ and ћ, which it has not. */
    @Test
    fun `Macedonian has no letter of Serbian's own`() {
        written(Language.MACEDONIAN)?.let { strings ->
            val serbian = strings.toString().filter { it in "ЂђЋћ" }
            assertEquals("", serbian, "Macedonian has Serbian's ${serbian.toSet()}")
        }
    }

    @Test
    fun `no text is blank in any language`() {
        Language.entries.forEach { language ->
            textsOf(stringsOf(language)).forEach { text -> assertTrue(text.isNotBlank(), "$language has a blank text") }
        }
    }

    @Test
    fun `each language has strings of its own`() {
        assertSame(SerbianCyrillicStrings, stringsOf(Language.SERBIAN_CYRILLIC))
        assertSame(SerbianLatinStrings, stringsOf(Language.SERBIAN_LATIN))
        assertSame(CroatianStrings, stringsOf(Language.CROATIAN))
        assertSame(BosnianStrings, stringsOf(Language.BOSNIAN))
        assertSame(SlovenianStrings, stringsOf(Language.SLOVENIAN))
        assertSame(MacedonianStrings, stringsOf(Language.MACEDONIAN))
        assertSame(EnglishStrings, stringsOf(Language.ENGLISH))
        Language.entries.forEach { language ->
            Language.entries.filter { it != language }.forEach { other ->
                assertTrue(stringsOf(other) !== stringsOf(language), "$language has $other's strings")
            }
        }
        // A language written has words of its own: a copy of another's would be a placeholder under its name.
        val written = Language.entries.filterNot { it in PENDING_LANGUAGES }
        written.forEach { language ->
            written.filter { it != language }.forEach { other ->
                assertNotEquals(stringsOf(other), stringsOf(language), "$language says what $other says")
            }
        }
    }

    /** A placeholder says what the language it stands in for says, so the game shows something whole in it. */
    @Test
    fun `a language not written yet has Serbian's words in its own script`() {
        PENDING_LANGUAGES.forEach { language ->
            val standIn = if (language.isCyrillic) SerbianCyrillicStrings else SerbianLatinStrings
            assertEquals(standIn, stringsOf(language), "$language")
        }
    }

    /** The name the game goes by, as the store and the launcher say it: its Latin with the ć, and Macedonian's ќ. */
    @Test
    fun `the game is Kvizic in every language`() {
        val names =
            mapOf(
                Language.SERBIAN_CYRILLIC to "Квизић",
                Language.SERBIAN_LATIN to "Kvizić",
                Language.CROATIAN to "Kvizić",
                Language.BOSNIAN to "Kvizić",
                Language.SLOVENIAN to "Kvizić",
                Language.MACEDONIAN to "Квизиќ",
                Language.ENGLISH to "Kvizić",
            )
        assertEquals(Language.entries.toSet(), names.keys, "a name for each language")
        names.forEach { (language, name) ->
            written(language)?.let { assertEquals(name, it.gameName, "$language") }
        }
    }

    /**
     * The button under a failure is one text on every screen, so the game says it one way, and the failure
     * that asks the player to try again asks in the button's words.
     */
    @Test
    fun `Try again is one text and the failure asking for it says it the same way`() {
        assertEquals("Покушај поново", SerbianCyrillicStrings.tryAgain)
        assertEquals("Pokušaj ponovo", SerbianLatinStrings.tryAgain)
        assertEquals("Try again", EnglishStrings.tryAgain)
        Language.entries.map(::stringsOf).forEach { strings ->
            val failure = strings.somethingWrong
            assertTrue(failure.endsWith("${strings.tryAgain}."), "\"$failure\" for \"${strings.tryAgain}\"")
        }
    }

    /**
     * A template's numbers and names go where its language puts them, but every language must have each of
     * them: a translation that dropped `{0}` would show a rate limit without its wait.
     */
    @Test
    fun `every template has the same placeholders in every language`() {
        val english = textsOf(EnglishStrings)
        Language.entries.forEach { language ->
            val texts = textsOf(stringsOf(language))
            assertEquals(english.size, texts.size, "$language")
            english.zip(texts).forEach { (source, text) ->
                assertEquals(placeholdersOf(source), placeholdersOf(text), "$language: \"$text\" for \"$source\"")
            }
        }
        assertTrue(english.any { placeholdersOf(it).isNotEmpty() }, "no template was checked")
    }

    /** Every country has its name in every language, and a country this build does not know none. */
    @Test
    fun `every country is named in every language`() {
        Language.entries.forEach { language ->
            val names = stringsOf(language).settingsScreen.countries
            Country.entries.forEach { country ->
                val name = names.of(country)
                if (country == Country.UNKNOWN) {
                    assertEquals(null, name, "$language")
                } else {
                    assertTrue(!name.isNullOrBlank(), "$language: $country")
                }
            }
        }
    }

    /** The strings of [language] once they are its own, or none while it is a placeholder. */
    private fun written(language: Language): Strings? = stringsOf(language).takeIf { language !in PENDING_LANGUAGES }

    /** Every text of [strings], as [Strings.map] visits them, which the first test holds to all of them. */
    private fun textsOf(strings: Strings): List<String> {
        val texts = mutableListOf<String>()
        strings.map { text -> text.also(texts::add) }
        return texts
    }

    private fun placeholdersOf(text: String): List<String> =
        PLACEHOLDER
            .findAll(text)
            .map { it.value }
            .sorted()
            .toList()

    private fun isCyrillic(char: Char): Boolean = char in 'Ѐ'..'ӿ'

    private fun isLatin(char: Char): Boolean = char in 'a'..'z' || char in 'A'..'Z'
}
