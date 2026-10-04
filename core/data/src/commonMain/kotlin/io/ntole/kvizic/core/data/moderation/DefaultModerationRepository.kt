package io.ntole.kvizic.core.data.moderation

import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.data.mapper.runApi
import io.ntole.kvizic.core.domain.moderation.AccountDetail
import io.ntole.kvizic.core.domain.moderation.AccountFilter
import io.ntole.kvizic.core.domain.moderation.AccountOrder
import io.ntole.kvizic.core.domain.moderation.AccountPage
import io.ntole.kvizic.core.domain.moderation.AdminToken
import io.ntole.kvizic.core.domain.moderation.BankOverview
import io.ntole.kvizic.core.domain.moderation.ModeratedQuestion
import io.ntole.kvizic.core.domain.moderation.ModerationRepository
import io.ntole.kvizic.core.domain.moderation.QuestionEdit
import io.ntole.kvizic.core.domain.moderation.QuestionFilter
import io.ntole.kvizic.core.domain.moderation.QuestionPage
import io.ntole.kvizic.core.domain.moderation.ReportOutcome
import io.ntole.kvizic.core.domain.moderation.ReportedQuestion
import io.ntole.kvizic.core.network.api.ModerationApi
import io.ntole.kvizic.core.player.DeleteAccountRequest
import io.ntole.kvizic.core.question.QuestionDecisionRequest
import io.ntole.kvizic.core.report.ResolveReportsRequest

/**
 * Every moderation call through [runApi] alone, never `withSessionRecovery`: the moderator is not a player,
 * so nothing here can refresh, replace or mint a session. Lists are read [PAGE_SIZE] at a time, the most
 * the server lists at once, since each read spends the address's admin budget.
 */
public class DefaultModerationRepository(
    private val api: ModerationApi,
) : ModerationRepository {
    override suspend fun overview(token: AdminToken): BankOverview = runApi { api.overview(token.value) }.toDomain()

    override suspend fun questions(
        token: AdminToken,
        filter: QuestionFilter,
        cursor: String?,
    ): QuestionPage {
        val page =
            runApi {
                api.questions(
                    token.value,
                    statuses = filter.statuses.mapNotNull { it.toWire() }.sortedBy { it.ordinal },
                    topics = filter.topics.sorted(),
                    search = filter.search.trim().takeIf { it.isNotEmpty() },
                    cursor = cursor,
                    limit = PAGE_SIZE,
                )
            }
        return QuestionPage(page.questions.map { it.toDomain() }, page.nextCursor)
    }

    override suspend fun edit(
        token: AdminToken,
        edit: QuestionEdit,
    ): ModeratedQuestion = runApi { api.edit(token.value, edit.toRequest()) }.toDomain()

    override suspend fun approve(
        token: AdminToken,
        id: String,
    ): ModeratedQuestion = runApi { api.approve(token.value, QuestionDecisionRequest(id)) }.toDomain()

    override suspend fun reject(
        token: AdminToken,
        id: String,
        reason: String,
    ): ModeratedQuestion = runApi { api.reject(token.value, QuestionDecisionRequest(id, reason.trim())) }.toDomain()

    override suspend fun retire(
        token: AdminToken,
        id: String,
    ): ModeratedQuestion = runApi { api.retire(token.value, QuestionDecisionRequest(id)) }.toDomain()

    override suspend fun restore(
        token: AdminToken,
        id: String,
    ): ModeratedQuestion = runApi { api.restore(token.value, QuestionDecisionRequest(id)) }.toDomain()

    override suspend fun reports(token: AdminToken): List<ReportedQuestion> =
        runApi { api.reports(token.value, PAGE_SIZE) }.reports.map { it.toDomain() }

    override suspend fun resolve(
        token: AdminToken,
        questionId: String,
        outcome: ReportOutcome,
    ) {
        runApi { api.resolve(token.value, ResolveReportsRequest(questionId, outcome.toWire())) }
    }

    override suspend fun accounts(
        token: AdminToken,
        order: AccountOrder,
        filter: AccountFilter,
        cursor: String?,
    ): AccountPage =
        runApi {
            api.accounts(
                token.value,
                sort = order.toWire(),
                search = filter.search.trim().takeIf { it.isNotEmpty() },
                answeredOnly = filter.answeredOnly,
                playGamesOnly = filter.playGamesOnly,
                cursor = cursor,
                limit = PAGE_SIZE,
            )
        }.toDomain()

    override suspend fun account(
        token: AdminToken,
        id: String,
    ): AccountDetail = runApi { api.account(token.value, id.trim()) }.toDomain()

    override suspend fun deleteAccount(
        token: AdminToken,
        accountId: String,
    ) {
        runApi { api.deleteAccount(token.value, DeleteAccountRequest(accountId.trim())) }
    }

    public companion object {
        /** The most the server lists at once ([KvizicApi.Limits.MAX_PAGE_SIZE]). */
        public const val PAGE_SIZE: Int = KvizicApi.Limits.MAX_PAGE_SIZE
    }
}
