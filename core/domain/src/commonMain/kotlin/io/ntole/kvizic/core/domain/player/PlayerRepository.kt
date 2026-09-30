package io.ntole.kvizic.core.domain.player

/** The session player, as the server knows them. Implemented in `:core:data`. */
public interface PlayerRepository {
    /**
     * The session player's profile, read from the server every time.
     *
     * @throws io.ntole.kvizic.core.domain.error.KvizicException on any failure.
     */
    public suspend fun profile(): Profile

    /**
     * Picks [avatarId], one of the game's avatars, for the session player, and returns the profile as it
     * then stands.
     *
     * @throws io.ntole.kvizic.core.domain.error.KvizicException on any failure: `INVALID_AVATAR` for an id
     *   the server does not have.
     */
    public suspend fun setAvatar(avatarId: String): Profile
}
