package io.ntole.kvizic.core.data.player

import io.ntole.kvizic.core.data.mapper.toDomain
import io.ntole.kvizic.core.data.session.DefaultSessionRepository
import io.ntole.kvizic.core.data.session.withSessionRecovery
import io.ntole.kvizic.core.domain.player.PlayerRepository
import io.ntole.kvizic.core.domain.player.Profile
import io.ntole.kvizic.core.network.api.PlayerApi

/**
 * The player's profile, recovering once from a session the server has stopped accepting (see
 * [withSessionRecovery]). A recovered session is a fresh guest, so what comes back is that guest's, not
 * the dead session's. Picking an avatar is safe to resend, so it recovers the same way.
 */
public class DefaultPlayerRepository(
    private val api: PlayerApi,
    private val session: DefaultSessionRepository,
) : PlayerRepository {
    override suspend fun profile(): Profile = session.withSessionRecovery { api.me() }.toDomain()

    override suspend fun setAvatar(avatarId: String): Profile =
        session.withSessionRecovery { api.setAvatar(avatarId) }.toDomain()
}
