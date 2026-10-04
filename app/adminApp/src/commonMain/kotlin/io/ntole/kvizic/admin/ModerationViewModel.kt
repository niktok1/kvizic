package io.ntole.kvizic.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.moderation.AccountOrder
import io.ntole.kvizic.core.domain.moderation.AdminToken
import io.ntole.kvizic.core.domain.moderation.BankStatus
import io.ntole.kvizic.core.domain.moderation.ModeratedQuestion
import io.ntole.kvizic.core.domain.moderation.ModerationRepository
import io.ntole.kvizic.core.domain.moderation.QuestionDifficulty
import io.ntole.kvizic.core.domain.moderation.QuestionFilter
import io.ntole.kvizic.core.domain.moderation.QuestionRules
import io.ntole.kvizic.core.domain.moderation.ReportOutcome
import io.ntole.kvizic.core.domain.topic.GetTopics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The moderation app's one ViewModel: the token typed and held in memory only, and every tab's reads and
 * actions, one at a time.
 *
 * A wrong token (`FORBIDDEN`) forgets the token at once, so nothing more is sent with it: the server locks
 * out an address after ten wrong tokens a minute, the right one's requests included. Lock forgets the token
 * and everything read with it, and cancels what is in flight, so nothing it answers is shown; the topics
 * stay, being the same for everybody.
 */
