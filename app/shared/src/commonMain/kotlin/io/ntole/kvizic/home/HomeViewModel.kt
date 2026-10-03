package io.ntole.kvizic.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ntole.kvizic.core.domain.analytics.Analytics
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.player.GetProfile
import io.ntole.kvizic.core.domain.player.SetAvatar
import io.ntole.kvizic.core.domain.playgames.LinkPlayGames
import io.ntole.kvizic.core.domain.session.CurrentSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

/**
 * The placeholder Home screen's player: their profile, read each time Home is shown ([shown]) and again
 * whenever who plays changes with nothing asked of the screen, so the name shown is always theirs: the
 * device becoming another player, a launch's Play Games sign-in as someone else or a dead session replaced
 * by a fresh guest ([CurrentSession.sessions]), and any Play Games sign-in ([LinkPlayGames.signedIn]),
 * which renames a player it links without changing who they are. A failed read is reported to [analytics]
 * as shown. A guest may sign in with Play Games from here ([linkPlayGames]), which the sign-in's read then
 * shows as linked.
 */
class HomeViewModel(
    private val getProfile: GetProfile,
    private val setAvatar: SetAvatar,
    private val session: CurrentSession,
    private val linkPlayGames: LinkPlayGames,
    private val analytics: Analytics,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeState(playGamesAvailable = linkPlayGames.available))

    val state: StateFlow<HomeState> = mutableState.asStateFlow()

    private var reading: Job? = null

    /** Whom the last read went out as, a failed one's too: a session a read of its own minted is no news. */
    private var lastReadAs: String? = null

    /** Whether a Play Games sign-in came after the read in flight went out, which then read the name before it. */
    private var readAgain = false

    /** The wait for the player to settle on an avatar, which each new pick starts again. */
    private var settling: Job? = null

    /** Held while an avatar is sent, so picks reach the server one after another, in order. */
    private val avatarSends = Mutex()

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
        viewModelScope.launch {
            // A sign-in that links the player playing keeps their id, so the sessions above name the same
            // player and read nothing, but it gives them their Play Games name.
            linkPlayGames.signedIn.collect {
                readAgain = true
                refresh()
            }
        }
    }

    /** Home is shown: the profile is read again, since a game played meanwhile moves the stats. */
    fun shown() = refresh()

    /** The failure's Try again. */
    fun retry() = refresh()

    /**
     * The guest's Save progress, Home's or the profile's: asks Play Games, then the server, one at a time. A
     * sign-in that worked is read through [LinkPlayGames.signedIn]; one Play Games or the server did not make
     * is shown until [dismissPlayGamesFailure] or the next try, so a tap is never silent.
     */
    fun linkPlayGames() {
        if (mutableState.value.linkingPlayGames) return
        mutableState.update { it.copy(linkingPlayGames = true, playGamesFailure = null) }
        viewModelScope.launch {
            try {
                linkPlayGames.manually()
            } catch (failure: KvizicException) {
                analytics.track(
                    AnalyticsEvent.ERROR_SHOWN,
                    mapOf(AnalyticsProperty.CODE to failure.error.name, AnalyticsProperty.ACTION to PLAY_GAMES_ACTION),
                )
                mutableState.update { it.copy(playGamesFailure = HomeFailure(failure.error, failure.retryAfter)) }
            } finally {
                mutableState.update { it.copy(linkingPlayGames = false) }
            }
        }
    }

    /** The Play Games failure is taken down. */
    fun dismissPlayGamesFailure() = mutableState.update { it.copy(playGamesFailure = null) }

    /**
     * Shows [avatarId] as the player's at once, and asks the server for it once they settle on it: no other
     * picked for [AVATAR_SETTLE], or the profile left ([keepAvatar]). One trying every avatar sends only the
     * one they stop at, not one request a tap, which spent the server's budget for picks.
     */
    fun changeAvatar(avatarId: String) {
        val state = mutableState.value
        if ((state.changingAvatar ?: state.profile?.avatarId) == avatarId) return
        mutableState.update { it.copy(changingAvatar = avatarId, avatarFailure = null) }
        settling?.cancel()
        settling =
            viewModelScope.launch {
                delay(AVATAR_SETTLE)
                sendAvatar()
            }
    }

    /** The profile is left, or the app: the avatar picked is sent now, not once the wait is over. */
    fun keepAvatar() {
        if (mutableState.value.changingAvatar == null) return
        settling?.cancel()
        settling = viewModelScope.launch { sendAvatar() }
    }

    /**
     * Sends the avatar picked last, unless the server has it already, after any sent before it: one sent is
     * never cancelled, so the server keeps the last pick, and the profile it answers shows.
     */
    private suspend fun sendAvatar() =
        avatarSends.withLock {
            val picked = mutableState.value.changingAvatar ?: return@withLock
            if (picked == mutableState.value.profile?.avatarId) {
                mutableState.update { it.copy(changingAvatar = null) }
                return@withLock
            }
            withContext(NonCancellable) {
                try {
                    val profile = setAvatar(picked)
                    mutableState.update {
                        it.copy(profile = profile, changingAvatar = it.changingAvatar.unless(picked))
                    }
                } catch (failure: KvizicException) {
                    analytics.track(
                        AnalyticsEvent.ERROR_SHOWN,
                        mapOf(AnalyticsProperty.CODE to failure.error.name, AnalyticsProperty.ACTION to AVATAR_ACTION),
                    )
                    mutableState.update {
                        it.copy(
                            changingAvatar = it.changingAvatar.unless(picked),
                            avatarFailure = HomeFailure(failure.error, failure.retryAfter),
                        )
                    }
                }
            }
        }

    /** This pick, or none once it is [sent]: one picked meanwhile is still to send. */
    private fun String?.unless(sent: String): String? = takeUnless { it == sent }

    /**
     * Reads the profile, unless a read is in flight already; then reads again while the device plays as
     * another player than the profile read, which changed while the read was in flight, or a Play Games
     * sign-in came while it was, a few times at most.
     */
    private fun refresh() {
        if (reading?.isActive == true) return
        reading =
            viewModelScope.launch {
                var reads = 0
                do {
                    readAgain = false
                    read()
                    reads++
                } while (reads < MAX_READS && (readAgain || isStale()))
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
        const val AVATAR_ACTION = "avatar"
        const val PLAY_GAMES_ACTION = "play_games"

        /** The most reads one refresh makes: the player changing under each is not worth chasing further. */
        const val MAX_READS = 3

        /** How long an avatar picked stands with no other picked before it is sent. */
        val AVATAR_SETTLE = 3.seconds
    }
}
