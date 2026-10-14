package io.ntole.kvizic.server.topic

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.region.ContentLanguages
import io.ntole.kvizic.core.region.Country
import io.ntole.kvizic.core.topic.TopicListDto
import io.ntole.kvizic.server.db.QuestionCountries
import io.ntole.kvizic.server.db.inTransaction
import io.ntole.kvizic.server.db.serverPool
import io.ntole.kvizic.server.question.BankFixtures
import io.ntole.kvizic.server.runTestServer
import org.jetbrains.exposed.v1.jdbc.insert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `GET /v1/topics` by the caller's country, and with every topic's and group's names. */
class TopicCountryFlowTest {
    private suspend fun HttpClient.topics(country: String? = null): HttpResponse =
        get(KvizicApi.Paths.TOPICS) { country?.let { header(KvizicApi.Headers.COUNTRY, it) } }

    private suspend fun HttpClient.geography(country: String? = null): Int =
        topics(country)
            .body<TopicListDto>()
            .topics
            .first { it.id == "GEOGRAPHY" }
            .questionCount

    @Test
    fun `a topic counts what the caller's country could be asked, and no header is Serbia`() =
        runTestServer("topics-by-country") { client, database ->
            assertEquals(0, client.geography())
            // One common question, one for Serbia and two for Croatia: every way to count gives its own number.
            database.serverPool().use { pool ->
                pool.inTransaction {
                    BankFixtures.question("common", listOf("GEOGRAPHY"))
                    listOf("rs" to Country.RS, "hr-1" to Country.HR, "hr-2" to Country.HR).forEach { (id, scope) ->
                        BankFixtures.question(id, listOf("GEOGRAPHY"))
                        QuestionCountries.insert { row ->
                            row[questionId] = id
                            row[country] = scope
                        }
                    }
                }
            }

            assertEquals(2, client.geography("RS"))
            // No header, or a blank one, is Serbia: neither Croatia's three nor the common one alone.
            assertEquals(2, client.geography())
            assertEquals(2, client.geography(""))
            assertEquals(2, client.geography(" "))
            assertEquals(3, client.geography("HR"))
            assertEquals(3, client.geography("hr"))
            assertEquals(1, client.geography("BA"))
            // A country this build does not know gets the common questions alone.
            assertEquals(1, client.geography("XK"))
            assertTrue(
                client
                    .topics("HR")
                    .headers
                    .getAll(HttpHeaders.Vary)
                    .orEmpty()
                    .any { KvizicApi.Headers.COUNTRY in it },
            )
        }

    /** Serbian and English from each row's own columns, and every other content language from V12. */
    @Test
    fun `every topic and group is named in Serbian and English from its row and in every other language`() =
        runTestServer("topics-names") { client, _ ->
            val list: TopicListDto = client.topics().body()

            (
                list.topics.map { Triple(it.names, it.nameSr, it.nameEn) } +
                    list.groups.map { Triple(it.names, it.nameSr, it.nameEn) }
            ).forEach { (names, sr, en) ->
                assertEquals(sr, names[ContentLanguages.SERBIAN])
                assertEquals(en, names[TopicStore.ENGLISH])
                assertEquals(ContentLanguages.ALL + TopicStore.ENGLISH, names.keys.toList(), sr)
            }
        }
}
