package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.LeaveReason
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration

/** Everything a lobby's loop acts on, one at a time. */
sealed interface LobbyCommand {
    /**
     * Holds a seat for [seat] until their socket comes with a ticket, or keeps a member's while they
     * rejoin. Answered through [reply].
     */
    data class Reserve(
        val seat: Seat,
        val reply: CompletableDeferred<ReserveResult>,
    ) : LobbyCommand

    /** A socket with a redeemed ticket for this lobby. */
    data class Attach(
        val connection: LobbyConnection,
    ) : LobbyCommand

    /** A socket closed, for whatever reason. */
    data class Detach(
        val connection: LobbyConnection,
    ) : LobbyCommand

    /**
     * A frame from [connection], stamped as it came off the network: [receivedAt] times an answer, and
     * [rtt] is the connection's measured round trip then.
     */
    data class FromClient(
        val connection: LobbyConnection,
        val message: ClientMessage,
        val receivedAt: ComparableTimeMark,
        val rtt: Duration = Duration.ZERO,
    ) : LobbyCommand

    /**
     * Takes [playerId] out of the lobby at once, for a reason outside it: another lobby, a logout. With
     * [sessionId], only while their seat is that session's: a logout on one device leaves the other be.
     */
    data class Remove(
        val playerId: String,
        val reason: LeaveReason,
        val sessionId: String? = null,
    ) : LobbyCommand

    /** How many are online and searching, for waiting lobbies to show. */
    data class Presence(
        val online: Int,
        val searching: Int,
    ) : LobbyCommand

    /** The server is stopping by [deadline]: open nothing new, and let a running game end in time. */
    data class Drain(
        val deadline: ComparableTimeMark,
    ) : LobbyCommand

    /** Checks the lobby's deadlines: seats held too long, an absent host, idleness. */
    data object Tick : LobbyCommand

    /** The phase timer set under [epoch] ran out; a later phase ignores it. */
    data class PhaseTimeout(
        val epoch: Long,
    ) : LobbyCommand

    /** The questions a start asked for under [epoch], or why there are none. */
    data class QuestionsLoaded(
        val epoch: Long,
        val result: Result<PickResult>,
    ) : LobbyCommand
}

/** Who a reservation is for, the name and avatar the lobby shows for them, and the experience their level is. */
data class Seat(
    val playerId: String,
    val sessionId: String,
    val name: String,
    val avatar: String,
    val xp: Int = 0,
)

sealed interface ReserveResult {
    data object Reserved : ReserveResult

    data object Full : ReserveResult

    data object Banned : ReserveResult

    /** The lobby is closing, or has. */
    data object Closed : ReserveResult

    data object Draining : ReserveResult
}

/** What came of [Lobby.deliver]. */
enum class Delivery {
    /** In the inbox, for the lobby's loop to handle. */
    QUEUED,

    /** The inbox stayed full for the whole wait. */
    NO_ROOM,

    /** The lobby has closed, and handles nothing more. */
    CLOSED,
}
