package io.ntole.kvizic.core.data.session

import io.ntole.kvizic.core.network.InMemoryTokenStorage
import io.ntole.kvizic.core.network.TokenStorage
import io.ntole.kvizic.core.network.environment.KvizicEnvironment

/**
 * Whether who plays on this device is settled for [environment]'s server, so a launch signs in with Play
 * Games no more, kept in [storage] beside the session and under a key of each environment's own, as the
 * session is.
 *
 * [DefaultSessionRepository] keeps it: a Play Games sign-in, a logout and an account's deletion settle it,
 * each the player's own doing, and a session that died and was replaced by a fresh guest forgets it, so
 * that device signs in with Play Games again. A fresh install has none.
 */
public class PlayGamesSettled(
    private val storage: TokenStorage = InMemoryTokenStorage(),
    environment: KvizicEnvironment = KvizicEnvironment.LOCAL,
) {
    private val key = keyFor(environment)

    public fun isSettled(): Boolean = storage.read(key) != null

    internal suspend fun settle(): Unit = storage.write(key, SETTLED)

    internal suspend fun forget(): Unit = storage.remove(key)

    internal companion object {
        private const val SETTLED = "settled"

        fun keyFor(environment: KvizicEnvironment): String = "kvizic.playgames.settled.${environment.name.lowercase()}"
    }
}
