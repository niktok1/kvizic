package io.ntole.kvizic.core.domain.error

/**
 * Why something failed, as the domain names it: the UI and the use cases branch on this and never on a
 * wire type. `:core:data` translates the server's error codes into it.
 *
 * Split in two so the platform's failures, which every app of the developer's shares, stay apart from
 * the game's own: a screen that handles only [CoreError] still compiles when [GameError] grows.
 */
public sealed interface DomainError {
    /** The failure's stable name, as analytics report it: never shown to a player. */
    public val name: String
}

/** Failures of the platform: the network, the server, the session, the build and the sign-in. */
public enum class CoreError : DomainError {
    /** The request never reached the server, or its answer did not arrive whole. */
    NETWORK,

    /**
     * The server was reached and failed, or answered in a shape this build cannot decode, or refused a
     * request no correct client sends: a bug on one side, so "check your connection" would mislead.
     */
    SERVER,

    /** The session is dead: the data layer replaces it with a fresh guest and tries once more. */
    UNAUTHORIZED,

    /** Too many requests; the exception's `retryAfter` says how long to wait, when the server said. */
    RATE_LIMITED,

    /** The server serves this build nothing more: only an update puts it right. */
    UPGRADE_REQUIRED,

    /** An admin route without the right admin token. Nothing to do with the player's session. */
    FORBIDDEN,

    /**
     * Google refused the Play Games server auth code: spent, expired or another app's. A new code may
     * work. Never [UNAUTHORIZED]: the session is fine.
     */
    PLAY_GAMES_CODE_REFUSED,

    /** A Play Games sign-in could not be made: the server could not ask Google, or Play Games gave no code. */
    PLAY_GAMES_UNAVAILABLE,

    /** A moderator named an account no player has. */
    PLAYER_NOT_FOUND,

    UNKNOWN,
}

/** Failures of the game itself: lobbies, the player's profile, and the moderator's question bank. */
public enum class GameError : DomainError {
    /** No open lobby has that code, or the lobby is gone. */
    LOBBY_NOT_FOUND,

    /** The lobby has no free seat. */
    LOBBY_FULL,

    /** The lobby's host kicked this player, who may not come back while it lasts. */
    LOBBY_BANNED,

    /** The server holds as many lobbies as it takes: try again shortly. */
    TOO_MANY_LOBBIES,

    /** The server is restarting and opens no new lobby or seat: try again in a moment. */
    SERVER_DRAINING,

    /** Lobby settings no host could pick. */
    INVALID_SETTINGS,

    /** An avatar the game does not have. */
    INVALID_AVATAR,

    /** No question has that id. */
    QUESTION_NOT_FOUND,

    /** A draft or an edit the question rules refuse: the moderator's to put right. */
    INVALID_QUESTION,

    /** An edit made from a revision another edit replaced first: reload and edit again. */
    STALE_REVISION,

    /** A question moved from a status it does not stand at. */
    WRONG_STATUS,

    /** A topic added under an id a topic has already. */
    TOPIC_EXISTS,

    /** A topic no topic has the id of. */
    TOPIC_NOT_FOUND,
}
