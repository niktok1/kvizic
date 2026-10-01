package io.ntole.kvizic.core.domain.moderation

/**
 * The moderator's calls, each carrying the [AdminToken] it is given and nothing of a player's: the
 * moderator is not a player, so no session is ensured, refreshed or minted. Implemented in `:core:data`.
 *
 * Every call throws [io.ntole.kvizic.core.domain.error.KvizicException] naming why it failed: `FORBIDDEN`
 * for a wrong token, `RATE_LIMITED` with its wait, `QUESTION_NOT_FOUND`, `WRONG_STATUS` for a question
 * that no longer stands where the move starts, `STALE_REVISION`, `INVALID_QUESTION`, `PLAYER_NOT_FOUND`.
 */
public interface ModerationRepository {
    public suspend fun overview(token: AdminToken): BankOverview

    /** A page of the bank at [filter], newest first, from [cursor], the page before's `next`, none first. */
    public suspend fun questions(
        token: AdminToken,
        filter: QuestionFilter,
        cursor: String? = null,
    ): QuestionPage

    /** The question as [edit] makes it, its revision moved on. */
    public suspend fun edit(
        token: AdminToken,
        edit: QuestionEdit,
    ): ModeratedQuestion

    /** Approves a draft, or a rejected question on a second look. */
    public suspend fun approve(
        token: AdminToken,
        id: String,
    ): ModeratedQuestion

    /** Rejects a draft for [reason], which [QuestionRules.reasonProblem] takes. */
    public suspend fun reject(
        token: AdminToken,
        id: String,
        reason: String,
    ): ModeratedQuestion

    /** Takes an approved or suspended question out of play. */
    public suspend fun retire(
        token: AdminToken,
        id: String,
    ): ModeratedQuestion

    /** Puts a retired or suspended question back in play. */
    public suspend fun restore(
        token: AdminToken,
        id: String,
    ): ModeratedQuestion

    /** The questions with open reports, the most reported first. */
    public suspend fun reports(token: AdminToken): List<ReportedQuestion>

    /** Closes question [questionId]'s open reports as [outcome]. */
    public suspend fun resolve(
        token: AdminToken,
        questionId: String,
        outcome: ReportOutcome,
    )

    /** Deletes the account [accountId] names, at its player's request, as their own deletion would. */
    public suspend fun deleteAccount(
        token: AdminToken,
        accountId: String,
    )
}
