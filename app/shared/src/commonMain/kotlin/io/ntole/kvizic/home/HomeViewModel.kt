package io.ntole.kvizic.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ntole.kvizic.core.domain.analytics.Analytics
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.player.GetProfile
import io.ntole.kvizic.core.domain.session.CurrentSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The placeholder Home screen's player: their profile, read each time Home is shown ([shown]) and again
 * whenever the device becomes another player with nothing asked of the screen, a launch's Play Games
 * sign-in or a dead session replaced by a fresh guest ([CurrentSession.sessions]), so the name shown is
 * always the one who plays. A failed read is reported to [analytics] as shown.
 */
class HomeViewModel(
    private val getProfile: GetProfile,
    private val session: CurrentSession,
    private val analytics: Analytics,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeState())

    val state: StateFlow<HomeState> = mutableState.asStateFlow()

    private var reading: Job? = null

    /** Whom the last read went out as, a failed one's too: a session a read of its own minted is no news. */
    private var lastReadAs: String? = null

    init {
        viewModelScope.launch {
            session.sessions.collect { player ->
                // A read in flight reads again by itself once it sees the player changed under it.
                if (player == lastReadAs || reading?.isActive == true) return@collect
                // Another player's name must not stay on screen while theirs is read.
                if (mutableState.value.profile?.playerId != player) mutableState.update { it.copy(profile = null) }
                refresh()
            }
        }
    }

    /** Home is shown: the profile is read again, since a game played meanwhile moves the stats. */
    fun shown() = refresh()

    /** The failure's Try again. */
    fun retry() = refresh()

    /**
     * Reads the profile, unless a read is in flight already; then reads again while the device plays as
     * another player than the profile read, which changed while the read was in flight, a few times at most.
     */
    private fun refresh() {
        if (reading?.isActive == true) return
        reading =
            viewModelScope.launch {
                var reads = 0
                do {
                    read()
                    reads++
                } while (reads < MAX_READS && isStale())
            }
    }

    /** Whether the profile shown is another player's than the one who plays now, with nothing failed. */
    private fun isStale(): Boolean =
        mutableState.value.failure == null && mutableState.value.profile?.playerId != session.current()

    private suspend fun read() {
        mutableState.update { it.copy(loading = true, failure = null) }
        try {
            val profile = getProfile()
            mutableState.update { it.copy(profile = profile, loading = false) }
        } catch (failure: KvizicException) {
            analytics.track(
                AnalyticsEvent.ERROR_SHOWN,
                mapOf(AnalyticsProperty.CODE to failure.error.name, AnalyticsProperty.ACTION to ACTION),
            )
            mutableState.update { it.copy(loading = false, failure = HomeFailure(failure.error, failure.retryAfter)) }
        } finally {
            lastReadAs = session.current()
        }
    }

    private companion object {
        /** What failed, as analytics name it. */
        const val ACTION = "profile"

        /** The most reads one refresh makes: the player changing under each is not worth chasing further. */
        const val MAX_READS = 3
    }
}
