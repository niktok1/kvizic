package io.ntole.kvizic.core.data.moderation

import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.domain.moderation.QuestionDifficulty
import io.ntole.kvizic.core.domain.moderation.QuestionRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The domain's copies of the wire's question limits, which the moderation app checks an edit by. */
class QuestionRulesLimitsTest {
    @Test
    fun `the rules' numbers are the wire's`() {
        assertEquals(KvizicApi.Limits.MAX_QUESTION_TEXT_LENGTH, QuestionRules.MAX_TEXT_LENGTH)
        assertEquals(KvizicApi.Limits.MAX_OPTION_LENGTH, QuestionRules.MAX_OPTION_LENGTH)
        assertEquals(KvizicApi.Limits.MIN_OPTIONS, QuestionRules.MIN_OPTIONS)
        assertEquals(KvizicApi.Limits.MAX_OPTIONS, QuestionRules.MAX_OPTIONS)
        assertEquals(KvizicApi.Limits.MAX_TOPICS_PER_QUESTION, QuestionRules.MAX_TOPICS)
        assertEquals(KvizicApi.Limits.MAX_EXPLANATION_LENGTH, QuestionRules.MAX_EXPLANATION_LENGTH)
        assertEquals(KvizicApi.Limits.MAX_SOURCE_URL_LENGTH, QuestionRules.MAX_SOURCE_LENGTH)
        assertEquals(KvizicApi.Limits.MAX_REASON_LENGTH, QuestionRules.MAX_REASON_LENGTH)
    }

    @Test
    fun `a question the server takes passes and each broken rule is named`() {
        fun problem(
            text: String = "Која река?",
            options: List<String> = listOf("Дунав", "Сава"),
            correct: Int = 0,
            topics: List<String> = listOf("GEOGRAPHY"),
            difficulty: QuestionDifficulty = QuestionDifficulty.EASY,
            source: String? = "https://example.org",
        ) = QuestionRules.problemOf(text, options, correct, topics, difficulty, explanation = null, source = source)

        assertNull(problem())
        assertNull(problem(text = "К".repeat(KvizicApi.Limits.MAX_QUESTION_TEXT_LENGTH), source = " "))
        assertNotNull(problem(text = "К".repeat(KvizicApi.Limits.MAX_QUESTION_TEXT_LENGTH + 1)))
        assertNotNull(problem(text = "Два\nреда"))
        assertNotNull(problem(options = listOf("Дунав")))
        assertNotNull(problem(options = listOf("Дунав", " дунав")))
        assertNotNull(problem(options = listOf("Дунав", "С".repeat(KvizicApi.Limits.MAX_OPTION_LENGTH + 1))))
        assertNotNull(problem(correct = 2))
        assertNotNull(problem(topics = emptyList()))
        assertNotNull(problem(difficulty = QuestionDifficulty.OTHER))
        assertNotNull(problem(source = "example.org"))
        assertNotNull(QuestionRules.reasonProblem(" "))
        assertNull(QuestionRules.reasonProblem("Два одговора су тачна."))
    }
}
