package io.ntole.kvizic.server.report

import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.core.report.AdminReportDto
import io.ntole.kvizic.core.report.ReasonCountDto
import io.ntole.kvizic.core.report.ReportReason
import io.ntole.kvizic.core.report.ReportResolution
import io.ntole.kvizic.server.db.Players
import io.ntole.kvizic.server.db.Questions
import io.ntole.kvizic.server.db.Reports
import io.ntole.kvizic.server.question.QuestionStore
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

/**
 * Players' reports of questions, one per player per revision of a question: an edit starts every
 * question's reports afresh. [SUSPEND_AFTER] open reports of a wrong answer take a question out of play
 * until the moderator looks. Each must run inside a transaction.
 */
object ReportStore {
    /** How many open wrong-answer reports of one revision suspend a question. */
    const val SUSPEND_AFTER: Int = 3

    enum class Outcome { REPORTED, NO_PLAYER, NO_QUESTION }

    /**
     * Records [reporterId]'s report of [questionId]. The reporter's row is locked first, as a deletion
     * locks it, then the question's, so reports of one question go one at a time: a repeat finds the
     * first, and the suspension counts what is there.
     */
    fun report(
        reporterId: String,
        questionId: String,
        reason: ReportReason,
        now: Long,
    ): Outcome {
        Players
            .select(Players.id)
            .where { Players.id eq reporterId }
            .forUpdate()
            .firstOrNull()
            ?: return Outcome.NO_PLAYER
        val question =
            Questions
                .select(Questions.status, Questions.revision)
                .where { Questions.id eq questionId }
                .forUpdate()
                .firstOrNull()
                ?.takeIf { it[Questions.status] in REPORTABLE }
                ?: return Outcome.NO_QUESTION
        val revision = question[Questions.revision]
        val already =
            !Reports
                .select(Reports.id)
                .where {
                    (Reports.questionId eq questionId) and (Reports.reporterId eq reporterId) and
                        (Reports.questionRevision eq revision)
                }.empty()
        if (!already) {
            Reports.insert { row ->
                row[id] = UUID.randomUUID().toString()
                row[Reports.questionId] = questionId
                row[Reports.reporterId] = reporterId
                row[Reports.reason] = reason
                row[questionRevision] = revision
                row[status] = ReportStatus.OPEN
                row[createdAt] = now
            }
        }
        if (reason == ReportReason.WRONG_ANSWER) {
            val wrong =
                Reports
                    .select(Reports.id)
                    .where {
                        (Reports.questionId eq questionId) and (Reports.questionRevision eq revision) and
                            (Reports.status eq ReportStatus.OPEN) and (Reports.reason eq ReportReason.WRONG_ANSWER)
                    }.count()
            if (wrong >= SUSPEND_AFTER) {
                Questions.update(
                    { (Questions.id eq questionId) and (Questions.status eq QuestionStatus.APPROVED) },
                ) { row ->
                    row[status] = QuestionStatus.SUSPENDED
                }
            }
        }
        return Outcome.REPORTED
    }

    /** The questions with open reports, the most reported first, each with its reports by reason. */
    fun open(limit: Int): List<AdminReportDto> {
        val count = Reports.id.count()
        val last = Reports.createdAt.max()
        // One statement, so every question's counts add up to its total.
        val byReason =
            Reports
                .select(Reports.questionId, Reports.reason, count, last)
                .where { Reports.status eq ReportStatus.OPEN }
                .groupBy(Reports.questionId, Reports.reason)
                .map { row ->
                    Triple(
                        row[Reports.questionId],
                        ReasonCountDto(row[Reports.reason], row[count].toInt()),
                        row[last] ?: 0L,
                    )
                }.groupBy { it.first }
        val ranked =
            byReason
                .map { (id, lines) -> Triple(id, lines.sumOf { it.second.count }, lines.maxOf { it.third }) }
                .sortedWith(
                    compareByDescending<Triple<String, Int, Long>> {
                        it.second
                    }.thenByDescending { it.third }.thenBy { it.first },
                ).take(limit)
        if (ranked.isEmpty()) return emptyList()
        val totals = ranked.associate { it.first to it.second }
        val rows = Questions.selectAll().where { Questions.id inList totals.keys }.toList()
        val questions = QuestionStore.dtosOf(rows, openReports = totals).associateBy { it.id }
        return ranked.mapNotNull { (id, total, lastAt) ->
            val question = questions[id] ?: return@mapNotNull null
            AdminReportDto(
                question = question,
                open = total,
                reasons = byReason.getValue(id).map { it.second }.sortedByDescending { it.count },
                lastReportedAt = lastAt,
            )
        }
    }

    /**
     * Resolves every open report of [questionId] with [resolution]: a dismissal or a fix puts a suspended
     * question back in play, a retirement takes it out. False when there is no such question.
     */
    fun resolve(
        questionId: String,
        resolution: ReportResolution,
        now: Long,
    ): Boolean {
        Questions
            .select(Questions.id)
            .where { Questions.id eq questionId }
            .forUpdate()
            .firstOrNull() ?: return false
        Reports.update({ (Reports.questionId eq questionId) and (Reports.status eq ReportStatus.OPEN) }) { row ->
            row[status] = ReportStatus.RESOLVED
            row[Reports.resolution] = resolution
            row[resolvedAt] = now
        }
        when (resolution) {
            ReportResolution.DISMISSED, ReportResolution.FIXED -> {
                Questions.update(
                    { (Questions.id eq questionId) and (Questions.status eq QuestionStatus.SUSPENDED) },
                ) { row ->
                    row[status] = QuestionStatus.APPROVED
                }
            }

            ReportResolution.RETIRED -> {
                Questions.update({
                    (Questions.id eq questionId) and
                        (Questions.status inList listOf(QuestionStatus.APPROVED, QuestionStatus.SUSPENDED))
                }) { row ->
                    row[status] = QuestionStatus.RETIRED
                    row[reviewedAt] = now
                }
            }

            ReportResolution.UNKNOWN -> {
                error("an unknown resolution is refused before it gets here")
            }
        }
        return true
    }

    private val REPORTABLE = setOf(QuestionStatus.APPROVED, QuestionStatus.SUSPENDED)
}
