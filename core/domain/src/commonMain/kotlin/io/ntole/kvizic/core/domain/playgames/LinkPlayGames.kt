package io.ntole.kvizic.core.domain.playgames

import io.ntole.kvizic.core.domain.analytics.Analytics
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.session.CurrentSession
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Signs this device in with Google Play Games Services, so a player gets an account with nothing to
 * fill in, and their name from Play Games.
 *
 * [run] is the launch's: for the session stored then and every one after it, while who plays here is
 * unsettled ([PlayGamesRepository.isSettled]), a player Play Games signed in by itself is signed in to
 * the server with no tap; [automatically] is one such try. [manually] is a button's. Either way the
 * server's answer is stored in place of the device's session, [analytics] hear who plays now, and so
 * does [signedIn].
 *
 * One at a time: bound once for the app, so the launch's try and a tap cannot both sign in.
 */
public class LinkPlayGames(
    private val playGames: PlayGames,
    private val link: PlayGamesRepository,
    private val session: CurrentSession,
    private val analytics: Analytics,
) {
    private val mutex = Mutex()

    private val signIns =
        MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Whether this build has Play Games, for a screen to offer it. */
    public val available: Boolean get() = playGames.available

    /**
     * The player id of each sign-in that stored a session, the launch's or a button's, once, to whoever
     * listens then. It is how a screen showing the player's name hears of the name Play Games gave: a
     * sign-in that links the player playing keeps their id, so [CurrentSession.sessions] names the same
     * player as before.
     */
    public val signedIn: Flow<String> = signIns.asSharedFlow()

    /**
     * At launch: waits for a session, then, while who plays here is unsettled and Play Games says the
     * player is signed in to it, signs in to the server with it. Returns whether it did. Never throws but
     * the caller's cancellation: a refusal, Google not answering or being offline leaves the player as
     * they are and shows nothing, and the next session or launch tries again.
     */
    public suspend fun automatically(): Boolean {
        if (!playGames.available) return false
        // Once a session exists: a first launch mints one only as the player starts to play.
        session.sessions.first()
        return tryUnsettled()
    }

    /**
     * From launch on, never returning but by the caller's cancellation: [automatically]'s try for the
     * session stored then, and again for every session stored after it. A dead session found after the
     * launch's try (a dev server's data reset, a refresh token expired) is replaced by a fresh guest and
     * unsettles the device, so that guest is signed in with Play Games at once, not at the next launch, or,
     * replaced while the app was in the background, as it comes back ([cameToForeground]). A session the
     * player chose settles it, so its try asks Play Games nothing.
     */
    public suspend fun run() {
        if (!playGames.available) return
        session.sessions.collect { tryUnsettled() }
    }

    /**
     * The app came back to the foreground: [automatically]'s try once more, for the session stored now.
     * Play Games is asked through the activity on screen, so a session stored while the app was in the
     * background found nobody to ask and is signed in now. Returns whether it was. A settled device asks
     * Play Games nothing, and one with no session yet signs in to nothing, as a launch waits for one.
     */
    public suspend fun cameToForeground(): Boolean {
        if (!playGames.available || session.current() == null) return false
        return tryUnsettled()
    }

    private suspend fun tryUnsettled(): Boolean =
        mutex.withLock {
            if (link.isSettled() || !playGames.isAuthenticated()) return@withLock false
            try {
                signIn(automatic = true)
            } catch (failed: KvizicException) {
                false
            }
        }

    /**
     * A button's: asks the player to sign in to Play Games unless they are, then signs in to the server
     * with it. Returns false, with nothing sent, when they did not sign in to Play Games, and with nothing
     * stored, when the device became another player while it was in flight.
     *
     * @throws KvizicException when the server could not sign them in, the stored session left as it was.
     */
    public suspend fun manually(): Boolean {
        if (!playGames.available) return false
        return mutex.withLock {
            if (!playGames.isAuthenticated() && !playGames.signIn()) return@withLock false
            signIn(automatic = false)
        }
    }

    /** Whether the device plays as the Play Games player now: false when it became another meanwhile. */
    private suspend fun signIn(automatic: Boolean): Boolean {
        val before = session.current()
        val code =
            playGames.serverAuthCode()
                ?: throw KvizicException(CoreError.PLAY_GAMES_UNAVAILABLE, "Play Games gave no server auth code")
        val player = link.signIn(code) ?: return false
        signIns.tryEmit(player)
        analytics.identify(player)
        analytics.track(
            AnalyticsEvent.PLAY_GAMES_SIGNED_IN,
            mapOf(AnalyticsProperty.AUTOMATIC to automatic, AnalyticsProperty.SWITCHED to (player != before)),
        )
        return true
    }
}
