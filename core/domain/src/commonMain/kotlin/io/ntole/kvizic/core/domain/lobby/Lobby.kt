package io.ntole.kvizic.core.domain.lobby

/** Who may find a lobby: its code alone, or the public list and Quick play too. */
public enum class LobbyVisibility { PRIVATE, PUBLIC }

/** A lobby among friends, one strangers may join, or a solo run. */
public enum class LobbyKind { PRIVATE, PUBLIC, SOLO }

/**
 * What the host picked: how many questions and how long each, which topics (none for all of them), how
 * many seats, who may find the lobby, and whether a wrong answer costs points.
 */
public data class LobbySettings(
    val questionCount: Int = 10,
    val secondsPerQuestion: Int = 15,
    val topics: List<String> = emptyList(),
    val maxPlayers: Int = 8,
    val visibility: LobbyVisibility = LobbyVisibility.PRIVATE,
    val wrongAnswerPenalty: Boolean = true,
)

/** A member as the lobby shows them: their seat's colour comes from [seat]. */
public data class LobbyMember(
    val playerId: String,
    val name: String,
    val avatar: String,
    val seat: Int,
    val connected: Boolean,
    /** Still looking at the last game's results: not back in the lobby yet. */
    val onResults: Boolean,
    /** A player of the game under way, where there is one. */
    val playing: Boolean,
)

/** A lobby as it stands. */
public data class Lobby(
    val id: String,
    val code: String,
    val kind: LobbyKind,
    val settings: LobbySettings,
    val host: String?,
    val members: List<LobbyMember>,
) {
    public fun member(playerId: String): LobbyMember? = members.firstOrNull { it.playerId == playerId }
}