class ModerationViewModel(
    private val moderation: ModerationRepository,
    private val getTopics: GetTopics,
) : ViewModel(),
    ModerationActions {
    private val mutable = MutableStateFlow(ModerationState())
    val state: StateFlow<ModerationState> = mutable.asStateFlow()

    private var token: AdminToken? = null
    private var job: Job? = null

    override fun typeToken(text: String) = mutable.update { it.copy(tokenText = SecretText(text)) }

    /** Takes what is typed as the token, once it can be one, and reads the tab shown. */
    override fun unlock() {
        val typed = AdminToken.of(mutable.value.tokenText.value) ?: return
        token = typed
        mutable.update { it.copy(unlocked = true, tokenText = SecretText(""), failure = null) }
        load(mutable.value.tab)
    }

    override fun lock() {
        job?.cancel()
        token = null
        mutable.update { ModerationState(locks = it.locks + 1, tab = it.tab, topics = it.topics) }
    }

    override fun select(tab: AdminTab) {
        mutable.update { it.copy(tab = tab, failure = null) }
        if (!loaded(tab)) load(tab)
    }

    /** Reads [tab] again, and the topics with it, which name every question's. */
    override fun load(tab: AdminTab) =
        act { token ->
            val topics = runCatching { getTopics() }.getOrNull()
            if (topics != null) mutable.update { it.copy(topics = topics) }
            when (tab) {
                AdminTab.REVIEW -> readDrafts(token)
                AdminTab.BANK -> readBank(token, more = false)
                AdminTab.REPORTS -> readReports(token)
                AdminTab.OVERVIEW -> mutable.update { it.copy(overview = moderation.overview(token)) }
                AdminTab.ACCOUNTS -> readAccounts(token, more = false)
            }
        }

    // Review.

    override fun next() = mutable.update { it.copy(review = it.review.moved(1)) }

    override fun previous() = mutable.update { it.copy(review = it.review.moved(-1)) }

    override fun approveCurrent() {
        val question = mutable.value.review.current ?: return
        act { token ->
            moderation.approve(token, question.id)
            decided(question.id)
        }
    }

    override fun startReject() = mutable.update { it.copy(review = it.review.copy(rejecting = true)) }

    override fun cancelReject() = mutable.update { it.copy(review = it.review.copy(rejecting = false, reason = "")) }

    override fun typeReason(reason: String) = mutable.update { it.copy(review = it.review.copy(reason = reason)) }

    override fun rejectCurrent() {
        val review = mutable.value.review
        val question = review.current ?: return
        if (QuestionRules.reasonProblem(review.reason) != null) return
        act { token ->
            moderation.reject(token, question.id, review.reason)
            decided(question.id)
        }
    }

    // The bank.

    override fun typeSearch(search: String) = mutable.update { it.copy(bank = it.bank.copy(search = search)) }

    override fun toggleStatus(status: BankStatus) {
        mutable.update { state ->
            val statuses = state.bank.statuses.let { if (status in it) it - status else it + status }
            state.copy(bank = state.bank.copy(statuses = statuses))
        }
        load(AdminTab.BANK)
    }

    override fun loadMore() = act { token -> readBank(token, more = true) }

    override fun approve(question: ModeratedQuestion) = move { token -> moderation.approve(token, question.id) }

    override fun retire(question: ModeratedQuestion) = move { token -> moderation.retire(token, question.id) }

    override fun restore(question: ModeratedQuestion) = move { token -> moderation.restore(token, question.id) }

    // Reports.

    override fun resolve(
        question: ModeratedQuestion,
        outcome: ReportOutcome,
    ) = act { token ->
        moderation.resolve(token, question.id, outcome)
        readReports(token)
    }

    // The editor.

    override fun edit(question: ModeratedQuestion) = mutable.update { it.copy(editor = EditorState.of(question)) }

    override fun closeEditor() = mutable.update { it.copy(editor = null) }

    override fun changeEditor(change: (EditorState) -> EditorState) =
        mutable.update { state -> state.copy(editor = state.editor?.let { change(it).copy(refusal = null) }) }

    override fun typeOption(
        index: Int,
        text: String,
    ) = changeEditor { it.copy(options = it.options.mapIndexed { i, option -> if (i == index) text else option }) }

    override fun addOption() =
        changeEditor { if (it.options.size >= QuestionRules.MAX_OPTIONS) it else it.copy(options = it.options + "") }

    override fun removeOption(index: Int) =
        changeEditor { editor ->
            if (editor.options.size <= QuestionRules.MIN_OPTIONS) return@changeEditor editor
            val correct =
                when {
                    index == editor.correct -> 0
                    index < editor.correct -> editor.correct - 1
                    else -> editor.correct
                }
            editor.copy(options = editor.options.filterIndexed { i, _ -> i != index }, correct = correct)
        }

    override fun toggleTopic(id: String) =
        changeEditor { editor ->
            editor.copy(topics = if (id in editor.topics) editor.topics - id else editor.topics + id)
        }

    override fun pickDifficulty(difficulty: QuestionDifficulty) = changeEditor { it.copy(difficulty = difficulty) }

    /**
     * Sends the edit once the rules find nothing wrong with it, and puts the question it answers with in
     * every list that shows it. A question its reports were about is marked fixed by the moderator, as a
     * separate step on the Reports tab.
     */
    override fun saveEdit() {
        val editor = mutable.value.editor ?: return
        if (editor.problem != null) return
        act(onFailure = { message ->
            mutable.update { it.copy(editor = it.editor?.copy(refusal = message)) }
        }) { token ->
            val saved = moderation.edit(token, editor.edit)
            mutable.update { it.replaced(saved).copy(editor = null) }
        }
    }

    // Accounts.

    override fun typeAccountSearch(search: String) =
        mutable.update {
            it.copy(accounts = it.accounts.copy(search = search))
        }

    override fun pickAccountOrder(order: AccountOrder) {
        mutable.update { it.copy(accounts = it.accounts.copy(order = order)) }
        load(AdminTab.ACCOUNTS)
    }

    override fun loadMoreAccounts() = act { token -> readAccounts(token, more = true) }

    /** Opens [id] with what it has played, and makes it the account a deletion would name. */
    override fun openAccount(id: String) =
        act { token ->
            val detail = moderation.account(token, id)
            mutable.update {
                it.copy(accounts = it.accounts.copy(selected = detail, accountId = id, confirming = false, done = null))
            }
        }

    override fun closeAccount() = mutable.update { it.copy(accounts = it.accounts.copy(selected = null)) }

    override fun typeAccountId(id: String) =
        mutable.update { it.copy(accounts = it.accounts.copy(accountId = id, confirming = false, done = null)) }

    override fun askToDelete() {
        if (mutable.value.accounts.accountId
                .isBlank()
        ) {
            return
        }
        mutable.update { it.copy(accounts = it.accounts.copy(confirming = true)) }
    }

    override fun cancelDelete() = mutable.update { it.copy(accounts = it.accounts.copy(confirming = false)) }

    override fun deleteAccount() {
        val id =
            mutable.value.accounts.accountId
                .trim()
        mutable.update { it.copy(accounts = it.accounts.copy(confirming = false)) }
        act { token ->
            moderation.deleteAccount(token, id)
            mutable.update { state ->
                val gone = state.accounts.players.count { it.id == id }
                state.copy(
                    accounts =
                        state.accounts.copy(
                            players = state.accounts.players.filter { it.id != id },
                            total = (state.accounts.total - gone).coerceAtLeast(0),
                            selected = state.accounts.selected?.takeIf { it.account.id != id },
                            accountId = "",
                            done = "Deleted the account $id.",
                        ),
                )
            }
        }
    }

    // How each read and action runs.

    private fun loaded(tab: AdminTab): Boolean =
        when (tab) {
            AdminTab.REVIEW -> mutable.value.review.loaded
            AdminTab.BANK -> mutable.value.bank.loaded
            AdminTab.REPORTS -> mutable.value.reports.loaded
            AdminTab.OVERVIEW -> mutable.value.overview != null
            AdminTab.ACCOUNTS -> mutable.value.accounts.loaded
        }

    /** The first page of the players at the order and search, or the next one after those shown. */
    private suspend fun readAccounts(
        token: AdminToken,
        more: Boolean,
    ) {
        val accounts = mutable.value.accounts
        val cursor = if (more) accounts.next ?: return else null
        val page = moderation.accounts(token, accounts.order, accounts.search, cursor)
        mutable.update { state ->
            state.copy(
                accounts =
                    state.accounts.copy(
                        loaded = true,
                        players = if (more) state.accounts.players + page.accounts else page.accounts,
                        total = page.total,
                        next = page.next,
                    ),
            )
        }
    }

    private suspend fun readDrafts(token: AdminToken) {
        val page = moderation.questions(token, QuestionFilter(statuses = setOf(BankStatus.DRAFT)))
        mutable.update { state ->
            state.copy(
                review =
                    state.review.copy(
                        loaded = true,
                        drafts = page.questions,
                        index = 0,
                        more = page.next != null,
                        rejecting = false,
                        reason = "",
                    ),
            )
        }
    }

    private suspend fun readBank(
        token: AdminToken,
        more: Boolean,
    ) {
        val bank = mutable.value.bank
        if (more && bank.next == null) return
        val filter = QuestionFilter(statuses = bank.statuses, search = bank.search)
        val page = moderation.questions(token, filter, cursor = if (more) bank.next else null)
        mutable.update { state ->
            // A filter changed while the page was read drops it: it was read at the one before.
            if (state.bank.statuses != filter.statuses || state.bank.search != bank.search) return@update state
            val questions = if (more) state.bank.questions + page.questions else page.questions
            state.copy(bank = state.bank.copy(loaded = true, questions = questions, next = page.next))
        }
    }

    private suspend fun readReports(token: AdminToken) {
        val reported = moderation.reports(token)
        mutable.update { it.copy(reports = ReportsState(loaded = true, reported = reported)) }
    }

    /** Drops the draft decided from the queue, the next one shown in its place, and reads more once none is left. */
    private suspend fun decided(id: String) {
        mutable.update { state ->
            val review = state.review
            val drafts = review.drafts.filterNot { it.id == id }
            state.copy(
                review =
                    review.copy(
                        drafts = drafts,
                        index = review.index.coerceAtMost((drafts.size - 1).coerceAtLeast(0)),
                        decided = review.decided + 1,
                        rejecting = false,
                        reason = "",
                    ),
            )
        }
        val review = mutable.value.review
        if (review.drafts.isEmpty() && review.more) token?.let { readDrafts(it) }
    }

    /** A question moved by [call], put where the server says it now stands in every list. */
    private fun move(call: suspend (AdminToken) -> ModeratedQuestion) =
        act { token ->
            mutable.update { it.replaced(call(token)) }
        }

    /**
     * Runs [block] with the token held, unless another action is running or no token is. A failure is said
     * at the top of the tab, or handed to [onFailure]; a wrong token is forgotten at once.
     */
    private fun act(
        onFailure: ((String) -> Unit)? = null,
        block: suspend (AdminToken) -> Unit,
    ) {
        val held = token ?: return
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, failure = null) }
        job =
            viewModelScope.launch {
                try {
                    block(held)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: KvizicException) {
                    val message = messageOf(failure)
                    if (failure.error == CoreError.FORBIDDEN) {
                        token = null
                        mutable.update { it.copy(unlocked = false) }
                    }
                    if (onFailure != null && failure.error != CoreError.FORBIDDEN) {
                        onFailure(message)
                    } else {
                        mutable.update { it.copy(failure = message) }
                    }
                } finally {
                    // Not once a Lock has cancelled it and another action may have begun.
                    if (job === currentCoroutineContext()[Job]) mutable.update { it.copy(busy = false) }
                }
            }
    }
}

/** [question] in every list that shows it, as the server now has it; a draft no longer one leaves the queue. */
private fun ModerationState.replaced(question: ModeratedQuestion): ModerationState {
    fun List<ModeratedQuestion>.with() = map { if (it.id == question.id) question else it }
    return copy(
        review =
            review.copy(
                drafts =
                    review.drafts.with().filter {
                        it.status == BankStatus.DRAFT
                    },
            ),
        bank = bank.copy(questions = bank.questions.with()),
        reports =
            reports.copy(
                reported =
                    reports.reported.map {
                        if (it.question.id ==
                            question.id
                        ) {
                            it.copy(question = question)
                        } else {
                            it
                        }
                    },
            ),
    )
}

private fun ReviewState.moved(by: Int): ReviewState {
    if (drafts.isEmpty()) return this
    return copy(index = (index + by).mod(drafts.size), rejecting = false, reason = "")
}
