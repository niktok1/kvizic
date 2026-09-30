package io.ntole.kvizic.core.domain.player

import io.ntole.kvizic.core.domain.session.SessionRepository

/**
 * Reads the player's profile, a session ensured first. On a cold first launch there is none yet, and
 * ensuring it here sends the first read with a bearer instead of having it refused and retried.
 */
public class GetProfile(
    private val players: PlayerRepository,
    private val session: SessionRepository,
) {
    /** @throws io.ntole.kvizic.core.domain.error.KvizicException as [PlayerRepository.profile] does. */
    public suspend operator fun invoke(): Profile {
        session.ensure()
        return players.profile()
    }
}
