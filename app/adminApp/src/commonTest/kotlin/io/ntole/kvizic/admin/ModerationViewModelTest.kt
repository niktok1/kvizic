package io.ntole.kvizic.admin

import androidx.lifecycle.viewModelScope
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.GameError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.moderation.BankStatus
import io.ntole.kvizic.core.domain.moderation.ReportOutcome
import io.ntole.kvizic.core.domain.moderation.ReportedQuestion
import io.ntole.kvizic.core.domain.report.QuestionReportReason
import io.ntole.kvizic.core.domain.topic.GetTopics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The moderation app's ViewModel over a bank in memory. */
@OptIn(ExperimentalCoroutinesApi::class)
class ModerationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val bank = ScriptedModeration()
    private lateinit var viewModel: ModerationViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        bank.questions += listOf(question("q1"), question("q2"), question("q3", BankStatus.APPROVED))
        viewModel = ModerationViewModel(bank, GetTopics(StaticTopics()))
    }

    @AfterTest
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    private suspend fun TestScope.unlocked(token: String = "secret") {
        viewModel.typeToken(token)
        viewModel.unlock()
        advanceUntilIdle()
    }

    @Test
    fun `nothing is sent until what is typed can be a token`() =
        test {
            viewModel.typeToken("   ")
            viewModel.unlock()
            viewModel.typeToken("has space")
            viewModel.unlock()
            advanceUntilIdle()

            assertTrue(bank.calls.isEmpty())
            assertFalse(viewModel.state.value.unlocked)
        }

    @Test
    fun `unlocking reads the drafts with the token and keeps it out of the state's text`() =
        test {
            unlocked()

            val state = viewModel.state.value
            assertTrue(state.unlocked)
            assertEquals(listOf("q1", "q2"), state.review.drafts.map { it.id })
            assertEquals(listOf("secret"), bank.tokens)
            assertFalse("secret" in state.toString())
            assertEquals("", state.tokenText.value, "the typed token stays in its field")
            assertEquals(listOf("Географија", "Спорт"), state.topics.map { it.nameSr })
        }

    @Test
    fun `approving and rejecting move through the queue`() =
        test {
            unlocked()

            viewModel.approveCurrent()
            advanceUntilIdle()
            viewModel.startReject()
            viewModel.typeReason("Wrong answer")
            viewModel.rejectCurrent()
            advanceUntilIdle()

            assertEquals(listOf("questions [DRAFT]  null", "approve q1", "reject q2 Wrong answer"), bank.calls)
            val review = viewModel.state.value.review
            assertNull(review.current)
            assertEquals(2, review.decided)
            assertFalse(review.rejecting)
        }

    @Test
    fun `a blank reason sends nothing`() =
        test {
            unlocked()

            viewModel.startReject()
            viewModel.typeReason("  ")
            viewModel.rejectCurrent()
            advanceUntilIdle()

            assertEquals(1, bank.calls.size)
        }

    @Test
    fun `next and previous go round the queue`() =
        test {
            unlocked()

            viewModel.next()
            assertEquals(
                "q2",
                viewModel.state.value.review.current
                    ?.id,
            )
            viewModel.next()
            assertEquals(
                "q1",
                viewModel.state.value.review.current
                    ?.id,
            )
            viewModel.previous()
            assertEquals(
                "q2",
                viewModel.state.value.review.current
                    ?.id,
            )
        }

    @Test
    fun `the queue reads more once the page read is decided`() =
        test {
            bank.pageSize = 1
            unlocked()
            assertTrue(viewModel.state.value.review.more)

            viewModel.approveCurrent()
            advanceUntilIdle()

            assertEquals(
                "q2",
                viewModel.state.value.review.current
                    ?.id,
            )
        }

    @Test
    fun `a wrong token is forgotten at once and nothing more is sent with it`() =
        test {
            bank.failNext = KvizicException(CoreError.FORBIDDEN)
            unlocked("wrong")

            val state = viewModel.state.value
            assertFalse(state.unlocked)
            assertEquals("Wrong admin token. Type it again.", state.failure)
            viewModel.approveCurrent()
            viewModel.load(AdminTab.BANK)
            advanceUntilIdle()
            assertEquals(1, bank.calls.size)
        }

    @Test
    fun `lock forgets the token and what was read and cancels what is in flight`() =
        test {
            unlocked()
            bank.hold = CompletableDeferred()
            viewModel.approveCurrent()
            advanceUntilIdle()

            viewModel.lock()
            bank.hold?.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.unlocked)
            assertFalse(state.busy)
            assertEquals(1, state.locks)
            assertTrue(state.review.drafts.isEmpty())
            assertEquals(2, state.topics.size, "the topics are everybody's")
        }

    @Test
    fun `one action at a time`() =
        test {
            unlocked()
            bank.hold = CompletableDeferred()

            viewModel.approveCurrent()
            advanceUntilIdle()
            viewModel.approveCurrent()
            viewModel.load(AdminTab.REVIEW)
            advanceUntilIdle()

            assertEquals(2, bank.calls.size)
            assertTrue(viewModel.state.value.busy)
            bank.hold?.complete(Unit)
            advanceUntilIdle()
            assertFalse(viewModel.state.value.busy)
        }

    @Test
    fun `the bank filters by status and pages`() =
        test {
            bank.pageSize = 2
            unlocked()
            viewModel.select(AdminTab.BANK)
            advanceUntilIdle()
            assertEquals(
                listOf("q1", "q2"),
                viewModel.state.value.bank.questions
                    .map { it.id },
            )

            viewModel.loadMore()
            advanceUntilIdle()
            assertEquals(
                listOf("q1", "q2", "q3"),
                viewModel.state.value.bank.questions
                    .map { it.id },
            )

            viewModel.toggleStatus(BankStatus.APPROVED)
            advanceUntilIdle()
            assertEquals(
                listOf("q3"),
                viewModel.state.value.bank.questions
                    .map { it.id },
            )
        }

    @Test
    fun `a move puts the question where the server says in every list`() =
        test {
            unlocked()
            viewModel.select(AdminTab.BANK)
            advanceUntilIdle()

            viewModel.approve(
                viewModel.state.value.bank.questions
                    .first(),
            )
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(
                BankStatus.APPROVED,
                state.bank.questions
                    .first()
                    .status,
            )
            assertEquals(listOf("q2"), state.review.drafts.map { it.id }, "an approved question leaves the queue")
        }

    @Test
    fun `an edit is checked before it is sent and the server's refusal is shown in the editor`() =
        test {
            unlocked()
            viewModel.edit(
                viewModel.state.value.review.drafts
                    .first(),
            )
            viewModel.changeEditor { it.copy(text = "") }
            viewModel.saveEdit()
            advanceUntilIdle()
            assertEquals(1, bank.calls.size, "a blank question was sent")

            viewModel.changeEditor { it.copy(text = "Која река протиче кроз Београд?") }
            bank.failNext = KvizicException(GameError.STALE_REVISION)
            viewModel.saveEdit()
            advanceUntilIdle()
            assertEquals(
                "Edited meanwhile. Close, load again and edit that.",
                viewModel.state.value.editor
                    ?.problem,
            )

            viewModel.typeOption(1, "Тиса")
            viewModel.saveEdit()
            advanceUntilIdle()
            assertEquals(
                1,
                viewModel.state.value.editor
                    ?.let { 1 } ?: 1,
            )
            assertTrue(
                viewModel.state.value.editor
                    ?.problem != null,
                "two answers alike",
            )

            viewModel.typeOption(1, "Сава")
            viewModel.saveEdit()
            advanceUntilIdle()
            val state = viewModel.state.value
            assertNull(state.editor)
            assertEquals(
                "Која река протиче кроз Београд?",
                state.review.drafts
                    .first()
                    .text,
            )
            assertEquals(
                2,
                state.review.drafts
                    .first()
                    .revision,
            )
        }

    @Test
    fun `answers are added and removed with the right one kept`() =
        test {
            unlocked()
            viewModel.edit(
                viewModel.state.value.review.drafts
                    .first(),
            )

            viewModel.changeEditor { it.copy(correct = 3) }
            viewModel.removeOption(1)
            assertEquals(
                2,
                viewModel.state.value.editor
                    ?.correct,
            )
            viewModel.removeOption(2)
            assertEquals(
                0,
                viewModel.state.value.editor
                    ?.correct,
            )
            viewModel.removeOption(0)
            assertEquals(
                2,
                viewModel.state.value.editor
                    ?.options
                    ?.size,
                "fewer than two answers",
            )
            viewModel.addOption()
            viewModel.addOption()
            viewModel.addOption()
            assertEquals(
                4,
                viewModel.state.value.editor
                    ?.options
                    ?.size,
                "more than four answers",
            )
        }

    @Test
    fun `reports are resolved and read again`() =
        test {
            bank.reported +=
                ReportedQuestion(
                    question("q3", BankStatus.SUSPENDED),
                    open = 3,
                    reasons = listOf(QuestionReportReason.WRONG_ANSWER to 3),
                    lastReportedAt = 5,
                )
            unlocked()
            viewModel.select(AdminTab.REPORTS)
            advanceUntilIdle()
            assertEquals(1, viewModel.state.value.reports.reported.size)

            viewModel.resolve(
                viewModel.state.value.reports.reported
                    .single()
                    .question,
                ReportOutcome.DISMISSED,
            )
            advanceUntilIdle()

            assertTrue("resolve q3 DISMISSED" in bank.calls)
            assertTrue(
                viewModel.state.value.reports.reported
                    .isEmpty(),
            )
        }

    @Test
    fun `an account is deleted only once confirmed`() =
        test {
            unlocked()
            viewModel.select(AdminTab.ACCOUNTS)
            viewModel.typeAccountId(" p1 ")
            viewModel.askToDelete()
            assertTrue(viewModel.state.value.accounts.confirming)
            assertEquals(1, bank.calls.size, "asking sent something")

            viewModel.deleteAccount()
            advanceUntilIdle()

            assertEquals("delete p1", bank.calls.last())
            assertEquals("Deleted the account p1.", viewModel.state.value.accounts.done)
            assertEquals("", viewModel.state.value.accounts.accountId)
        }

    @Test
    fun `a limit names its wait`() =
        test {
            bank.failNext = KvizicException(CoreError.RATE_LIMITED, retryAfter = kotlin.time.Duration.parse("12s"))
            unlocked()

            assertEquals("Too many requests. Wait 12 s.", viewModel.state.value.failure)
            assertTrue(viewModel.state.value.unlocked)
        }
}
