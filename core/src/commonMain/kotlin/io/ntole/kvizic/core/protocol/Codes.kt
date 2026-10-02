package io.ntole.kvizic.core.protocol

import kotlinx.serialization.Serializable

/** Why a command was refused. */
@Serializable
public enum class RejectCode {
    /** Only the host may do that. */
    NOT_HOST,

    /** Not while the lobby is where it is: settings during a game, a start during one. */
    WRONG_PHASE,

    /** Locked in already, with another answer. */
    ALREADY_ANSWERED,

    /** An answer to a question that is not the one asked. */
    WRONG_QUESTION,

    /** An answer before the answers were shown. */
    TOO_EARLY,

    /** An answer after time ran out. */
    TOO_LATE,

    /** An answer that names no answer the question has. */
    INVALID_OPTION,

    /** Settings no host could pick. */
    INVALID_SETTINGS,

    /** A kick or a handover naming no member. */
    NO_SUCH_PLAYER,

    /** Too many frames: slow down. */
    RATE_LIMITED,

    /** An answer from a member who watches this game rather than playing it. */
    NOT_PLAYING,

    /** A start while the server is restarting. */
    DRAINING,

    /** A vote to put a member out, again before the voter's wait between two votes is over. */
    TOO_SOON,

    UNKNOWN,
}

@Serializable
public enum class LeaveReason {
    LEFT,
    KICKED,

    /** Gone too long without reconnecting. */
    TIMED_OUT,

    /** Joined another lobby. */
    JOINED_ANOTHER,

    /** Logged out, or deleted their account. */
    SESSION_ENDED,

    /** Most of the others in the room voted them out. */
    VOTED_OUT,

    /** Still on the last game's results when the next one started: they never came back to the room. */
    NOT_BACK,
    UNKNOWN,
}

@Serializable
public enum class AbortReason {
    /** The bank had no question for the settings. */
    NO_QUESTIONS,

    /** Nobody was left in the lobby to play when the countdown ended. */
    NO_PLAYERS,

    /** The server is restarting. */
    SERVER_RESTARTING,
    UNKNOWN,
}

@Serializable
public enum class NoticeKind {
    /** The server restarts in `remainingMs`; this lobby will not survive it. */
    SERVER_RESTARTING,

    /** Too few questions in the topics picked, so some come from other topics. */
    TOPICS_TOPPED_UP,

    /** Too few questions for the game's length, so it is shorter. */
    GAME_SHORTENED,

    /**
     * To a public lobby's host alone, who has done nothing for a while with others waiting: hosting passes to
     * another in `remainingMs`, unless they send [ClientMessage.Stay].
     */
    HOST_IDLE,

    /**
     * To everyone in a waiting lobby nobody has done anything in for long: it closes in `remainingMs`, unless
     * a member sends [ClientMessage.Stay].
     */
    ROOM_IDLE,
    UNKNOWN,
}

@Serializable
public enum class CloseReason {
    LEFT,
    KICKED,
    LOBBY_CLOSED,
    IDLE,
    REPLACED,
    SESSION_ENDED,
    SERVER_RESTARTING,
    VOTED_OUT,
    NOT_BACK,
    UNKNOWN,
}
