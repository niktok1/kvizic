package io.ntole.kvizic.core.lobby

import kotlinx.serialization.Serializable

/** Opens a lobby with the bearer as host. */
@Serializable
public data class CreateLobbyRequest(
    public val settings: LobbySettingsDto = LobbySettingsDto(),
)

/** A seat in the lobby with [code], six digits; spaces and dashes are ignored. */
@Serializable
public data class JoinLobbyRequest(
    public val code: String,
)

/**
 * A one-time ticket for the realtime socket, bound to the player, their session and [lobbyId]. It is the
 * socket's only credential, sent in the first frame, and lives [ticketExpiresInMs]: a rejoin asks for a
 * new one. [toString] hides it.
 */
@Serializable
public data class TicketDto(
    public val lobbyId: String,
    public val code: String,
    public val ticket: String,
    public val ticketExpiresInMs: Long,
) {
    public override fun toString(): String =
        "TicketDto(lobbyId=$lobbyId, code=$code, ticket=***, ticketExpiresInMs=$ticketExpiresInMs)"
}

/** The open public lobbies, and how many players are online and searching right now. */
@Serializable
public data class LobbyListDto(
    public val lobbies: List<PublicLobbyDto> = emptyList(),
    public val online: Int = 0,
    public val searching: Int = 0,
)

/** One public lobby as the list shows it. */
@Serializable
public data class PublicLobbyDto(
    public val code: String,
    public val hostName: String,
    public val hostAvatar: String,
    public val players: Int,
    public val maxPlayers: Int,
    public val inGame: Boolean,
    public val settings: LobbySettingsDto,
)
