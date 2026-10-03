package io.ntole.kvizic.services

import io.ntole.kvizic.core.domain.lobby.LobbySession
import io.ntole.kvizic.core.domain.playgames.LinkPlayGames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * What the app does by itself, with no screen asking, for as long as it runs: one for the app's life, as
 * the analytics are, so a rotation's new activity finds it started. From the launch on, the player Play
 * Games signed in by itself is signed in to the server, for the session stored then and every one after
 * it ([LinkPlayGames.run]), and one stored while the app was in the background as it comes back. The launch
 * also takes back a seat the server holds for the player still ([LobbySession.rejoin]): the system ended the
 * app in the background mid-game, and the player comes back into the game, not onto Home.
 *
 * [foreground] is the platform lifecycle's start, which `App` tells it of; the first is the launch.
 * Everything runs in [scope], off every screen, and nothing it does shows but whose profile Home reads.
 */
class AppServices(
    private val linkPlayGames: LinkPlayGames,
    private val lobby: LobbySession,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var started = false

    /**
     * The app came to the foreground: the first time, at launch, it starts what runs by itself; each time
     * after, it does what could not be done in the background ([LinkPlayGames.cameToForeground]).
     */
    fun foreground() {
        if (started) {
            scope.launch { linkPlayGames.cameToForeground() }
            return
        }
        started = true
        scope.launch { lobby.rejoin() }
        scope.launch { linkPlayGames.run() }
    }
}
