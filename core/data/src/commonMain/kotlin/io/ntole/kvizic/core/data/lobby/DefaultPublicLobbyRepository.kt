package io.ntole.kvizic.core.data.lobby

import io.ntole.kvizic.core.data.session.DefaultSessionRepository
import io.ntole.kvizic.core.data.session.withSessionRecovery
import io.ntole.kvizic.core.domain.lobby.PublicLobbies
import io.ntole.kvizic.core.domain.lobby.PublicLobby
import io.ntole.kvizic.core.domain.lobby.PublicLobbyRepository
import io.ntole.kvizic.core.network.api.LobbyApi

/** The public lobbies, read with the player's session, which a first launch mints. */
public class DefaultPublicLobbyRepository(
    private val api: LobbyApi,
    private val session: DefaultSessionRepository,
) : PublicLobbyRepository {
    override suspend fun list(): PublicLobbies {
        session.ensure()
        return session.withSessionRecovery { api.publicLobbies() }.toDomain()
    }

    override suspend fun preview(code: String): PublicLobby {
        session.ensure()
        return session.withSessionRecovery { api.preview(code) }.toDomain()
    }
}
