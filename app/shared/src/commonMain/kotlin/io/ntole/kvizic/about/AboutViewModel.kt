package io.ntole.kvizic.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ntole.kvizic.core.domain.account.DeleteAccount
import io.ntole.kvizic.core.domain.analytics.Analytics
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.core.domain.error.DomainError
import io.ntole.kvizic.core.domain.error.KvizicException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * The About screen's one action, deleting the account ([DeleteAccount]): one at a time, and what became
 * of it in [deletion]. A failure is reported to [analytics] as shown; nothing is forgotten until the
 * server has said the account is gone.
 */
class AboutViewModel(
    private val deleteAccount: DeleteAccount,
    private val analytics: Analytics,
) : ViewModel() {
    private val state = MutableStateFlow<Deletion>(Deletion.Idle)

    val deletion: StateFlow<Deletion> = state.asStateFlow()

    /** Deletes the account, unless a deletion is in flight already. */
    fun delete() {
        if (state.value == Deletion.InFlight) return
        state.value = Deletion.InFlight
        viewModelScope.launch {
            state.value =
                try {
                    deleteAccount()
                    Deletion.Done
                } catch (failure: KvizicException) {
                    analytics.track(
                        AnalyticsEvent.ERROR_SHOWN,
                        mapOf(AnalyticsProperty.CODE to failure.error.name, AnalyticsProperty.ACTION to ACTION),
                    )
                    Deletion.Failed(failure.error, failure.retryAfter)
                }
        }
    }

    /** The screen went on once the account was deleted: the next visit starts with nothing said. */
    fun leftAfterDeletion() {
        if (state.value == Deletion.Done) state.value = Deletion.Idle
    }

    private companion object {
        /** What failed, as analytics name it. */
        const val ACTION = "delete_account"
    }
}

/** What became of the account's deletion. */
sealed interface Deletion {
    /** Nothing asked, or the last deletion's visit is over. */
    data object Idle : Deletion

    /** Sent, and not answered yet. */
    data object InFlight : Deletion

    /** The account is gone, and the device plays on as a fresh guest from the next call. */
    data object Done : Deletion

    /** Nothing was deleted, and [error] says why, with the wait a rate limit named, [retryAfter]. */
    data class Failed(
        val error: DomainError,
        val retryAfter: Duration? = null,
    ) : Deletion
}
