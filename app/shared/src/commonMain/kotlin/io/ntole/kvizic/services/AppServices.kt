package io.ntole.kvizic.services

import io.ntole.kvizic.core.domain.playgames.LinkPlayGames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * What the app does by itself, with no screen asking, for as long as it runs: one for the app's life, as
 * the analytics are, so a rotation's new activity finds it started. From the launch on, the player Play
 * Games signed in by itself is signed in to the server, for the session stored then and every one after
 * it ([LinkPlayGames.run]).
 *
 * [foreground] is the platform lifecycle's start, which `App` tells it of; the first is the launch.
 * Everything runs in [scope], off every screen, and nothing it does shows but whose profile Home reads.
 */
class AppServices(
    private val linkPlayGames: LinkPlayGames,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var started = false

    /** The app came to the foreground: the first time, at launch, it starts what runs by itself. */
    fun foreground() {
        if (started) return
        started = true
        scope.launch { linkPlayGames.run() }
    }
}
