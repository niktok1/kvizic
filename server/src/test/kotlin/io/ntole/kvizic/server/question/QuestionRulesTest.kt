package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.QuestionKind
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
    )

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

    @Test
    fun `the rules refuse what a player could not answer`() {
        assertTrue(refusal { check(correct = 4) }.contains("right answer"))
        assertTrue(refusal { check(options = listOf("Сиднеј", "сиднеј", "Перт", "Канбера")) }.contains("same"))
        assertTrue(refusal { check(text = "   ") }.contains("blank"))
        assertTrue(refusal { check(text = "а".repeat(201)) }.contains("over 200"))
        assertTrue(refusal { check(text = "Први ред\nдруги ред") }.contains("one line"))
        assertTrue(refusal { check(options = listOf("а", "б".repeat(81))) }.contains("over 80"))
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
    fun `an import key is visible ASCII of a bounded length`() {
        assertTrue(isImportKey("3f8175a5-d812-495b-8de9-ecd9282d3e41"))
        assertTrue(!isImportKey(""))
        assertTrue(!isImportKey("with space"))
        assertTrue(!isImportKey("ключ"))
        assertTrue(!isImportKey("k".repeat(65)))
    }
}
