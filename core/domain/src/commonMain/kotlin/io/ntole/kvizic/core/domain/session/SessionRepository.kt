package io.ntole.kvizic.core.domain.session

/**
 * The player's session, with nothing asked of the player: [ensure] mints a server-issued guest on the
 * first run and reuses the stored session afterwards. Callers only ever see a player id; what changes
 * whose session is stored (a Play Games sign-in, a logout, a deletion) goes through the repositories
 * that own it.
 */
public interface SessionRepository {
    /** The current player id, a session minted first if there is none yet. */
    public suspend fun ensure(): String
}
