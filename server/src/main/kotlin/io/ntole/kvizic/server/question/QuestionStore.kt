package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.question.AdminQuestionDto
import io.ntole.kvizic.core.question.AdminQuestionPageDto
import io.ntole.kvizic.core.question.EditQuestionRequest
import io.ntole.kvizic.core.question.ImportRefusalDto
import io.ntole.kvizic.core.question.ImportResultDto
import io.ntole.kvizic.core.question.QuestionDraftDto
import io.ntole.kvizic.core.question.QuestionStatsDto
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.server.db.QuestionOptions
import io.ntole.kvizic.server.db.QuestionStats
import io.ntole.kvizic.server.db.QuestionTopics
import io.ntole.kvizic.server.db.Questions
import io.ntole.kvizic.server.db.Reports
import io.ntole.kvizic.server.db.Topics
import io.ntole.kvizic.server.plugins.ApiFailure
import io.ntole.kvizic.server.report.ReportStatus
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

/**
 * The question bank as the moderator works it: imports, the list, edits and every move between
 * statuses. Each must run inside a transaction. An edit is a compare-and-set on the revision it was made
 * from, and a move one on the statuses it may start from, so two moderators never undo each other.
 */
object QuestionStore {
    /**
     * Imports [drafts] as [status], each checked by the rules: a key the bank has already, or one earlier
     * in the same import, is a duplicate and writes nothing. Two imports of one key racing fail on its
     * unique index, and the rerun finds it.
     */
    fun import(
        batch: String,
        drafts: List<QuestionDraftDto>,
        status: QuestionStatus,
        knownTopics: Set<String>,
        now: Long,
    ): ImportResultDto {
        val keys = drafts.map { it.key }.filter(::isImportKey)
        val existing =
            if (keys.isEmpty()) {
                mutableSetOf()
            } else {
                Questions
                    .select(Questions.importKey)
                    .where { Questions.importKey inList keys }
                    .mapNotNull { it[Questions.importKey] }
                    .toMutableSet()
            }
        val created = mutableListOf<String>()
        val duplicates = mutableListOf<String>()
        val refused = mutableListOf<ImportRefusalDto>()
        drafts.forEach { draft ->
            if (!isImportKey(draft.key)) {
                refused += ImportRefusalDto(draft.key.take(MAX_SHOWN_KEY), "the key is no import key")
                return@forEach
            }
            if (draft.key in existing) {
                duplicates += draft.key
                return@forEach
            }
            val checked =
                try {
                    checkedQuestion(
                        text = draft.text,
                        options = draft.options,
                        correct = draft.correct,
                        topics = draft.topics,
                        difficulty = draft.difficulty,
                        kind = draft.kind,
                        explanation = draft.explanation,
                        source = draft.source,
                        language = draft.language,
                        author = draft.author,
                        knownTopics = knownTopics,
                    )
                } catch (refusal: QuestionRefused) {
                    refused += ImportRefusalDto(draft.key, refusal.message)
                    return@forEach
                }
            insert(checked, status, batch, draft.key, now)
            existing += draft.key
            created += draft.key
        }
        return ImportResultDto(created, duplicates, refused)
    }

    /**
     * A page of the bank, newest first, narrowed to [statuses] and [topics] (none for all) and to
     * [search], matched in the text and the answers whatever the case; from [cursor], the page before's
     * last question, for the next.
     */
    fun page(
        statuses: Set<QuestionStatus>,
        topics: Set<String>,
        search: String?,
        cursor: QuestionCursor?,
        limit: Int,
    ): AdminQuestionPageDto {
        var condition: Op<Boolean> = Op.TRUE
        if (statuses.isNotEmpty()) condition = condition and (Questions.status inList statuses)
        if (topics.isNotEmpty()) {
            condition = condition and
                exists(
                    QuestionTopics
                        .select(QuestionTopics.questionId)
                        .where {
                            (QuestionTopics.questionId eq Questions.id) and (QuestionTopics.topicId inList topics)
                        },
                )
        }
        search?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { term ->
            val pattern = LikePattern("%", escapeChar = '\\') + LikePattern.ofLiteral(term) + "%"
            condition = condition and
                (
                    (Questions.text.lowerCase() like pattern) or
                        exists(
                            QuestionOptions
                                .select(QuestionOptions.questionId)
                                .where {
                                    (QuestionOptions.questionId eq Questions.id) and
                                        (QuestionOptions.text.lowerCase() like pattern)
                                },
                        )
                )
        }
        cursor?.let { after ->
            condition = condition and
                (
                    (Questions.createdAt less after.createdAt) or
                        ((Questions.createdAt eq after.createdAt) and (Questions.id less after.id))
                )
        }
        val rows =
            Questions
                .selectAll()
                .where { condition }
                .orderBy(Questions.createdAt to SortOrder.DESC, Questions.id to SortOrder.DESC)
                .limit(limit + 1)
                .toList()
        val page = rows.take(limit)
        val next =
            page
                .lastOrNull()
                ?.takeIf {
                    rows.size > limit
                }?.let { QuestionCursor(it[Questions.createdAt], it[Questions.id]).encode() }
        return AdminQuestionPageDto(dtosOf(page), next)
    }

