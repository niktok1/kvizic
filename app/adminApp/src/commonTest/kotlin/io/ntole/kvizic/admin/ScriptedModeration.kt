package io.ntole.kvizic.admin

import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.moderation.AdminToken
import io.ntole.kvizic.core.domain.moderation.BankOverview
import io.ntole.kvizic.core.domain.moderation.BankStatus
import io.ntole.kvizic.core.domain.moderation.ModeratedQuestion
import io.ntole.kvizic.core.domain.moderation.ModerationRepository
import io.ntole.kvizic.core.domain.moderation.QuestionDifficulty
import io.ntole.kvizic.core.domain.moderation.QuestionEdit
import io.ntole.kvizic.core.domain.moderation.QuestionFilter
import io.ntole.kvizic.core.domain.moderation.QuestionPage
import io.ntole.kvizic.core.domain.moderation.QuestionPlay
import io.ntole.kvizic.core.domain.moderation.ReportOutcome
import io.ntole.kvizic.core.domain.moderation.ReportedQuestion
import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.core.domain.topic.TopicGroup
import io.ntole.kvizic.core.domain.topic.TopicRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A bank in memory, answering as the server would: each call is recorded with the token it carried, and
 * [failNext] makes the next call fail as given. [hold] keeps every call waiting until it completes.
 */
internal class ScriptedModeration : ModerationRepository {
    val questions = mutableListOf<ModeratedQuestion>()
    val reported = mutableListOf<ReportedQuestion>()
    val calls = mutableListOf<String>()
    val tokens = mutableListOf<String>()
    var failNext: KvizicException? = null
    var hold: CompletableDeferred<Unit>? = null

    /** How many questions a page holds, so a test can page through a few. */
    var pageSize = 100

    private suspend fun call(
        token: AdminToken,
        what: String,
    ) {
        calls += what
        tokens += token.value
        hold?.await()
        failNext?.let {
            failNext = null
            throw it
        }
    }

    override suspend fun overview(token: AdminToken): BankOverview {
        call(token, "overview")
        return BankOverview(
            byStatus = questions.groupingBy { it.status }.eachCount(),
            approvedByTopic = emptyMap(),
            liveLobbies = 1,
            liveGames = 0,
            connectedPlayers = 2,
            gamesToday = 3,
        )
    }

    override suspend fun questions(
        token: AdminToken,
        filter: QuestionFilter,
        cursor: String?,
    ): QuestionPage {
        call(token, "questions ${filter.statuses.sorted()} ${filter.search} $cursor")
        val matching =
            questions.filter {
                (filter.statuses.isEmpty() || it.status in filter.statuses) &&
                    (filter.search.isBlank() || filter.search in it.text)
            }
        val from = cursor?.toInt() ?: 0
        val page = matching.drop(from).take(pageSize)
        val next = (from + pageSize).takeIf { it < matching.size }?.toString()
        return QuestionPage(page, next)
    }

    override suspend fun edit(
        token: AdminToken,
        edit: QuestionEdit,
    ): ModeratedQuestion {
        call(token, "edit ${edit.id}")
        return replace(edit.id) {
            it.copy(text = edit.text.trim(), options = edit.options, correct = edit.correct, revision = it.revision + 1)
        }
    }

    override suspend fun approve(
        token: AdminToken,
        id: String,
    ): ModeratedQuestion {
        call(token, "approve $id")
        return replace(id) { it.copy(status = BankStatus.APPROVED) }
    }

    override suspend fun reject(
        token: AdminToken,
        id: String,
        reason: String,
    ): ModeratedQuestion {
        call(token, "reject $id $reason")
        return replace(id) { it.copy(status = BankStatus.REJECTED, rejectionReason = reason) }
    }

    override suspend fun retire(
        token: AdminToken,
        id: String,
    ): ModeratedQuestion {
        call(token, "retire $id")
        return replace(id) { it.copy(status = BankStatus.RETIRED) }
    }

    override suspend fun restore(
        token: AdminToken,
        id: String,
    ): ModeratedQuestion {
        call(token, "restore $id")
        return replace(id) { it.copy(status = BankStatus.APPROVED) }
    }

    override suspend fun reports(token: AdminToken): List<ReportedQuestion> {
        call(token, "reports")
        return reported.toList()
    }

    override suspend fun resolve(
        token: AdminToken,
        questionId: String,
        outcome: ReportOutcome,
    ) {
        call(token, "resolve $questionId $outcome")
        reported.removeAll { it.question.id == questionId }
    }

    override suspend fun deleteAccount(
        token: AdminToken,
        accountId: String,
    ) {
        call(token, "delete $accountId")
    }

    private fun replace(
        id: String,
        change: (ModeratedQuestion) -> ModeratedQuestion,
    ): ModeratedQuestion {
        val index = questions.indexOfFirst { it.id == id }
        val changed = change(questions[index])
        questions[index] = changed
        return changed
    }
}

internal class StaticTopics : TopicRepository {
    private val listed =
        MutableStateFlow(
            listOf(Topic("GEOGRAPHY", "Географија", "Geography", 6), Topic("SPORT", "Спорт", "Sport", 6)),
        )
    override val topics: StateFlow<List<Topic>> = listed
    override val groups: StateFlow<List<TopicGroup>> = MutableStateFlow(emptyList())

    override suspend fun refresh(): List<Topic> = listed.value
}

internal fun question(
    id: String,
    status: BankStatus = BankStatus.DRAFT,
    text: String = "Која река протиче кроз Нови Сад?",
): ModeratedQuestion =
    ModeratedQuestion(
        id = id,
        status = status,
        text = text,
        options = listOf("Дунав", "Сава", "Тиса", "Морава"),
        correct = 0,
        topics = listOf("GEOGRAPHY"),
        difficulty = QuestionDifficulty.EASY,
        explanation = "Дунав тече кроз Нови Сад.",
        source = "https://sr.wikipedia.org/wiki/Нови_Сад",
        author = "claude",
        importBatch = "2026-10-01-batch-01a",
        revision = 1,
        createdAt = 1,
        updatedAt = 1,
        reviewedAt = null,
        rejectionReason = null,
        play = QuestionPlay(shown = 12, answered = 10, correct = 7, averageCorrectMillis = 4_230),
        openReports = 0,
    )
