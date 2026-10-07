package io.ntole.kvizic.core.domain.playgames

/**
 * Google Play Games Services on this device: the platform's own sign-in, which vouches for who the
 * player is to the game's server. Only an Android build with Play Games set up has it; everywhere else it
 * is [None], which is never [available].
 *
 * The player's name is not read here: the server reads it from Google at each sign-in, so nothing the
 * client says about who the player is gets taken on trust.
 *
 * Nothing here throws but the caller's cancellation: a failure is a sign-in that did not happen, or no
 * code.
 */
public interface PlayGames {
    /** Whether this build has Play Games at all: an Android build given its project's ids. */
    public val available: Boolean

    /**
     * Whether the player is signed in to Play Games on this device. Play Games v2 signs a player with a
     * profile in by itself as the game starts, so asked at launch this is that sign-in's outcome.
     */
    public suspend fun isAuthenticated(): Boolean

    /** Asks the player to sign in to Play Games, as a tap asks: whether they are signed in after it. */
    public suspend fun signIn(): Boolean

    /**
     * A one-time server auth code for the game's server, which exchanges it with Google for the player
     * it names, or null when Play Games gave none. A new one each time: the server spends it.
     */
    public suspend fun serverAuthCode(): String?

    /** Why the last call answered nothing, when the platform said: for analytics, never for a decision. */
    public val lastFailure: String? get() = null

    public companion object {
        /** No Play Games: a build or a platform without it, and the tests. */
        public val None: PlayGames = NoPlayGames
    }
}

private object NoPlayGames : PlayGames {
    override val available: Boolean = false

    override suspend fun isAuthenticated(): Boolean = false

    override suspend fun signIn(): Boolean = false

    override suspend fun serverAuthCode(): String? = null
}
