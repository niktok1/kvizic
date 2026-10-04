package io.ntole.kvizic.admin

import io.ntole.kvizic.core.domain.moderation.AccountDetail
import io.ntole.kvizic.core.domain.moderation.AccountOrder
import io.ntole.kvizic.core.domain.moderation.AccountSummary
import io.ntole.kvizic.core.domain.moderation.BankOverview
import io.ntole.kvizic.core.domain.moderation.BankStatus
import io.ntole.kvizic.core.domain.moderation.ModeratedQuestion
import io.ntole.kvizic.core.domain.moderation.QuestionDifficulty
import io.ntole.kvizic.core.domain.moderation.QuestionEdit
import io.ntole.kvizic.core.domain.moderation.ReportedQuestion
import io.ntole.kvizic.core.domain.topic.Topic

/** The moderation app's tabs, in their order on screen. */
enum class AdminTab { REVIEW, BANK, REPORTS, OVERVIEW, ACCOUNTS }

/** Text that is never shown by [toString], so the typed admin token never lands in a log or a state's text. */
class SecretText(
    val value: String,
) {
    override fun equals(other: Any?): Boolean = other is SecretText && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "SecretText(…)"
}

/**
 * Everything the moderation app shows. The token itself lives in the ViewModel's memory only; [tokenText]
 * is what is typed in its field, and [unlocked] whether a token is held. [locks] counts every Lock, so the
 * token's field is made anew each time and keeps no undo history of the token.
 *
 * One action runs at a time ([busy]). [failure] is a read's or an action's, shown at the top of the tab
 * it came from.
 */
data class ModerationState(
    val tokenText: SecretText = SecretText(""),
    val unlocked: Boolean = false,
    val locks: Int = 0,
    val tab: AdminTab = AdminTab.REVIEW,
    val topics: List<Topic> = emptyList(),
    val busy: Boolean = false,
    val failure: String? = null,
    val review: ReviewState = ReviewState(),
    val bank: BankState = BankState(),
    val reports: ReportsState = ReportsState(),
    val overview: BankOverview? = null,
    val accounts: AccountsState = AccountsState(),
    val editor: EditorState? = null,
) {
    /** A question's topics named in Serbian, one not listed by its id. */
    fun topicNames(ids: List<String>): String =
        ids.joinToString(" · ") { id -> topics.firstOrNull { it.id == id }?.nameSr ?: id }
}

/**
 * The drafts waiting, one shown at a time ([current]). [more] says the server holds more than were read.
 * [decided] counts what was decided since the token was typed. [rejecting] opens the reason's field.
 */
data class ReviewState(
    val loaded: Boolean = false,
    val drafts: List<ModeratedQuestion> = emptyList(),
    val index: Int = 0,
    val more: Boolean = false,
    val decided: Int = 0,
    val rejecting: Boolean = false,
    val reason: String = "",
) {
    val current: ModeratedQuestion? get() = drafts.getOrNull(index)
}

/** The bank's list at [statuses] and [search], read a page at a time while [next] says there is more. */
data class BankState(
    val loaded: Boolean = false,
    val statuses: Set<BankStatus> = emptySet(),
    val search: String = "",
    val questions: List<ModeratedQuestion> = emptyList(),
    val next: String? = null,
)

data class ReportsState(
    val loaded: Boolean = false,
    val reported: List<ReportedQuestion> = emptyList(),
)

/**
 * The players, [players] of the [total] matching [search], at [order], read a page at a time while [next]
 * says there is more; [selected] is the one opened. A deletion is asked for by [accountId], the opened
 * player's or one typed from an email, once [confirming], and what became of the last one is [done].
 */
data class AccountsState(
    val loaded: Boolean = false,
    val order: AccountOrder = AccountOrder.LAST_SEEN,
    val search: String = "",
    val players: List<AccountSummary> = emptyList(),
    val total: Int = 0,
    val next: String? = null,
    val selected: AccountDetail? = null,
    val accountId: String = "",
    val confirming: Boolean = false,
    val done: String? = null,
)

/**
 * A question being edited, every field as typed: [options] 2 to 4, [correct] an index into them. [problem]
 * is what the rules find wrong, or the server's refusal; Save sends nothing while there is one.
 */
data class EditorState(
    val original: ModeratedQuestion,
    val text: String,
    val options: List<String>,
    val correct: Int,
    val topics: List<String>,
    val difficulty: QuestionDifficulty,
    val explanation: String,
    val source: String,
    val refusal: String? = null,
) {
    val edit: QuestionEdit
        get() =
            QuestionEdit(
                id = original.id,
                revision = original.revision,
                text = text,
                options = options,
                correct = correct,
                topics = topics,
                difficulty = difficulty,
                explanation = explanation,
                source = source,
            )

    val problem: String? get() = edit.problem ?: refusal

    companion object {
        fun of(question: ModeratedQuestion): EditorState =
            EditorState(
                original = question,
                text = question.text,
                options = question.options,
                correct = question.correct,
                topics = question.topics,
                difficulty = question.difficulty,
                explanation = question.explanation.orEmpty(),
                source = question.source.orEmpty(),
            )
    }
}
