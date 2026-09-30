package io.ntole.kvizic.core.domain.lobby

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Why this device is no longer in a lobby. */
public enum class LobbyExit {
    /** The player left. */
    LEFT,

    /** The host removed them; the lobby will not take them back while it lasts. */
    KICKED,

    /** The lobby closed: everyone left, it sat idle, or it lived its longest. */
    LOBBY_CLOSED,

    /** The lobby is gone, and so is the seat. */
    LOBBY_GONE,

    /** The same player opened the lobby on another device. */
    REPLACED,

    /** The session ended: a logout, or the account deleted. */
    SESSION_ENDED,

    /** The server restarted; games do not survive it. */
    SERVER_RESTARTING,

    /** This build is too old for the server. */
    UPGRADE_REQUIRED,

    /** No connection could be made, however often tried. */
    CONNECTION_LOST,
}

/** What this device knows of the lobby it is in, if any. */
public sealed interface LobbySessionState {
    /** In no lobby. */
    public data object Idle : LobbySessionState

    /** A seat is being taken, or the socket opened for the first time. */
    public data class Joining(
        val code: String?,
    ) : LobbySessionState

    /**
     * In [lobby], playing or waiting as [phase] says. [reconnecting] while the connection is being made
     * again: what shows is how things stood, and nothing sent meanwhile is lost for good.
     */
    public data class InLobby(
        val you: String,
        val lobby: Lobby,
        val phase: GamePhase,
        val reconnecting: Boolean,
    ) : LobbySessionState

    /** Out of the lobby, for [exit]. */
    public data class Ended(
        val exit: LobbyExit,
        val code: String?,
    ) : LobbySessionState
}

/** What passes without changing the lobby: a reaction, a notice, a command the lobby refused. */
public sealed interface LobbyEvent {
    public data class Reacted(
        val playerId: String,
        val reaction: String,
    ) : LobbyEvent

    public data class Notice(
        val kind: NoticeKind,
    ) : LobbyEvent

    /** A command of this player's the lobby refused, and why. */
    public data class Refused(
        val command: LobbyCommandKind,
        val reason: RefusalReason,
    ) : LobbyEvent

    /** How many play now, and how many look for a game. */
    public data class Presence(
        val online: Int,
        val searching: Int,
    ) : LobbyEvent
}

public enum class NoticeKind { SERVER_RESTARTING, TOPICS_TOPPED_UP, GAME_SHORTENED, UNKNOWN }

public enum class LobbyCommandKind { ANSWER, SETTINGS, START, KICK, TRANSFER_HOST, BACK_TO_LOBBY }

public enum class RefusalReason {
    NOT_HOST,
    WRONG_PHASE,
    ALREADY_ANSWERED,
    TOO_LATE,
    INVALID_SETTINGS,
    NO_SUCH_PLAYER,
    NOT_PLAYING,
    RATE_LIMITED,
    SERVER_DRAINING,
    UNKNOWN,
}

/**
 * The lobby this device is in, for the app's whole life: one at a time, kept through lost connections,
 * and given up only when the player leaves or the lobby lets them go. Taking a seat throws a
 * `KvizicException` naming why it could not; commands never throw, and a refusal comes as an event.
 */
public interface LobbySession {
    public val state: StateFlow<LobbySessionState>
    public val events: Flow<LobbyEvent>

    public suspend fun create(settings: LobbySettings)

    public suspend fun join(code: String)

    public suspend fun quickPlay()

    public suspend fun solo()

    /** Locks in [option] for the question asked now; shows at once, and is sent again if the connection drops. */
    public fun answer(option: Int)

    public fun start()

    public fun updateSettings(settings: LobbySettings)

    public fun kick(playerId: String)

    public fun transferHost(playerId: String)

    public fun backToLobby()

    public fun react(reaction: String)

    /** Leaves the lobby for good, gives up joining one, or clears how the last one ended. */
    public fun leave()

    /** The network came back, or the app to the foreground: a connection being made again is tried now. */
    public fun wake()
}
