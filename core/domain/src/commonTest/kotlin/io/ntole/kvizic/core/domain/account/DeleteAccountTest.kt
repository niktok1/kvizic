package io.ntole.kvizic.core.domain.account

import io.ntole.kvizic.core.domain.analytics.Analytics
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.KvizicException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Deleting the device's account, and what the analytics hear of it. */
class DeleteAccountTest {
    private val calls = mutableListOf<String>()
    private val accounts = FakeAccounts(calls)
    private val deleteAccount = DeleteAccount(accounts, RecordingAnalytics(calls))

    @Test
    fun `a deletion is the account's last event and then the analytics forget the player`() =
        runTest {
            deleteAccount()

            assertEquals(listOf("deleteAccount", AnalyticsEvent.ACCOUNT_DELETED, "reset"), calls)
        }

    @Test
    fun `a deletion that failed tells the analytics nothing`() =
        runTest {
            accounts.failWith = CoreError.NETWORK

            val failure = assertFailsWith<KvizicException> { deleteAccount() }

            assertEquals(CoreError.NETWORK, failure.error)
            assertEquals(listOf("deleteAccount"), calls)
        }

    private class FakeAccounts(
        private val calls: MutableList<String>,
    ) : AccountRepository {
        var failWith: CoreError? = null

        override suspend fun logOut() {
            calls += "logOut"
        }

        override suspend fun deleteAccount() {
            calls += "deleteAccount"
            failWith?.let { throw KvizicException(it) }
        }
    }

    private class RecordingAnalytics(
        private val calls: MutableList<String>,
    ) : Analytics by Analytics.None {
        override fun track(
            event: String,
            properties: Map<String, Any?>,
        ) {
            calls += event
        }

        override fun reset() {
            calls += "reset"
        }
    }
}
