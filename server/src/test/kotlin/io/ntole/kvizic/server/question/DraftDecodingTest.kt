package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.question.ImportQuestionsRequest
import io.ntole.kvizic.core.question.LocalizeQuestionsRequest
import io.ntole.kvizic.core.question.QuestionDraftDto
import io.ntole.kvizic.core.question.QuestionLocalizationDto
import io.ntole.kvizic.core.region.Country
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A request's questions read one by one, so one this server cannot read is refused alone. */
class DraftDecodingTest {
    private fun fields(serializer: KSerializer<*>) = serializer.descriptor.elementNames.toList()

    @Test
    fun `the bodies read as they come are the requests field for field`() {
        assertEquals(fields(ImportQuestionsRequest.serializer()), fields(ImportBody.serializer()))
        assertEquals(fields(LocalizeQuestionsRequest.serializer()), fields(LocalizeBody.serializer()))
    }

    @Test
    fun `each is read on its own, and one that does not read is refused by its key`() {
        val decoded =
            listOf(
                buildJsonObject {
                    put("key", "a")
                    put("countries", buildJsonArray { add(JsonPrimitive("SI")) })
                },
                buildJsonObject {
                    put("key", "b")
                    put("countries", buildJsonArray { add(JsonPrimitive("XK")) })
                },
                JsonPrimitive("c"),
                JsonNull,
                buildJsonObject { put("key", "d") },
                buildJsonObject { put("countries", "SI") },
            ).decodedEach(QuestionLocalizationDto.serializer())
        assertEquals(
            listOf(QuestionLocalizationDto("a", listOf(Country.SI)), QuestionLocalizationDto("d")),
            decoded.items,
        )
        assertEquals(listOf("b", "", "", ""), decoded.refused.map { it.key })
        assertTrue(decoded.refused.all { it.reason.startsWith("unreadable: ") && it.reason.lines().size == 1 })
        assertTrue("XK" in decoded.refused.first().reason)
    }

    @Test
    fun `a key is shown back no longer than an import key can be`() {
        val decoded =
            listOf(buildJsonObject { put("key", "k".repeat(1000)) }).decodedEach(QuestionDraftDto.serializer())
        assertEquals("k".repeat(MAX_SHOWN_KEY), decoded.refused.single().key)
    }
}
