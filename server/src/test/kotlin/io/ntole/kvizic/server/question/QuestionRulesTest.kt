package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.QuestionKind
import io.ntole.kvizic.core.question.QuestionTextDto
import io.ntole.kvizic.core.region.ContentLanguages
import io.ntole.kvizic.core.region.Country
import io.ntole.kvizic.server.db.QuestionOptions
import io.ntole.kvizic.server.db.Questions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the bank takes as a question, and what it refuses. */
class QuestionRulesTest {
    private val topics = setOf("GEOGRAPHY", "SPORT")

    private fun check(
        text: String = "Који је главни град Аустралије?",
        options: List<String> = listOf("Сиднеј", "Мелбурн", "Канбера", "Перт"),
        correct: Int = 2,
        topicIds: List<String> = listOf("GEOGRAPHY"),
        difficulty: Difficulty = Difficulty.EASY,
        kind: QuestionKind = QuestionKind.CHOICE,
        explanation: String? = null,
        source: String? = "https://sr.wikipedia.org/wiki/Канбера",
        language: String = "sr-Cyrl",
        author: String = "claude",
        countries: List<Country> = emptyList(),
        translations: Map<String, QuestionTextDto> = emptyMap(),
    ) = checkedQuestion(
        text,
        options,
        correct,
        topicIds,
        difficulty,
        kind,
        explanation,
        source,
        language,
        author,
        topics,
        countries,
        translations,
    )

    /** A translation of the default question's four answers into [tag], or of [options]. */
    private fun words(
        tag: String,
        text: String = "Pitanje $tag?",
        options: List<String> = listOf("A $tag", "B $tag", "C $tag", "D $tag"),
        explanation: String? = null,
    ) = QuestionTextDto(text, options, explanation)

    private fun refusal(block: () -> Unit): String = assertFailsWith<QuestionRefused> { block() }.message

    @Test
    fun `a question is trimmed and its topics kept once each`() {
        val checked =
            check(
                text = "  Који?  ",
                options = listOf(" а ", "б"),
                correct = 0,
                topicIds = listOf("SPORT", "SPORT"),
                explanation = "  ",
            )
        assertEquals("Који?", checked.text)
        assertEquals(listOf("а", "б"), checked.options)
        assertEquals(listOf("SPORT"), checked.topics)
        assertNull(checked.explanation, "a blank explanation is none")
    }

    @Test
    fun `two, three and four answers are all questions, one or five are not`() {
        check(options = listOf("а", "б"), correct = 1)
        check(options = listOf("а", "б", "в"), correct = 2)
        assertTrue(refusal { check(options = listOf("а"), correct = 0) }.contains("2 to 4"))
        assertTrue(refusal { check(options = listOf("а", "б", "в", "г", "д"), correct = 0) }.contains("2 to 4"))
    }

    /** Read against the clock: the longest question reads in some seven seconds (`GameTimings.readTime`). */
    @Test
    fun `a question is at most 120 characters and an answer 60`() {
        assertEquals(120, check(text = "а".repeat(120)).text.length)
        assertEquals(60, check(options = listOf("а", "б".repeat(60)), correct = 0).options.last().length)
        assertTrue(KvizicApi.Limits.MAX_QUESTION_TEXT_LENGTH <= Questions.TEXT_COLUMN, "the question fits its column")
        assertTrue(KvizicApi.Limits.MAX_OPTION_LENGTH <= QuestionOptions.TEXT_COLUMN, "an answer fits its column")
        assertTrue(
            KvizicApi.Limits.MAX_EXPLANATION_LENGTH <= Questions.EXPLANATION_COLUMN,
            "an explanation fits its column",
        )
    }

    @Test
    fun `the rules refuse what a player could not answer`() {
        assertTrue(refusal { check(correct = 4) }.contains("right answer"))
        assertTrue(refusal { check(options = listOf("Сиднеј", "сиднеј", "Перт", "Канбера")) }.contains("same"))
        assertTrue(refusal { check(text = "   ") }.contains("blank"))
        assertTrue(refusal { check(text = "а".repeat(121)) }.contains("over 120"))
        assertTrue(refusal { check(text = "Први ред\nдруги ред") }.contains("one line"))
        assertTrue(refusal { check(options = listOf("а", "б".repeat(61))) }.contains("over 60"))
        assertTrue(refusal { check(topicIds = emptyList()) }.contains("1 to 3"))
        assertTrue(refusal { check(topicIds = listOf("COOKING")) }.contains("COOKING"))
        assertTrue(refusal { check(difficulty = Difficulty.UNKNOWN) }.contains("difficulty"))
        assertTrue(refusal { check(kind = QuestionKind.UNKNOWN) }.contains("kind"))
        assertTrue(refusal { check(kind = QuestionKind.TRUE_FALSE) }.contains("two answers"))
        assertTrue(refusal { check(source = "sr.wikipedia.org") }.contains("web address"))
        assertTrue(refusal { check(source = "https://a b") }.contains("web address"))
        assertTrue(refusal { check(language = "Serbian") }.contains("language"))
    }