    /** Question [id] as the moderator sees it, or null. */
    fun find(id: String): AdminQuestionDto? =
        dtosOf(Questions.selectAll().where { Questions.id eq id }.toList()).firstOrNull()

    /**
     * The moderator's edit, made from [EditQuestionRequest.revision]: refused as stale when another got
     * there first. New answers, or another right one, start the question's stats afresh.
     */
    fun edit(
        request: EditQuestionRequest,
        knownTopics: Set<String>,
        now: Long,
    ): AdminQuestionDto {
        val current =
            Questions.selectAll().where { Questions.id eq request.id }.firstOrNull()
                ?: throw ApiFailure.questionNotFound(request.id)
        val checked =
            try {
                checkedQuestion(
                    text = request.text,
                    options = request.options,
                    correct = request.correct,
                    topics = request.topics,
                    difficulty = request.difficulty,
                    kind = current[Questions.kind],
                    explanation = request.explanation,
                    source = request.source,
                    language = current[Questions.language],
                    author = current[Questions.author],
                    knownTopics = knownTopics,
                )
            } catch (refusal: QuestionRefused) {
                throw ApiFailure.invalidQuestion(refusal.message)
            }
        val updated =
            Questions.update({ (Questions.id eq request.id) and (Questions.revision eq request.revision) }) { row ->
                row[text] = checked.text
                row[correctSlot] = checked.correct
                row[difficulty] = checked.difficulty
                row[explanation] = checked.explanation
                row[sourceUrl] = checked.source
                row[revision] = revision + 1
                row[updatedAt] = now
            }
        if (updated == 0) throw ApiFailure.staleRevision(request.id)

        // The row is this transaction's now, so what it held before is read as it stood.
        val before = optionsOf(listOf(request.id))[request.id].orEmpty().map { it.second }
        val answersChanged = before != checked.options || current[Questions.correctSlot] != checked.correct
        QuestionOptions.deleteWhere { QuestionOptions.questionId eq request.id }
        insertOptions(request.id, checked.options)
        QuestionTopics.deleteWhere { QuestionTopics.questionId eq request.id }
        insertTopics(request.id, checked.topics)
        if (answersChanged) {
            QuestionStats.update({ QuestionStats.questionId eq request.id }) { row ->
                row[timesShown] = 0
                row[timesAnswered] = 0
                row[timesCorrect] = 0
                row[totalCorrectMs] = 0
            }
        }
        return checkNotNull(find(request.id)) { "question ${request.id} vanished mid-transaction" }
    }

    /**
     * Moves question [id] to [to], only from one of [from], as a compare-and-set: one moved meanwhile, or
     * standing elsewhere, is refused, and nothing changes.
     */
    fun move(
        id: String,
        from: Set<QuestionStatus>,
        to: QuestionStatus,
        reason: String?,
        now: Long,
    ): AdminQuestionDto {
        val updated =
            Questions.update({ (Questions.id eq id) and (Questions.status inList from) }) { row ->
                row[status] = to
                row[reviewedAt] = now
                row[rejectionReason] = reason
            }
        if (updated == 0) {
            if (Questions
                    .select(
                        Questions.id,
                    ).where { Questions.id eq id }
                    .empty()
            ) {
                throw ApiFailure.questionNotFound(id)
            }
            throw ApiFailure.wrongStatus(id, from.joinToString(" or "))
        }
        return checkNotNull(find(id)) { "question $id vanished mid-transaction" }
    }

    /** Every question at [statuses], oldest first, as drafts that import back as they are. */
    fun export(statuses: Set<QuestionStatus>): List<QuestionDraftDto> {
        val rows =
            Questions
                .selectAll()
                .where { Questions.status inList statuses }
                .orderBy(Questions.createdAt to SortOrder.ASC, Questions.id to SortOrder.ASC)
                .toList()
        return dtosOf(rows).map { question ->
            QuestionDraftDto(
                key = question.importKey ?: question.id,
                text = question.text,
                options = question.options,
                correct = question.correct,
                topics = question.topics,
                difficulty = question.difficulty,
                kind = question.kind,
                explanation = question.explanation,
                source = question.source,
                language = question.language,
                author = question.author,
            )
        }
    }

