package io.ntole.kvizic.server.topic

import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.core.region.ContentLanguages
import io.ntole.kvizic.core.region.Country
import io.ntole.kvizic.core.topic.TopicDto
import io.ntole.kvizic.core.topic.TopicGroupDto
import io.ntole.kvizic.server.db.QuestionCountries
import io.ntole.kvizic.server.db.QuestionTopics
import io.ntole.kvizic.server.db.Questions
import io.ntole.kvizic.server.db.TopicGroupNames
import io.ntole.kvizic.server.db.TopicGroups
import io.ntole.kvizic.server.db.TopicNames
import io.ntole.kvizic.server.db.Topics
import io.ntole.kvizic.server.plugins.ApiFailure
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/**
 * The topics questions are filed under, in the order they were added, and the groups a picker lists them
 * under. Never deleted.
 *
 * A topic's and a group's [TopicDto.names] are one map by tag, so a client reads one: Serbian Cyrillic and
 * English from the row's own two columns, which the moderator renames, and every other content language
 * from [TopicNames] and [TopicGroupNames], which migrations write: a row there in Serbian or English is
 * never read, and a tag that is not a content language never served. A topic the moderator adds has its two
 * names alone until a migration gives it the rest, there being no topics tab to write them from yet.
 */
object TopicStore {
    /** The tag a topic's English name is served under, beside the content languages' (`ContentLanguages`). */
    const val ENGLISH = "en"

    /**
     * Every topic, oldest first, with its names and how many approved questions it has: those a solo player
     * in [country] could be asked ([askableIn]), or for no country every one, as the moderator's overview
     * counts the bank. Three queries however many topics there are. Must run inside a transaction.
     */
    fun all(country: Country? = null): List<TopicDto> {
        val counts = approvedCounts(country)
        val translated = translations(TopicNames, TopicNames.topicId, TopicNames.language, TopicNames.name)
        return Topics
            .selectAll()
            .orderBy(Topics.createdAt to SortOrder.ASC, Topics.id to SortOrder.ASC)
            .map { row ->
                val id = row[Topics.id]
                TopicDto(
                    id = id,
                    nameSr = row[Topics.nameSr],
                    nameEn = row[Topics.nameEn],
                    questionCount = counts[id] ?: 0,
                    groupId = row[Topics.groupId],
                    names = names(row[Topics.nameSr], row[Topics.nameEn], translated[id].orEmpty()),
                )
            }
    }

    /** Every group topics are listed under, in its order, with its names. Must run inside a transaction. */
    fun groups(): List<TopicGroupDto> {
        val translated =
            translations(TopicGroupNames, TopicGroupNames.groupId, TopicGroupNames.language, TopicGroupNames.name)
        return TopicGroups
            .selectAll()
            .orderBy(TopicGroups.listOrder to SortOrder.ASC, TopicGroups.id to SortOrder.ASC)
            .map { row ->
                val id = row[TopicGroups.id]
                TopicGroupDto(
                    id = id,
                    nameSr = row[TopicGroups.nameSr],
                    nameEn = row[TopicGroups.nameEn],
                    names = names(row[TopicGroups.nameSr], row[TopicGroups.nameEn], translated[id].orEmpty()),
                )
            }
    }

    /** Every topic's id. Must run inside a transaction. */
    fun ids(): Set<String> = Topics.select(Topics.id).map { it[Topics.id] }.toSet()

    /**
     * Adds a topic, with its Serbian and English names alone (see [TopicStore]). An id a topic has already is
     * [ApiFailure.topicExists]: the read spares a certain violation, and the primary key decides two creations
     * racing, the second's rerun finding the first. Must run inside a transaction.
     */
    fun create(
        topic: CheckedTopic,
        now: Long = System.currentTimeMillis(),
    ): TopicDto {
        if (Topics.select(Topics.id).where { Topics.id eq topic.id }.any()) throw ApiFailure.topicExists(topic.id)
        Topics.insert { row ->
            row[id] = topic.id
            row[nameSr] = topic.nameSr
            row[nameEn] = topic.nameEn
            row[createdAt] = now
        }
        return TopicDto(
            topic.id,
            topic.nameSr,
            topic.nameEn,
            questionCount = 0,
            names = names(topic.nameSr, topic.nameEn, emptyMap()),
        )
    }

    /**
     * Sets both of a topic's names, its Serbian and English ones; its id never changes, nor its names in the
     * other languages. Must run inside a transaction.
     */
    fun rename(topic: CheckedTopic): TopicDto {
        val updated =
            Topics.update({ Topics.id eq topic.id }) { row ->
                row[nameSr] = topic.nameSr
                row[nameEn] = topic.nameEn
            }
        if (updated == 0) throw ApiFailure.topicNotFound(topic.id)
        return all().first { it.id == topic.id }
    }

    /**
     * Whether a question a solo player in [country] could be asked: a common one, scoped to no country, or one
     * whose countries include [country]. [Country.UNKNOWN], a country no question is scoped to, gets the common
     * ones alone. Each test is a look-up in the countries' primary key.
     */
    private fun askableIn(country: Country): Op<Boolean> {
        val common =
            notExists(
                QuestionCountries
                    .select(QuestionCountries.questionId)
                    .where { QuestionCountries.questionId eq Questions.id },
            )
        if (country == Country.UNKNOWN) return common
        return common or
            exists(
                QuestionCountries
                    .select(QuestionCountries.questionId)
                    .where {
                        (QuestionCountries.questionId eq Questions.id) and (QuestionCountries.country eq country)
                    },
            )
    }

    /** How many approved questions each topic has, of those [country] could be asked when there is one. */
    private fun approvedCounts(country: Country?): Map<String, Int> {
        val approved = QuestionTopics.questionId.count()
        val approvedOnly: Op<Boolean> = Questions.status eq QuestionStatus.APPROVED
        val counted = if (country == null) approvedOnly else approvedOnly and askableIn(country)
        return QuestionTopics
            .join(Questions, JoinType.INNER, QuestionTopics.questionId, Questions.id)
            .select(QuestionTopics.topicId, approved)
            .where { counted }
            .groupBy(QuestionTopics.topicId)
            .associate { row -> row[QuestionTopics.topicId] to row[approved].toInt() }
    }

    /** Every name [table] holds, by its owner's id and then by tag, in one query. */
    private fun translations(
        table: Table,
        owner: Column<String>,
        language: Column<String>,
        name: Column<String>,
    ): Map<String, Map<String, String>> =
        buildMap<String, MutableMap<String, String>> {
            table.select(owner, language, name).forEach { row ->
                getOrPut(row[owner]) { mutableMapOf() }[row[language]] = row[name]
            }
        }

    /**
     * One map by tag, in `ContentLanguages.ALL`'s order and English last: [nameSr] and [nameEn], the row's own,
     * and of [translated] each content language's but Serbian.
     */
    private fun names(
        nameSr: String,
        nameEn: String,
        translated: Map<String, String>,
    ): Map<String, String> =
        buildMap {
            put(ContentLanguages.SERBIAN, nameSr)
            ContentLanguages.ALL.forEach { tag -> translated[tag]?.let { putIfAbsent(tag, it) } }
            put(ENGLISH, nameEn)
        }
}
