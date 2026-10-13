package io.ntole.kvizic.language

import kotlin.test.Test
import kotlin.test.assertEquals

/** Each language's rule picks a counted word's form as CLDR's examples have it. */
class PluralTest {
    private val word = Plural(one = "one {0}", two = "two {0}", few = "few {0}", many = "many {0}")

    @Test
    fun `Serbian Croatian and Bosnian count one few and many`() {
        val forms =
            mapOf(
                0 to "many",
                1 to "one",
                2 to "few",
                3 to "few",
                5 to "many",
                11 to "many",
                21 to "one",
                101 to "one",
                102 to "few",
                103 to "few",
                111 to "many",
            )
        listOf(Language.SERBIAN_CYRILLIC, Language.SERBIAN_LATIN, Language.CROATIAN, Language.BOSNIAN).forEach {
            assertForms(forms, it)
        }
    }

    @Test
    fun `Slovenian counts one two few and many by the last two digits`() {
        val forms =
            mapOf(
                0 to "many",
                1 to "one",
                2 to "two",
                3 to "few",
                5 to "many",
                11 to "many",
                21 to "many",
                101 to "one",
                102 to "two",
                103 to "few",
                111 to "many",
            )
        assertForms(forms, Language.SLOVENIAN)
    }

    @Test
    fun `Macedonian counts one for a last digit of one but not for eleven`() {
        val forms =
            mapOf(
                0 to "many",
                1 to "one",
                2 to "many",
                3 to "many",
                5 to "many",
                11 to "many",
                21 to "one",
                101 to "one",
                102 to "many",
                103 to "many",
                111 to "many",
            )
        assertForms(forms, Language.MACEDONIAN)
    }

    @Test
    fun `English counts one and many`() {
        val forms = listOf(0, 2, 3, 5, 11, 21, 101, 102, 103, 111).associateWith { "many" } + (1 to "one")
        assertForms(forms, Language.ENGLISH)
    }

    /** Serbian's two is its few, and English's its many, so neither reads a form it has not written. */
    @Test
    fun `Serbian and English write their two as the form their rule picks for two`() {
        listOf(SerbianCyrillicStrings, EnglishStrings).forEach { strings ->
            val language = if (strings === EnglishStrings) Language.ENGLISH else Language.SERBIAN_CYRILLIC
            val words = strings.game
            listOf(words.games, words.wins, words.questionCount, words.topicCount, words.seatCount).forEach {
                assertEquals(it.of(2, language), it.two.fill(2), "$language: $it")
            }
        }
    }

    private fun assertForms(
        forms: Map<Int, String>,
        language: Language,
    ) {
        forms.forEach { (count, form) -> assertEquals("$form $count", word.of(count, language), "$language: $count") }
    }
}
