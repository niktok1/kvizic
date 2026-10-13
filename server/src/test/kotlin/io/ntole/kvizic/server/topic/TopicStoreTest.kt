package io.ntole.kvizic.server.topic

import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.core.region.ContentLanguages
import io.ntole.kvizic.core.region.Country
import io.ntole.kvizic.server.db.QuestionCountries
import io.ntole.kvizic.server.db.TopicGroupNames
import io.ntole.kvizic.server.db.TopicGroups
import io.ntole.kvizic.server.db.TopicNames
import io.ntole.kvizic.server.db.Topics
import io.ntole.kvizic.server.db.appTables
import io.ntole.kvizic.server.db.connectH2
import io.ntole.kvizic.server.db.h2Url
import io.ntole.kvizic.server.question.BankFixtures
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.Connection
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** A topic's and a group's names in every language, and a topic's count of what a country could be asked. */
class TopicStoreTest {
    private val database =
        connectH2(h2Url("kvizic-topics-${UUID.randomUUID()}"), Connection.TRANSACTION_READ_COMMITTED)

    init {
        transaction(database) {
            SchemaUtils.create(*appTables)
            TopicGroups.insert { row ->
                row[id] = "G"
                row[nameSr] = "Група"
                row[nameEn] = "Group"
                row[listOrder] = 0
            }
            BankFixtures.topic("T", createdAt = 0)
            BankFixtures.topic("EMPTY", createdAt = 1)
            Topics.update({ Topics.id eq "T" }) { row ->
                row[nameSr] = "Тема"
                row[nameEn] = "Topic"
                row[groupId] = "G"
            }
        }
    }

    private fun scoped(
        id: String,
        vararg countries: Country,
        status: QuestionStatus = QuestionStatus.APPROVED,
    ) {
        BankFixtures.question(id, listOf("T"), status = status)
        countries.forEach { country ->
            QuestionCountries.insert { row ->
                row[questionId] = id
                row[QuestionCountries.country] = country
            }
        }
    }

    private fun count(country: Country?): Int = transaction(database) { TopicStore.all(country).first().questionCount }

    @Test
    fun `a topic and a group are named in every content language and English, Serbian first`() {
        transaction(database) {
            mapOf("hr" to "Tema hr", "bs" to "Tema bs", "sl" to "Tema sl", "mk" to "Тема мк").forEach { (tag, name) ->
                TopicNames.insert { row ->
                    row[topicId] = "T"
                    row[language] = tag
                    row[TopicNames.name] = name
                }
                TopicGroupNames.insert { row ->
                    row[groupId] = "G"
                    row[language] = tag
                    row[TopicGroupNames.name] = "Група $name"
                }
            }
        }

        val (topic, empty) = transaction(database) { TopicStore.all() }
        val group = transaction(database) { TopicStore.groups().single() }

        assertEquals(
            listOf(
                "sr-Cyrl" to "Тема",
                "hr" to "Tema hr",
                "bs" to "Tema bs",
                "sl" to "Tema sl",
                "mk" to "Тема мк",
                "en" to "Topic",
            ),
            topic.names.toList(),
        )
        assertEquals(ContentLanguages.ALL + TopicStore.ENGLISH, group.names.keys.toList())
        assertEquals("Група", group.names.getValue(ContentLanguages.SERBIAN))
        assertEquals("Група Тема мк", group.names.getValue(ContentLanguages.MACEDONIAN))
        // A topic with no names of its own yet, as one the moderator adds, still has its two.
        assertEquals(mapOf("sr-Cyrl" to "EMPTY", "en" to "EMPTY"), empty.names)
    }

    @Test
    fun `the row's own names win, and a tag that is no content language is never served`() {
        transaction(database) {
            listOf("sr-Cyrl" to "Стара", "en" to "Old", "sr-Latn" to "Tema", "de" to "Thema").forEach { (tag, name) ->
                TopicNames.insert { row ->
                    row[topicId] = "T"
                    row[language] = tag
                    row[TopicNames.name] = name
                }
            }
        }

        val names = transaction(database) { TopicStore.all().first().names }

        assertEquals(mapOf("sr-Cyrl" to "Тема", "en" to "Topic"), names)
    }

    @Test
    fun `a topic counts the common questions and those scoped to the caller's country`() {
        transaction(database) {
            scoped("common")
            scoped("rs", Country.RS)
            scoped("hr", Country.HR)
            scoped("hr-ba", Country.HR, Country.BA)
            scoped("hr-draft", Country.HR, status = QuestionStatus.DRAFT)
            scoped("common-retired", status = QuestionStatus.RETIRED)
        }

        assertEquals(2, count(Country.RS))
        assertEquals(3, count(Country.HR))
        assertEquals(2, count(Country.BA))
        assertEquals(1, count(Country.SI))
        // A newer client's country is one no question is scoped to.
        assertEquals(1, count(Country.UNKNOWN))
        // The moderator's overview counts the whole bank.
        assertEquals(4, count(null))
        assertEquals(0, transaction(database) { TopicStore.all(Country.HR).last().questionCount })
    }
}
