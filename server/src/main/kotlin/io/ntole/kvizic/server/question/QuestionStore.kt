package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.question.AdminQuestionDto
import io.ntole.kvizic.core.question.AdminQuestionPageDto
import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.EditQuestionRequest
import io.ntole.kvizic.core.question.ImportRefusalDto
import io.ntole.kvizic.core.question.ImportResultDto
import io.ntole.kvizic.core.question.QuestionDraftDto
import io.ntole.kvizic.core.question.QuestionLocalizationDto
import io.ntole.kvizic.core.question.QuestionStatsDto
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.core.question.QuestionTextDto
import io.ntole.kvizic.core.region.ContentLanguages
import io.ntole.kvizic.core.region.Country
import io.ntole.kvizic.server.db.QuestionCountries
import io.ntole.kvizic.server.db.QuestionOptionTranslations
import io.ntole.kvizic.server.db.QuestionOptions
import io.ntole.kvizic.server.db.QuestionStats
import io.ntole.kvizic.server.db.QuestionTopics
import io.ntole.kvizic.server.db.QuestionTranslations
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
 * The question bank as the moderator works it: imports, the list, edits, every move between statuses, and
 * the countries and translations the content repo sets. Each must run inside a transaction. An edit is a
 * compare-and-set on the revision it was made from, and a move one on the statuses it may start from, so two
 * moderators never undo each other.
 */
object QuestionStore {
    /**
     * Imports [drafts] as [status], each checked by the rules, its countries and translations with it, the
     * translations made from its first revision: a key the bank has already, or one earlier in the same
     * import, is a duplicate and writes nothing, its countries and translations included, which a
     * localization sets ([localize]). Two imports of one key racing fail on its unique index, and the rerun
     * finds it.
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
                        countries = draft.countries,
                        translations = draft.translations,
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
     * there first. New answers, or another right one, start the question's stats afresh. Its countries and
     * translations are never touched: the revision moving on leaves each translation's own behind, which
     * marks it stale until the content repo sends it again.
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
                row[timesUnanswered] = 0
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

    /**
     * Sets the countries and translations of the question [localization] names, by its import key, else by
     * its id, where they stand, whatever its status: its countries replaced when it names a set (empty makes
     * it common), and each language it holds replaced, made from the question's revision now; a language it
     * leaves out is kept. All of it is checked as an import's is, against the question as stored, and one
     * part refused writes nothing. No status moves and the revision does not: a translation is no edit.
     *
     * What already stands as sent is left as it is, its `updated_at` too, so the same localization twice
     * changes nothing the second time. The question's row is locked first, as an edit's update locks it, so
     * the two take turns, and what a translation was checked against is the revision it is marked with.
     */
    fun localize(
        localization: QuestionLocalizationDto,
        now: Long,
    ): Localized {
        val key = localization.key
        if (!isImportKey(key)) return Localized.Unknown
        val question =
            lockedSource(Questions.importKey eq key)
                ?: lockedSource(Questions.id eq key)
                ?: return Localized.Unknown
        val id = question[Questions.id]
        val revision = question[Questions.revision]
        val optionCount =
            QuestionOptions
                .select(QuestionOptions.slot)
                .where { QuestionOptions.questionId eq id }
                .count()
                .toInt()
        val (countries, translations) =
            try {
                localization.countries?.let(::checkedCountries) to
                    checkedTranslations(
                        localization.translations,
                        question[Questions.language],
                        optionCount,
                        question[Questions.explanation] != null,
                    )
            } catch (refusal: QuestionRefused) {
                return Localized.Refused(refusal.message)
            }
        if (countries != null && countries != countriesOf(listOf(id))[id].orEmpty().toSet()) {
            QuestionCountries.deleteWhere { QuestionCountries.questionId eq id }
            insertCountries(id, countries)
        }
        val stored = translationsOf(listOf(id))[id].orEmpty()
        translations
            .filter { (tag, words) -> stored[tag] != StoredTranslation(words, revision) }
            .forEach { (tag, words) ->
                QuestionOptionTranslations.deleteWhere {
                    (QuestionOptionTranslations.questionId eq id) and (QuestionOptionTranslations.language eq tag)
                }
                QuestionTranslations.deleteWhere {
                    (QuestionTranslations.questionId eq id) and (QuestionTranslations.language eq tag)
                }
                insertTranslations(id, mapOf(tag to words), revision, now)
            }
        return Localized.Updated
    }

