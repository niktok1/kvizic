package io.ntole.kvizic.services

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.games.GamesSignInClient
import io.ntole.kvizic.core.domain.playgames.PlayGames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.android.gms.games.PlayGames as GooglePlayGames

/**
 * Google Play Games Services v2 on this device, for the game server whose OAuth client is
 * [serverClientId]. Each call asks the activity on screen for its sign-in client, on the main thread, and
 * answers a failure as nothing done: none on screen, Play Games refusing, or its task failing.
 *
 * Made only once `PlayGamesSdk.initialize` has run, which signs a player with a profile in by itself as the
 * game starts.
 */
internal class AndroidPlayGames(
    private val activities: ActivityTracker,
    private val serverClientId: String,
) : PlayGames {
    override val available: Boolean = true

    @Volatile
    override var lastFailure: String? = null
        private set

    override suspend fun isAuthenticated(): Boolean = ask { isAuthenticated().resultOrNull(::fail)?.isAuthenticated } == true

    override suspend fun signIn(): Boolean = ask { signIn().resultOrNull(::fail)?.isAuthenticated } == true

    override suspend fun serverAuthCode(): String? =
        ask { requestServerSideAccess(serverClientId, false).resultOrNull(::fail) }?.takeIf { it.isNotBlank() }

    private fun fail(cause: Exception?) {
        lastFailure = cause?.let { (it as? ApiException)?.let { api -> "API_${api.statusCode}" } ?: it::class.simpleName }
    }

    private suspend fun <T> ask(question: suspend GamesSignInClient.() -> T?): T? =
        withContext(Dispatchers.Main) {
            lastFailure = null
            val activity =
                activities.current() ?: run {
                    lastFailure = "NO_ACTIVITY"
                    return@withContext null
                }
            GooglePlayGames.getGamesSignInClient(activity).question()
        }
}
