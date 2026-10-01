package io.ntole.kvizic.core.protocol

/**
 * The realtime protocol's version. Additive changes (a field with a default, a message type, an enum
 * value) never bump it: an older client ignores what it does not know. A breaking change bumps
 * [VERSION], and the server accepts [MIN_SUPPORTED] to [VERSION]; an older hello is closed with
 * [CloseCodes.UPGRADE_REQUIRED].
 */
public object Protocol {
    public const val VERSION: Int = 1
    public const val MIN_SUPPORTED: Int = 1
}

/**
 * Why the server closed a socket, a fixed contract. A code a client does not know means: reconnect
 * with backoff. [Closing] names the reason in words a client can show just before the close.
 */
public object CloseCodes {
    public const val NORMAL: Short = 1000
    public const val INTERNAL: Short = 1011

    /** A frame that is not the protocol: malformed, oversize, or a second hello. */
    public const val PROTOCOL_ERROR: Short = 4400

    /** The hello's ticket is unknown, expired or used already. Ask REST for a new one. */
    public const val TICKET_INVALID: Short = 4401

    /** The host kicked this player; they may not come back while the lobby lasts. */
    public const val KICKED: Short = 4403

    /** The lobby is gone. */
    public const val LOBBY_GONE: Short = 4404

    /** No hello in time, or the connection went silent past its heartbeat. */
    public const val TIMEOUT: Short = 4408

    /** The same player opened another socket; this one is replaced. */
    public const val REPLACED: Short = 4409

    /** The player's session ended: logged out, or the account was deleted. */
    public const val SESSION_ENDED: Short = 4410

    /** The client's protocol is older than the server serves: show the update screen. */
    public const val UPGRADE_REQUIRED: Short = 4426

    /** Too many frames, or too slow to read what the server sends. Reconnect and resync. */
    public const val TOO_MUCH: Short = 4429

    /** The server is restarting. The lobby will not survive it. */
    public const val SERVER_RESTARTING: Short = 4503
}

/**
 * The fixed quick reactions, by id. The client draws each and names it in its own words. [NUDGE] is a
 * member's word to the host that the room is ready: the client offers it to everyone but the host, while
 * the room waits.
 */
public object Reactions {
    public const val NUDGE: String = "nudge"

    public val ALL: List<String> = listOf("bravo", "clap", "fire", "wow", "laugh", "oops", NUDGE)
}
