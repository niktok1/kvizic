package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.protocol.ServerMessage
import io.ntole.kvizic.core.region.ContentLanguages

/**
 * One socket of a member, as a lobby sees it: a socket in production, a recording fake in tests. Bound
 * to its player and session for its whole life.
 */
interface LobbyConnection {
    /** Unique among every connection this process opened. */
    val id: Long
    val playerId: String
    val sessionId: String

    /**
     * The content language its reader reads, one of `ContentLanguages.ALL`, as `ContentLanguages.of` maps the
     * UI tag a socket's hello said: the lobby words what it sends a member in the language of their latest
     * connection. Serbian Cyrillic for one that says none.
     */
    val language: String get() = ContentLanguages.SERBIAN

    /**
     * Queues [message] and returns at once, never waiting on the client. False when the socket cannot
     * keep up, and then it is being closed: the member is treated as gone and resyncs when they reconnect.
     */
    fun send(message: ServerMessage): Boolean

    /** Closes the socket with [code], one of `CloseCodes`, after what was queued. */
    fun close(code: Short)
}