    private fun insert(
        checked: CheckedQuestion,
        status: QuestionStatus,
        batch: String,
        key: String,
        now: Long,
    ) {
        val id = UUID.randomUUID().toString()
        Questions.insert { row ->
            row[Questions.id] = id
            row[Questions.status] = status
            row[kind] = checked.kind
            row[text] = checked.text
            row[correctSlot] = checked.correct
            row[difficulty] = checked.difficulty
            row[explanation] = checked.explanation
            row[sourceUrl] = checked.source
            row[language] = checked.language
            row[author] = checked.author
            row[importBatch] = batch
            row[importKey] = key
            row[createdAt] = now
            row[updatedAt] = now
            row[reviewedAt] = if (status == QuestionStatus.DRAFT) null else now
        }
        insertOptions(id, checked.options)
        insertTopics(id, checked.topics)
        QuestionStats.insert { row -> row[questionId] = id }
    }

    private fun insertOptions(
        id: String,
        options: List<String>,
    ) {
        QuestionOptions.batchInsert(options.withIndex(), shouldReturnGeneratedValues = false) { (slot, option) ->
            this[QuestionOptions.questionId] = id
            this[QuestionOptions.slot] = slot
            this[QuestionOptions.text] = option
        }
    }

    private fun insertTopics(
        id: String,
        topics: List<String>,
    ) {
        QuestionTopics.batchInsert(topics, shouldReturnGeneratedValues = false) { topic ->
            this[QuestionTopics.questionId] = id
            this[QuestionTopics.topicId] = topic
        }
    }

    private fun optionsOf(ids: List<String>): Map<String, List<Pair<Int, String>>> =
        QuestionOptions
            .selectAll()
            .where { QuestionOptions.questionId inList ids }
            .orderBy(QuestionOptions.slot)
            .groupBy({ it[QuestionOptions.questionId] }, { it[QuestionOptions.slot] to it[QuestionOptions.text] })

    /** The moderator's view of [rows], everything of theirs read in one statement per table. */
    internal fun dtosOf(
        rows: List<ResultRow>,
        openReports: Map<String, Int>? = null,
    ): List<AdminQuestionDto> {
        val ids = rows.map { it[Questions.id] }
        if (ids.isEmpty()) return emptyList()
        val options = optionsOf(ids)
        val topics =
            QuestionTopics
                .join(Topics, JoinType.INNER, QuestionTopics.topicId, Topics.id)
                .select(QuestionTopics.questionId, QuestionTopics.topicId)
                .where { QuestionTopics.questionId inList ids }
                .orderBy(Topics.createdAt to SortOrder.ASC, Topics.id to SortOrder.ASC)
                .groupBy({ it[QuestionTopics.questionId] }, { it[QuestionTopics.topicId] })
        val stats =
            QuestionStats
                .selectAll()
                .where {
                    QuestionStats.questionId inList ids
                }.associateBy { it[QuestionStats.questionId] }
        val reports =
            openReports ?: run {
                val open = Reports.id.count()
                Reports
                    .select(Reports.questionId, open)
                    .where { (Reports.questionId inList ids) and (Reports.status eq ReportStatus.OPEN) }
                    .groupBy(Reports.questionId)
                    .associate { it[Reports.questionId] to it[open].toInt() }
            }
        return rows.map { row ->
            val id = row[Questions.id]
            val slots = options[id].orEmpty()
            val played = stats[id]
            AdminQuestionDto(
                id = id,
                status = row[Questions.status],
                kind = row[Questions.kind],
                text = row[Questions.text],
                options = slots.map { it.second },
                correct = slots.indexOfFirst { it.first == row[Questions.correctSlot] },
                topics = topics[id].orEmpty(),
                difficulty = row[Questions.difficulty],
                explanation = row[Questions.explanation],
                source = row[Questions.sourceUrl],
                language = row[Questions.language],
                author = row[Questions.author],
                importBatch = row[Questions.importBatch],
                importKey = row[Questions.importKey],
                revision = row[Questions.revision],
                createdAt = row[Questions.createdAt],
                updatedAt = row[Questions.updatedAt],
                reviewedAt = row[Questions.reviewedAt],
                rejectionReason = row[Questions.rejectionReason],
                stats =
                    played?.let {
                        val correct = it[QuestionStats.timesCorrect]
                        QuestionStatsDto(
                            shown = it[QuestionStats.timesShown],
                            answered = it[QuestionStats.timesAnswered],
                            correct = correct,
                            averageCorrectMs = if (correct > 0) it[QuestionStats.totalCorrectMs] / correct else null,
                        )
                    } ?: QuestionStatsDto(),
                openReports = reports[id] ?: 0,
            )
        }
    }

    /** How long a refused key is shown back: an import key's length, whatever was sent. */
    private const val MAX_SHOWN_KEY = 64
}
