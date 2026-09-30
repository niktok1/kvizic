package io.ntole.kvizic.core.protocol

import io.ntole.kvizic.core.lobby.LobbySettingsDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the server sends on the socket.
 *
 * A message with a [StateChange.v] changes the lobby's state, and every connected socket gets one for
 * each version in order, its content fitted to who reads it ([Picks] or [Progress]). So a client that
 * sees a version jump missed a frame, and sends [ClientMessage.Resync] for a [Snapshot]. Every connect
 * starts with a [Snapshot]; there is no replay. Timers are relative (`remainingMs`, from when the
 * server sent the message), never wall-clock, so no client clock matters.
 */
@Serializable
public sealed interface ServerMessage {
    /** A message that moves the lobby to version [v]. */
    public sealed interface StateChange : ServerMessage {
        public val v: Long
    }

    /** Right after a hello the server took: who the socket is, and how often to expect a ping. */
    @Serializable
    @SerialName("welcome")
    public data class Welcome(
        public val protocol: Int,
        public val you: String,
        public val pingEveryMs: Long,
    ) : ServerMessage

    /** The whole lobby as it stands at version [v]: sent on every connect, and on a resync. */
    @Serializable
    @SerialName("snapshot")
    public data class Snapshot(
        public val v: Long,
        public val you: String,
        public val lobby: LobbyView,
        public val phase: PhaseView,
    ) : ServerMessage

    /** Answer with [ClientMessage.Pong] and the same [seq]. [rttMs] is the trip the server measured last. */
    @Serializable
    @SerialName("ping")
    public data class Ping(
        public val seq: Long,
        public val rttMs: Long = 0,
    ) : ServerMessage

    /** The command [id] went through. */
    @Serializable
    @SerialName("ack")
    public data class Ack(
        public val id: Int,
    ) : ServerMessage

    /** The command [id] was refused, and changed nothing. No [id] for a frame that carried none. */
    @Serializable
    @SerialName("rejected")
    public data class Rejected(
        public val id: Int? = null,
        public val code: RejectCode = RejectCode.UNKNOWN,
    ) : ServerMessage

    @Serializable
    @SerialName("joined")
    public data class MemberJoined(
        override val v: Long,
        public val member: MemberView,
    ) : StateChange

    @Serializable
    @SerialName("left")
    public data class MemberLeft(
        override val v: Long,
        public val player: String,
        public val reason: LeaveReason = LeaveReason.UNKNOWN,
    ) : StateChange

    /** A member's state changed: connected or not, back from the results, a new avatar. */
    @Serializable
    @SerialName("member")
    public data class MemberUpdated(
        override val v: Long,
        public val member: MemberView,
    ) : StateChange

    @Serializable
    @SerialName("host")
    public data class HostChanged(
        override val v: Long,
        public val host: String,
    ) : StateChange

    @Serializable
    @SerialName("settings")
    public data class SettingsChanged(
        override val v: Long,
        public val settings: LobbySettingsDto,
    ) : StateChange

    /** The host started a game; it begins in [remainingMs]. A member still on the results can still join it. */
    @Serializable
    @SerialName("countdown")
    public data class CountdownStarted(
        override val v: Long,
        public val remainingMs: Long,
    ) : StateChange

    /** The game begins, with [players] playing it; every other member watches. */
    @Serializable
    @SerialName("started")
    public data class GameStarted(
        override val v: Long,
        public val gameId: String,
        public val players: List<String>,
        public val questionCount: Int,
    ) : StateChange

    /** A question, alone, to read; its answers follow in [readMs]. */
    @Serializable
    @SerialName("question")
    public data class QuestionShown(
        override val v: Long,
        public val question: QuestionView,
        public val readMs: Long,
    ) : StateChange

    /** The answers to question [index], in the order an answer names by position. The clock starts now. */
    @Serializable
    @SerialName("answers")
    public data class AnswersOpened(
        override val v: Long,
        public val index: Int,
        public val options: List<String>,
        public val remainingMs: Long,
        public val durationMs: Long,
    ) : StateChange

    /** To a player who has locked in: everyone's picks so far, theirs included. Never who is right. */
    @Serializable
    @SerialName("picks")
    public data class Picks(
        override val v: Long,
        public val index: Int,
        public val picks: List<PickView>,
    ) : StateChange

    /** To a player who has not locked in, and to those watching: who has answered, never what. */
    @Serializable
    @SerialName("progress")
    public data class Progress(
        override val v: Long,
        public val index: Int,
        public val answered: List<String>,
    ) : StateChange

    /** Time is up: the right answer, what everyone picked and scored, and the standings. */
    @Serializable
    @SerialName("reveal")
    public data class Revealed(
        override val v: Long,
        public val reveal: RevealView,
    ) : StateChange

    /** The final results. Each player goes back to the lobby by hand ([ClientMessage.BackToLobby]). */
    @Serializable
    @SerialName("over")
    public data class GameOver(
        override val v: Long,
        public val results: ResultsView,
    ) : StateChange

    /** The game could not start, and the lobby waits again. */
    @Serializable
    @SerialName("aborted")
    public data class GameAborted(
        override val v: Long,
        public val reason: AbortReason = AbortReason.UNKNOWN,
    ) : StateChange

    /** A member's quick reaction. Not a state change: reactions are never replayed. */
    @Serializable
    @SerialName("reaction")
    public data class Reacted(
        public val player: String,
        public val reaction: String,
    ) : ServerMessage

    /** How many players are online, and how many wait in public lobbies. */
    @Serializable
    @SerialName("presence")
    public data class Presence(
        public val online: Int,
        public val searching: Int,
    ) : ServerMessage

    @Serializable
    @SerialName("notice")
    public data class Notice(
        public val kind: NoticeKind = NoticeKind.UNKNOWN,
        public val remainingMs: Long? = null,
    ) : ServerMessage

    /** Why the server is about to close this socket. */
    @Serializable
    @SerialName("closing")
    public data class Closing(
        public val reason: CloseReason = CloseReason.UNKNOWN,
    ) : ServerMessage

    /** A message type this build does not know: ignored. Never sent. */
    @Serializable
    @SerialName("unknown")
    public data object Unknown : ServerMessage
}
