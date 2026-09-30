package io.ntole.kvizic.about

import io.ntole.kvizic.analytics.RecordingAnalytics
import io.ntole.kvizic.core.domain.account.AccountRepository
import io.ntole.kvizic.core.domain.account.DeleteAccount
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.KvizicException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Deleting the account from the About screen, one deletion at a time. */
@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {
    private val main: TestDispatcher = StandardTestDispatcher()
    private val accounts = FakeAccounts()
    private val analytics = RecordingAnalytics()
    private val about by lazy { AboutViewModel(DeleteAccount(accounts, analytics), analytics) }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a deletion is done once the server has deleted the account and the next visit says nothing`() =
        runTest(main) {
            about.delete()
            testScheduler.advanceUntilIdle()

            assertEquals(Deletion.Done, about.deletion.value)
            assertEquals(1, accounts.deletions)

            about.leftAfterDeletion()
            assertEquals(Deletion.Idle, about.deletion.value)
        }

    @Test
    fun `a second tap while a deletion is in flight sends nothing more`() =
        runTest(main) {
            val answer = CompletableDeferred<Unit>()
            accounts.hold = answer

            about.delete()
            testScheduler.runCurrent()
            about.delete()
            testScheduler.runCurrent()

            assertEquals(Deletion.InFlight, about.deletion.value)
            answer.complete(Unit)
            testScheduler.advanceUntilIdle()
            assertEquals(1, accounts.deletions)
        }

    @Test
    fun `a deletion that failed says why and is reported`() =
        runTest(main) {
            accounts.failWith = CoreError.NETWORK

            about.delete()
            testScheduler.advanceUntilIdle()

            assertEquals(Deletion.Failed(CoreError.NETWORK), about.deletion.value)
            assertEquals(
                mapOf(AnalyticsProperty.CODE to "NETWORK", AnalyticsProperty.ACTION to "delete_account"),
                analytics.named(AnalyticsEvent.ERROR_SHOWN).single().properties,
            )
            about.leftAfterDeletion()
            assertEquals(Deletion.Failed(CoreError.NETWORK), about.deletion.value, "a failure stays to be read")
        }

    private class FakeAccounts : AccountRepository {
        var deletions = 0
        var failWith: CoreError? = null
        var hold: CompletableDeferred<Unit>? = null

        override suspend fun logOut() = Unit

        override suspend fun deleteAccount() {
            deletions++
            hold?.await()
            failWith?.let { throw KvizicException(it) }
        }
    }
}
