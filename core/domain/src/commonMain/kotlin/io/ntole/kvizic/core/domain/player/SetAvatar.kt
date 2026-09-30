package io.ntole.kvizic.core.domain.player

import io.ntole.kvizic.core.domain.session.SessionRepository

/** Picks the player's avatar, a session ensured first, as [GetProfile] ensures one. */
public class SetAvatar(
    private val players: PlayerRepository,
    private val session: SessionRepository,
) {
    /** @throws io.ntole.kvizic.core.domain.error.KvizicException as [PlayerRepository.setAvatar] does. */
    public suspend operator fun invoke(avatarId: String): Profile {
        session.ensure()
        return players.setAvatar(avatarId)
    }
}
