package io.ntole.kvizic.server.question

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.question.AdminQuestionPageDto
import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.ImportQuestionsRequest
import io.ntole.kvizic.core.question.ImportResultDto
import io.ntole.kvizic.core.question.LocalizeQuestionsRequest
import io.ntole.kvizic.core.question.LocalizeResultDto
import io.ntole.kvizic.core.question.QuestionDraftDto
import io.ntole.kvizic.core.question.QuestionLocalizationDto
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.core.question.QuestionTextDto
import io.ntole.kvizic.core.region.Country
import io.ntole.kvizic.server.FLOW_ADMIN_TOKEN
import io.ntole.kvizic.server.runTestServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Countries and translations into the bank over HTTP: an import's, and a localization's of what is there. */
class QuestionRegionsFlowTest {
    private val json = Json { encodeDefaults = true }

    private fun words(
        tag: String,
        key: String,
    ) = QuestionTextDto("Pitanje $tag $key?", listOf("Točno $tag $key", "Netočno $tag $key"))

    private fun draft(
        key: String,
        countries: List<Country> = emptyList(),
        translations: Map<String, QuestionTextDto> = emptyMap(),
    ) = QuestionDraftDto(
        key = key,
        text = "Питање $key?",
        options = listOf("Тачно $key", "Нетачно $key"),
        correct = 0,
        topics = listOf("GEOGRAPHY"),
        difficulty = Difficulty.EASY,
        countries = countries,
        translations = translations,
    )

    /** [body] as it is written: JSON this build's types could never write, such as a country it does not know. */
    private suspend fun HttpClient.admin(
        path: String,
        body: JsonObject,
    ): HttpResponse =
        post(path) {
            header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN)
            setBody(TextContent(body.toString(), ContentType.Application.Json))
        }

    private suspend fun HttpClient.importing(vararg drafts: JsonObject): HttpResponse =
        admin(
            KvizicApi.Paths.ADMIN_QUESTION_IMPORTS,
            buildJsonObject {
                put("batch", "b")
                put("questions", JsonArray(drafts.toList()))
            },
        )

    private suspend fun HttpClient.localizing(vararg questions: JsonObject): HttpResponse =
        admin(
            KvizicApi.Paths.ADMIN_QUESTION_LOCALIZATIONS,
            buildJsonObject { put("questions", JsonArray(questions.toList())) },
        )

    private fun QuestionDraftDto.written() = json.encodeToJsonElement(QuestionDraftDto.serializer(), this).jsonObject

    private fun QuestionLocalizationDto.written() =
        json.encodeToJsonElement(QuestionLocalizationDto.serializer(), this).jsonObject

    /** [this] with its countries named as [names] are, whatever they are. */
    private fun JsonObject.withCountries(vararg names: String) =
        JsonObject(this + ("countries" to JsonArray(names.map(::JsonPrimitive))))

    private suspend fun HttpClient.exported(): List<QuestionDraftDto> =
        get(KvizicApi.Paths.ADMIN_QUESTION_EXPORTS) {
            header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN)
            parameter(KvizicApi.Query.STATUS, QuestionStatus.DRAFT.name)
        }.body<ImportQuestionsRequest>().questions

    private suspend fun HttpClient.ids(): Map<String, String> =
        get(KvizicApi.Paths.ADMIN_QUESTIONS) { header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN) }
            .body<AdminQuestionPageDto>()
            .questions
            .associate { checkNotNull(it.importKey) to it.id }

    @Test
    fun `an import takes countries and translations, and refuses alone a draft naming a country it does not know`() =
        runTestServer("bank-regions-import") { client, _ ->
            val result =
                client
                    .importing(
                        draft(
                            "a",
                            listOf(Country.HR),
                            mapOf("hr" to words("hr", "a"), "bs" to words("bs", "a")),
                        ).written(),
                        draft("b").written().withCountries("RS", "XK"),
                        buildJsonObject { put("key", "c") },
                        draft("d").written(),
                    ).body<ImportResultDto>()
            assertEquals(listOf("a", "d"), result.created)
            assertEquals(listOf("b", "c"), result.refused.map { it.key })
            assertTrue(result.refused.all { it.reason.startsWith("unreadable: ") }, result.refused.toString())
            assertTrue("XK" in result.refused.first().reason, result.refused.first().reason)

            val a = client.exported().single { it.key == "a" }
            assertEquals(listOf(Country.HR), a.countries)
            assertEquals(mapOf("hr" to words("hr", "a"), "bs" to words("bs", "a")), a.translations)
        }

    @Test
    fun `a localization sets what it names, and says what it updated, did not know and refused`() =
        runTestServer("bank-regions-localize") { client, _ ->
            client.importing(draft("a").written(), draft("b").written(), draft("c").written())
            val idOfB = client.ids().getValue("b")

            val result =
                client
                    .localizing(
                        QuestionLocalizationDto("a", listOf(Country.SI), mapOf("sl" to words("sl", "a"))).written(),
                        QuestionLocalizationDto(idOfB, translations = mapOf("mk" to words("mk", "b"))).written(),
                        QuestionLocalizationDto("nope", listOf(Country.SI)).written(),
                        QuestionLocalizationDto("c", translations = mapOf("en" to words("en", "c"))).written(),
                        QuestionLocalizationDto("c").written().withCountries("XK"),
                    ).body<LocalizeResultDto>()
            assertEquals(listOf("a", idOfB), result.updated)
            assertEquals(listOf("nope"), result.unknown)
            assertEquals(listOf("c", "c"), result.refused.map { it.key })
            assertTrue(
                result.refused.any { it.reason == "translation en: no such language" },
                result.refused.toString(),
            )
            assertTrue(result.refused.any { "XK" in it.reason }, result.refused.toString())

            val bank = client.exported().associateBy { it.key }
            assertEquals(listOf(Country.SI), bank.getValue("a").countries)
            assertEquals(mapOf("sl" to words("sl", "a")), bank.getValue("a").translations)
            assertEquals(mapOf("mk" to words("mk", "b")), bank.getValue("b").translations)
            assertEquals(draft("c"), bank.getValue("c"), "refused, so as it was")
        }

    @Test
    fun `a localization over the batch, or without the admin token, is refused whole`() =
        runTestServer("bank-regions-localize-limits") { client, _ ->
            client.importing(draft("a").written())
            val tooMany = (1..26).map { QuestionLocalizationDto("a", listOf(Country.SI)).written() }
            assertEquals(HttpStatusCode.BadRequest, client.localizing(*tooMany.toTypedArray()).status)
            val noToken =
                client.post(KvizicApi.Paths.ADMIN_QUESTION_LOCALIZATIONS) {
                    setBody(
                        TextContent(
                            json.encodeToString(
                                LocalizeQuestionsRequest.serializer(),
                                LocalizeQuestionsRequest(listOf(QuestionLocalizationDto("a", listOf(Country.SI)))),
                            ),
                            ContentType.Application.Json,
                        ),
                    )
                }
            assertEquals(HttpStatusCode.Forbidden, noToken.status)
            assertEquals(emptyList(), client.exported().single().countries)
        }
}
