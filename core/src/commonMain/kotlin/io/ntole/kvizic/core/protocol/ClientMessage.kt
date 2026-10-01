package io.ntole.kvizic.core.protocol

import io.ntole.kvizic.core.lobby.LobbySettingsDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a client sends on the socket. A command with an [id] is answered with [ServerMessage.Ack] or
 * [ServerMessage.Rejected] naming it; the client picks the ids, one per command.
 */
@Serializable
public sealed interface ClientMessage {
    /** The first frame, and only the first: the ticket REST issued, and which build is talking. */
    @Serializable
    @SerialName("hello")
    public data class Hello(
        public val ticket: String,
        public val protocol: Int,
        public val platform: String = "",
        public val build: Int = 0,
    ) : ClientMessage {
        public override fun toString(): String =
            "Hello(ticket=***, protocol=$protocol, platform=$platform, build=$build)"
    }

    /** Answers the server's [ServerMessage.Ping] with its [seq], which is how the server measures the trip. */
    @Serializable
    @SerialName("pong")
    public data class Pong(
        public val seq: Long,
    ) : ClientMessage

    /** Asks for a fresh [ServerMessage.Snapshot], after a gap in the versions. */
    @Serializable
    @SerialName("resync")
    public data object Resync : ClientMessage

    /** Locks in [option] (its position in the order the answers were sent) for question [question]. */
    @Serializable
    @SerialName("answer")
    public data class Answer(
        public val id: Int,
        public val question: Int,
        public val option: Int,
    ) : ClientMessage

    /** One of [Reactions.ALL]. */
    @Serializable
    @SerialName("react")
    public data class React(
        public val reaction: String,
    ) : ClientMessage

    /** The host changing the lobby's settings, while it waits. */
    @Serializable
    @SerialName("settings")
    public data class UpdateSettings(
        public val id: Int,
        public val settings: LobbySettingsDto,
    ) : ClientMessage

    /** The host starting a game from the lobby. */
    @Serializable
    @SerialName("start")
    public data class Start(
        public val id: Int,
    ) : ClientMessage

    /** The host removing [player] from the lobby for as long as it lasts. */
    @Serializable
    @SerialName("kick")
    public data class Kick(
        public val id: Int,
        public val player: String,
    ) : ClientMessage

    /** The host handing hosting to [player]. */
    @Serializable
    @SerialName("host")
    public data class TransferHost(
        public val id: Int,
        public val player: String,
    ) : ClientMessage

    /**
     * A member's vote to put [player] out of the lobby while it waits, or, with none, their vote taken
     * back. One vote at a time: a vote for another member moves it.
     */
    @Serializable
    @SerialName("vote_kick")
    public data class VoteKick(
        public val id: Int,
        public val player: String? = null,
    ) : ClientMessage

    /** Leaves the results screen for the lobby, by hand: nothing moves a player back by itself. */
    @Serializable
    @SerialName("back")
    public data class BackToLobby(
        public val id: Int,
    ) : ClientMessage

    /** Leaves the lobby for good. The server closes the socket after it. */
    @Serializable
    @SerialName("leave")
    public data object Leave : ClientMessage

    /** A message type this build does not know. Never sent. */
    @Serializable
    @SerialName("unknown")
    public data object Unknown : ClientMessage
}
