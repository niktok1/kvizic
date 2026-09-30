package io.ntole.kvizic.core.error

import kotlinx.serialization.Serializable

/**
 * The machine-readable cause of a failed request; a client branches on this, never on the message.
 * [UNKNOWN] is the default, so a code a newer server adds decodes on an older client instead of
 * failing the whole payload.
 */
@Serializable
public enum class ErrorCode {
    // --- Platform ---

    /** The request is malformed or holds what no correct client sends: a bug on one side. 400. */
    VALIDATION_FAILED,

    /** No valid access token, or the player it names is gone. 401: a client refreshes, then mints a guest. */
    UNAUTHORIZED,

    /** An admin route without the right admin token. 403, never 401. */
    FORBIDDEN,

    /** The refresh token is unknown, spent or expired. 401. */
    INVALID_REFRESH_TOKEN,

    /** Too many requests; `Retry-After` names the wait in whole seconds. 429. */
    RATE_LIMITED,

    /** The client's build is older than the oldest this server serves. 426. */
    UPGRADE_REQUIRED,

    /** Google refused the Play Games code: spent, expired or another app's. 422: ask Play Games again. */
    PLAY_GAMES_CODE_REFUSED,

    /** Google could not be asked about the Play Games code. 502. */
    PLAY_GAMES_UNAVAILABLE,

    /** A moderator named an account no player has. 404. */
    PLAYER_NOT_FOUND,

    /** Unexpected server-side failure. 500. */
    INTERNAL,

    // --- The game ---

    /** No open lobby has that code, or the lobby is gone. 404. */
    LOBBY_NOT_FOUND,

    /** The lobby has no free seat. 409. */
    LOBBY_FULL,

    /** The lobby's host kicked this player; they may not come back while it lasts. 403. */
    LOBBY_BANNED,

    /** The server holds as many lobbies as it takes; try again shortly. 503. */
    TOO_MANY_LOBBIES,

    /** The server is restarting and opens no new lobby or seat; try again in a moment. 503. */
    SERVER_DRAINING,

    /** Lobby settings no host could pick: a count, time or size off the lists, or a topic no topic has. 400. */
    INVALID_SETTINGS,

    /** An avatar id the game does not have. 400. */
    INVALID_AVATAR,

    /** No question has that id. 404. */
    QUESTION_NOT_FOUND,

    /** A draft or an edit the rules refuse: the moderator's to put right. 422. */
    INVALID_QUESTION,

    /** An edit made from a revision another edit replaced first. 409: reload and edit again. */
    STALE_REVISION,

    /** A question moved from a status it does not stand at. 409. */
    WRONG_STATUS,

    /** A topic added under an id a topic has already. 409. */
    TOPIC_EXISTS,

    /** A topic no topic has the id of. 404. */
    TOPIC_NOT_FOUND,

    UNKNOWN,
}
