package io.ntole.kvizic.core.domain.playgames

/**
 * Signing in to the game's server with Play Games, and whether this device has settled who plays on it.
 * Implemented in `:core:data`.
 */
public interface PlayGamesRepository {
    /**
     * Whether who plays on this device is settled, so a launch signs in with Play Games no more: by a
     * Play Games sign-in, a logout or an account's deletion, each the player's own doing. A fresh install
     * is not, nor a device whose session died and was replaced by a fresh guest, and then a launch signs
     * in with Play Games by itself.
     */
    public fun isSettled(): Boolean

    /**
     * Signs in with [serverAuthCode], a Play Games server auth code: the server answers with a session of
     * the player Play Games names, which is stored in place of this device's and settles it. Returns the
     * player id of that session: the player playing before, now linked, or another the Play Games player
     * was linked to already. Returns null, with nothing stored, when the device became another player
     * while the sign-in was in flight, which stands.
     *
     * @throws io.ntole.kvizic.core.domain.error.KvizicException on any failure, the stored session left as
     *   it was: `PLAY_GAMES_CODE_REFUSED` for a code Google refused, `PLAY_GAMES_UNAVAILABLE` for a
     *   server that could not ask Google.
     */
    public suspend fun signIn(serverAuthCode: String): String?
}
