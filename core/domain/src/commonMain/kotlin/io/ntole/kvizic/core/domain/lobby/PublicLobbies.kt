package io.ntole.kvizic.core.domain.lobby

/** An open public lobby, as the list shows it. */
public data class PublicLobby(
    val code: String,
    val hostName: String,
    val hostAvatar: String,
    val players: Int,
    val maxPlayers: Int,
    val inGame: Boolean,
    val settings: LobbySettings,
)

/** The open public lobbies, and how many play and look for a game. */
public data class PublicLobbies(
    val lobbies: List<PublicLobby>,
    val online: Int,
    val searching: Int,
)

/** The public lobbies, read afresh each time. */
public interface PublicLobbyRepository {
    public suspend fun list(): PublicLobbies

    /**
     * The room with [code] as the list shows it, public or private: what a player sees before they join. Throws
     * a `KvizicException` for a code naming no room.
     */
    public suspend fun preview(code: String): PublicLobby
}