    @Test
    fun `a true or false question has exactly its two answers`() {
        val checked = check(options = listOf("Тачно", "Нетачно"), correct = 0, kind = QuestionKind.TRUE_FALSE)
        assertEquals(QuestionKind.TRUE_FALSE, checked.kind)
    }

    @Test
    fun `a question is in a content language, never another tag`() {
        ContentLanguages.ALL.forEach { assertEquals(it, check(language = " $it ").language) }
        listOf("sr-Latn", "en", "sr", "HR", "").forEach { tag ->
            assertTrue(refusal { check(language = tag) }.startsWith("no language"), tag)
        }
    }

    @Test
    fun `a question's countries come in their order, none twice and none unknown`() {
        assertEquals(emptySet(), check().countries, "common")
        assertEquals(
            listOf(Country.RS, Country.BA, Country.ME),
            check(countries = listOf(Country.ME, Country.RS, Country.BA)).countries.toList(),
        )
        assertEquals("countries: HR twice", refusal { check(countries = listOf(Country.HR, Country.SI, Country.HR)) })
        assertEquals("countries: UNKNOWN is no country", refusal { check(countries = listOf(Country.UNKNOWN)) })
    }

    @Test
    fun `a translation is trimmed as its source is, and kept in the languages' order`() {
        val checked =
            check(
                explanation = "Објашњење.",
                translations =
                    mapOf(
                        "mk" to words("mk", explanation = "Објаснување."),
                        "hr" to words("hr", text = "  Pitanje hr?  ", explanation = " Objašnjenje. "),
                    ),
            )
        assertEquals(listOf("hr", "mk"), checked.translations.keys.toList())
        val hr = checked.translations.getValue("hr")
        assertEquals("Pitanje hr?", hr.text)
        assertEquals(listOf("A hr", "B hr", "C hr", "D hr"), hr.options)
        assertEquals("Objašnjenje.", hr.explanation)
        assertNull(check(translations = mapOf("sl" to words("sl", explanation = "  "))).translations["sl"]?.explanation)
    }

    @Test
    fun `a translation is refused for what its source would be, naming the language`() {
        fun refused(vararg translations: Pair<String, QuestionTextDto>) =
            refusal { check(translations = mapOf(*translations)) }

        assertEquals("translation en: no such language", refused("en" to words("en")))
        assertEquals("translation sr-Latn: no such language", refused("sr-Latn" to words("sr-Latn")))
        assertEquals("translation sr-Cyrl: the source's own language", refused("sr-Cyrl" to words("sr-Cyrl")))
        assertEquals("translation hr: the question is blank", refused("hr" to words("hr", text = " ")))
        assertEquals(
            "translation hr: the question is over 120 characters",
            refused("hr" to words("hr", text = "a".repeat(121))),
        )
        assertEquals(
            "translation bs: the question is not one line",
            refused("bs" to words("bs", text = "Prvi red\ndrugi red")),
        )
        assertEquals(
            "translation sl: answer 3 is over 60 characters",
            refused("sl" to words("sl", options = listOf("A", "B", "c".repeat(61), "D"))),
        )
        assertEquals(
            "translation mk: answer 2 is blank",
            refused("mk" to words("mk", options = listOf("A", " ", "C", "D"))),
        )
        assertEquals(
            "translation hr: 3 answers, not the source's 4",
            refused("hr" to words("hr", options = listOf("A", "B", "C"))),
        )
        assertEquals(
            "translation hr: two answers are the same",
            refused("hr" to words("hr", options = listOf("Isto", "B", "isto", "D"))),
        )
        assertEquals(
            "translation hr: an explanation, where the source has none",
            refused("hr" to words("hr", explanation = "Objašnjenje.")),
        )
        assertEquals(
            "translation hr: the explanation is over 160 characters",
            refusal {
                check(
                    explanation = "Објашњење.",
                    translations =
                        mapOf("hr" to words("hr", explanation = "e".repeat(161))),
                )
            },
        )
        assertEquals(
            "translation bs: no explanation, where the source has one",
            refusal { check(explanation = "Објашњење.", translations = mapOf("bs" to words("bs"))) },
        )
    }

    @Test
    fun `an import key is visible ASCII of a bounded length`() {
        assertTrue(isImportKey("3f8175a5-d812-495b-8de9-ecd9282d3e41"))
        assertTrue(!isImportKey(""))
        assertTrue(!isImportKey("with space"))
        assertTrue(!isImportKey("ключ"))
        assertTrue(!isImportKey("k".repeat(65)))
    }
}
