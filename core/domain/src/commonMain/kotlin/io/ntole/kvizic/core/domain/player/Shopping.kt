package io.ntole.kvizic.core.domain.player

import io.ntole.kvizic.core.domain.session.SessionRepository

/** The shop: what it sells, buying with coins and wearing what was bought, a session ensured first. */
public class Shopping(
    private val players: PlayerRepository,
    private val session: SessionRepository,
) {
    /** @throws io.ntole.kvizic.core.domain.error.KvizicException as [PlayerRepository.shop] does. */
    public suspend fun items(): List<ShopItem> {
        session.ensure()
        return players.shop()
    }

    /** @throws io.ntole.kvizic.core.domain.error.KvizicException as [PlayerRepository.buy] does. */
    public suspend fun buy(itemId: String): Profile {
        session.ensure()
        return players.buy(itemId)
    }

    /** @throws io.ntole.kvizic.core.domain.error.KvizicException as [PlayerRepository.wear] does. */
    public suspend fun wear(
        frameId: String?,
        titleId: String?,
    ): Profile {
        session.ensure()
        return players.wear(frameId, titleId)
    }
}
