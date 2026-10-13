package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.region.ContentLanguages
import io.ntole.kvizic.core.region.ContentLanguages.BOSNIAN
import io.ntole.kvizic.core.region.ContentLanguages.CROATIAN
import io.ntole.kvizic.core.region.ContentLanguages.MACEDONIAN
import io.ntole.kvizic.core.region.ContentLanguages.SERBIAN
import io.ntole.kvizic.core.region.ContentLanguages.SLOVENIAN
import kotlin.test.Test
import kotlin.test.assertEquals

class GameQuestionTest {
    private val source = QuestionText("Питање извор", listOf("А извор", "Б извор", "В извор"), "Зато извор")

    private fun words(lang: String) =
        QuestionText("Питање $lang", listOf("А $lang", "Б $lang", "В $lang"), "Зато $lang")

    private fun question(vararg langs: String) =
        GameQuestion(
            questionId = "q1",
            text = source.text,
            options = source.options,
            correct = 1,
            topicId = "SPORT",
            explanation = source.explanation,
            texts = langs.associateWith(::words),
        )

    @Test
    fun `a reader of the source's language reads the source`() {
        val question = question(CROATIAN, BOSNIAN)
        assertEquals(source, question.textIn(SERBIAN))
        assertEquals(source, question.textIn("sr-Latn"), "Latin is made from the Cyrillic on the client")
        assertEquals(source, question.textIn(""), "an old client says no language")
    }

    @Test
    fun `a reader with a translation reads it`() {
        val question = question(CROATIAN, SLOVENIAN, MACEDONIAN)
        assertEquals(words(CROATIAN), question.textIn(CROATIAN))
        assertEquals(words(SLOVENIAN), question.textIn(SLOVENIAN))
        assertEquals(words(MACEDONIAN), question.textIn(MACEDONIAN))
    }

    @Test
    fun `a reader with none reads the nearest there is`() {
        val question = question(CROATIAN)
        assertEquals(words(CROATIAN), question.textIn(BOSNIAN))
        assertEquals(words(CROATIAN), question.textIn(SLOVENIAN))
        assertEquals(source, question.textIn(MACEDONIAN), "Macedonian looks to Serbian first")
        assertEquals(words(BOSNIAN), question(BOSNIAN).textIn(SLOVENIAN))
    }

    @Test
    fun `with no translation every reader reads the source`() {
        ContentLanguages.ALL.forEach { lang -> assertEquals(source, question().textIn(lang), lang) }
    }

    @Test
    fun `a source in another language is found where the chain names it`() {
        val croatian = question().copy(language = CROATIAN, texts = mapOf(SERBIAN to words(SERBIAN)))
        assertEquals(source, croatian.textIn(BOSNIAN), "Bosnian looks to Croatian first")
        assertEquals(words(SERBIAN), croatian.textIn(MACEDONIAN))
    }

    @Test
    fun `a translation with another number of answers is passed over`() {
        val stale = QuestionText("Питање hr", listOf("А hr", "Б hr", "В hr", "Г hr"), null)
        val question = question(BOSNIAN).let { it.copy(texts = it.texts + (CROATIAN to stale)) }
        assertEquals(words(BOSNIAN), question.textIn(CROATIAN))
    }
}
