package io.ntole.kvizic.admin

import io.ntole.kvizic.core.domain.moderation.AccountOrder
import io.ntole.kvizic.core.domain.moderation.BankStatus
import io.ntole.kvizic.core.domain.moderation.ModeratedQuestion
import io.ntole.kvizic.core.domain.moderation.QuestionDifficulty
import io.ntole.kvizic.core.domain.moderation.ReportOutcome

/**
 * What the moderation app's screens can ask for: [ModerationViewModel]'s, and nothing for a screen drawn
 * alone, as a test draws one.
 */
@Suppress("TooManyFunctions")
interface ModerationActions {
    fun typeToken(text: String) {}

    fun unlock() {}

    fun lock() {}

    fun select(tab: AdminTab) {}

    fun load(tab: AdminTab) {}

    fun next() {}

    fun previous() {}

    fun approveCurrent() {}

    fun startReject() {}

    fun cancelReject() {}

    fun typeReason(reason: String) {}

    fun rejectCurrent() {}

    fun typeSearch(search: String) {}

    fun toggleStatus(status: BankStatus) {}

    fun loadMore() {}

    fun approve(question: ModeratedQuestion) {}

    fun retire(question: ModeratedQuestion) {}

    fun restore(question: ModeratedQuestion) {}

    fun resolve(
        question: ModeratedQuestion,
        outcome: ReportOutcome,
    ) {}

    fun edit(question: ModeratedQuestion) {}

    fun closeEditor() {}

    fun changeEditor(change: (EditorState) -> EditorState) {}

    fun typeOption(
        index: Int,
        text: String,
    ) {}

    fun addOption() {}

    fun removeOption(index: Int) {}

    fun toggleTopic(id: String) {}

    fun pickDifficulty(difficulty: QuestionDifficulty) {}

    fun saveEdit() {}

    fun typeAccountSearch(search: String) {}

    fun pickAccountOrder(order: AccountOrder) {}

    fun loadMoreAccounts() {}

    fun openAccount(id: String) {}

    fun closeAccount() {}

    fun typeAccountId(id: String) {}

    fun askToDelete() {}

    fun cancelDelete() {}

    fun deleteAccount() {}
}
