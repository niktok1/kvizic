package io.ntole.kvizic.core.data.player

import io.ntole.kvizic.core.data.mapper.toDomain
import io.ntole.kvizic.core.data.session.DefaultSessionRepository
import io.ntole.kvizic.core.data.session.withSessionRecovery
import io.ntole.kvizic.core.domain.player.PlayerRepository
import io.ntole.kvizic.core.domain.player.Profile
import io.ntole.kvizic.core.domain.player.ShopItem
import io.ntole.kvizic.core.network.api.PlayerApi

/**
 * The player's profile, recovering once from a session the server has stopped accepting (see
 * [withSessionRecovery]). A recovered session is a fresh guest, so what comes back is that guest's, not
 * the dead session's. Picking an avatar, buying (an item owned is not charged again) and wearing are safe to
 * resend, so they recover the same way.
 */
public class DefaultPlayerRepository(
    private val api: PlayerApi,
    private val session: DefaultSessionRepository,
) : PlayerRepository {
    override suspend fun profile(): Profile = session.withSessionRecovery { api.me() }.toDomain()

    override suspend fun setAvatar(avatarId: String): Profile =
        session.withSessionRecovery { api.setAvatar(avatarId) }.toDomain()

    override suspend fun shop(): List<ShopItem> = session.withSessionRecovery { api.shop() }.items.map { it.toDomain() }

    override suspend fun buy(itemId: String): Profile = session.withSessionRecovery { api.buy(itemId) }.toDomain()

    override suspend fun wear(
        frameId: String?,
        titleId: String?,
        crownId: String?,
    ): Profile = session.withSessionRecovery { api.wear(frameId, titleId, crownId) }.toDomain()
}