    /**
     * Every question at [statuses], oldest first, as drafts that import back as they are: with its countries
     * and its translations, stale or not, which an import makes from its first revision. A translation that
     * no longer fits its question, its answers counted otherwise or its explanation there or not where the
     * question's is not, is left out, since the rules would refuse the whole draft for it; such a one is
     * made again and sent by a localization.
     */
    fun export(statuses: Set<QuestionStatus>): List<QuestionDraftDto> {
        val rows =
            Questions
                .selectAll()
                .where { Questions.status inList statuses }
                .orderBy(Questions.createdAt to SortOrder.ASC, Questions.id to SortOrder.ASC)
                .toList()
        val questions = dtosOf(rows)
        val ids = questions.map { it.id }
        val countries = countriesOf(ids)
        val translations = translationsOf(ids)
        return questions.map { question ->
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
                countries = countries[question.id].orEmpty(),
                translations =
                    translations[question.id]
                        .orEmpty()
                        .mapValues { (_, stored) -> stored.words }
                        .filterValues { words ->
                            words.options.size == question.options.size &&
                                (words.explanation != null) == (question.explanation != null)
                        }.mapValues { (_, words) -> QuestionTextDto(words.text, words.options, words.explanation) },
            )
        }
    }

    /** The question [where] picks, locked, with what its translations are checked against. */
    private fun lockedSource(where: Op<Boolean>): ResultRow? =
        Questions
            .select(Questions.id, Questions.language, Questions.revision, Questions.explanation)
            .where(where)
            .forUpdate()
            .singleOrNull()

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
            row[revision] = FIRST_REVISION
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
        insertCountries(id, checked.countries)
        insertTranslations(id, checked.translations, FIRST_REVISION, now)
    }

    private fun insertCountries(
        id: String,
        countries: Set<Country>,
    ) {
        if (countries.isEmpty()) return
        QuestionCountries.batchInsert(countries, shouldReturnGeneratedValues = false) { country ->
            this[QuestionCountries.questionId] = id
            this[QuestionCountries.country] = country
        }
    }

    /** [translations] of question [id], each with its answers in the source's slots, made from [revision]. */
    private fun insertTranslations(
        id: String,
        translations: Map<String, CheckedText>,
        revision: Int,
        now: Long,
    ) {
        if (translations.isEmpty()) return
        QuestionTranslations.batchInsert(translations.entries, shouldReturnGeneratedValues = false) { (tag, words) ->
            this[QuestionTranslations.questionId] = id
            this[QuestionTranslations.language] = tag
            this[QuestionTranslations.text] = words.text
            this[QuestionTranslations.explanation] = words.explanation
            this[QuestionTranslations.sourceRevision] = revision
            this[QuestionTranslations.updatedAt] = now
        }
        val options = translations.flatMap { (tag, words) -> words.options.withIndex().map { tag to it } }
        QuestionOptionTranslations.batchInsert(options, shouldReturnGeneratedValues = false) { (tag, option) ->
            this[QuestionOptionTranslations.questionId] = id
            this[QuestionOptionTranslations.language] = tag
            this[QuestionOptionTranslations.slot] = option.index
            this[QuestionOptionTranslations.text] = option.value
        }
    }

    /** The countries of each of [ids] that has any, in [Country]'s order. */
    private fun countriesOf(ids: List<String>): Map<String, List<Country>> {
        if (ids.isEmpty()) return emptyMap()
        return QuestionCountries
            .selectAll()
            .where { QuestionCountries.questionId inList ids }
            .groupBy({ it[QuestionCountries.questionId] }, { it[QuestionCountries.country] })
            .mapValues { (_, countries) -> countries.sorted() }
    }

    /** The translations of each of [ids] that has any, by tag in `ContentLanguages.ALL`'s order. */
    private fun translationsOf(ids: List<String>): Map<String, Map<String, StoredTranslation>> {
        if (ids.isEmpty()) return emptyMap()
        val options =
            QuestionOptionTranslations
                .selectAll()
                .where { QuestionOptionTranslations.questionId inList ids }
                .orderBy(QuestionOptionTranslations.slot)
                .groupBy(
                    { it[QuestionOptionTranslations.questionId] to it[QuestionOptionTranslations.language] },
                    { it[QuestionOptionTranslations.text] },
                )
        return QuestionTranslations
            .selectAll()
            .where { QuestionTranslations.questionId inList ids }
            .sortedBy { ContentLanguages.ALL.indexOf(it[QuestionTranslations.language]) }
            .groupBy { it[QuestionTranslations.questionId] }
            .mapValues { (id, rows) ->
                rows.associate { row ->
                    val tag = row[QuestionTranslations.language]
                    val words =
                        CheckedText(
                            text = row[QuestionTranslations.text],
                            options = options[id to tag].orEmpty(),
                            explanation = row[QuestionTranslations.explanation],
                        )
                    tag to StoredTranslation(words, row[QuestionTranslations.sourceRevision])
                }
            }
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
                stats = statsOf(stats[id], row[Questions.difficulty], slots.size),
                openReports = reports[id] ?: 0,
            )
        }
    }

    /** How a question has played, from its stats [played], none for one never asked, and so how hard. */
    private fun statsOf(
        played: ResultRow?,
        authored: Difficulty,
        options: Int,
    ): QuestionStatsDto {
        val answered = played?.get(QuestionStats.timesAnswered) ?: 0
        val correct = played?.get(QuestionStats.timesCorrect) ?: 0
        val unanswered = played?.get(QuestionStats.timesUnanswered) ?: 0
        return QuestionStatsDto(
            shown = played?.get(QuestionStats.timesShown) ?: 0,
            answered = answered,
            correct = correct,
            averageCorrectMs = if (correct > 0) played?.get(QuestionStats.totalCorrectMs)?.div(correct) else null,
            unanswered = unanswered,
            playsAs = MeasuredDifficulty.of(authored, options, answered, correct, unanswered),
        )
    }

    /** The revision a question is imported at, which its first translations are made from. */
    private const val FIRST_REVISION = 1
}

/** A translation as the bank holds it: its words, and the question's revision they were made from. */
private data class StoredTranslation(
    val words: CheckedText,
    val sourceRevision: Int,
)

/** What [QuestionStore.localize] did with the question it was given. */
sealed interface Localized {
    /** Found and set, or found as it was sent already. */
    data object Updated : Localized

    /** No question has the key as its import key or its id. */
    data object Unknown : Localized

    /** Found, and refused by the rules for [reason]: nothing of it was written. */
    data class Refused(
        val reason: String,
    ) : Localized
}
