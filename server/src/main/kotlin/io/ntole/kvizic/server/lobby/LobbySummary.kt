package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.region.Country

/**
 * What a lobby shows outside itself, published after every command it handles: the public list, Quick
 * play and presence read these, never a lobby's own state.
 */
data class LobbySummary(
    val id: String,
    val code: String,
    val kind: LobbyKind,
    val settings: LobbySettingsDto,
    val hostName: String,
    val hostAvatar: String,
    /** Seats taken, held ones included. */
    val seatsTaken: Int,
    /** Members with an open socket. */
    val connected: Set<String>,
    val inGame: Boolean,
    val draining: Boolean,
    val closed: Boolean,
    /** When the lobby opened, in the order lobbies opened: less is older. */
    val openedOrder: Long,
    /**
     * The countries its connected members play from: what Quick play finds a room of a player's own country
     * by. Empty when nobody is connected.
     */
    val connectedCountries: Set<Country> = emptySet(),
) {
    val hasRoom: Boolean get() = seatsTaken < settings.maxPlayers
}
