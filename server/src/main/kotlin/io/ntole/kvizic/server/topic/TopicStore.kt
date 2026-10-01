package io.ntole.kvizic.server.topic

import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.core.topic.TopicDto
import io.ntole.kvizic.core.topic.TopicGroupDto
import io.ntole.kvizic.server.db.QuestionTopics
import io.ntole.kvizic.server.db.Questions
import io.ntole.kvizic.server.db.TopicGroups
import io.ntole.kvizic.server.db.Topics
import io.ntole.kvizic.server.plugins.ApiFailure
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** The topics questions are filed under, in the order they were added. Never deleted. */
object TopicStore {
    /** Every topic, oldest first, with how many approved questions it has. Must run inside a transaction. */
    fun all(): List<TopicDto> {
        val approved = QuestionTopics.questionId.count()
        val counts =
            QuestionTopics
                .join(Questions, JoinType.INNER, QuestionTopics.questionId, Questions.id)
                .select(QuestionTopics.topicId, approved)
                .where { Questions.status eq QuestionStatus.APPROVED }
                .groupBy(QuestionTopics.topicId)
                .associate { row -> row[QuestionTopics.topicId] to row[approved].toInt() }
        return Topics
            .selectAll()
            .orderBy(Topics.createdAt to SortOrder.ASC, Topics.id to SortOrder.ASC)
            .map { row ->
                TopicDto(
                    id = row[Topics.id],
                    nameSr = row[Topics.nameSr],
                    nameEn = row[Topics.nameEn],
                    questionCount = counts[row[Topics.id]] ?: 0,
                    groupId = row[Topics.groupId],
                )
            }
    }

    /** Every group topics are listed under, in its order. Must run inside a transaction. */
    fun groups(): List<TopicGroupDto> =
        TopicGroups
            .selectAll()
            .orderBy(TopicGroups.listOrder to SortOrder.ASC, TopicGroups.id to SortOrder.ASC)
            .map { row -> TopicGroupDto(row[TopicGroups.id], row[TopicGroups.nameSr], row[TopicGroups.nameEn]) }

    /** Every topic's id. Must run inside a transaction. */
    fun ids(): Set<String> = Topics.select(Topics.id).map { it[Topics.id] }.toSet()

    /**
     * Adds a topic. An id a topic has already is [ApiFailure.topicExists]: the read spares a certain
     * violation, and the primary key decides two creations racing, the second's rerun finding the first.
     * Must run inside a transaction.
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
        return TopicDto(topic.id, topic.nameSr, topic.nameEn, questionCount = 0)
    }

    /** Sets both of a topic's names; its id never changes. Must run inside a transaction. */
    fun rename(topic: CheckedTopic): TopicDto {
        val updated =
            Topics.update({ Topics.id eq topic.id }) { row ->
                row[nameSr] = topic.nameSr
                row[nameEn] = topic.nameEn
            }
        if (updated == 0) throw ApiFailure.topicNotFound(topic.id)
        return all().first { it.id == topic.id }
    }
}
