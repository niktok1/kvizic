package io.ntole.kvizic.core.network.realtime

import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.ServerMessage
import kotlinx.coroutines.channels.ReceiveChannel

/** One open realtime socket, to the lobby its ticket named. */
public interface PlayConnection {
    /** Every message the server sends, in order; closed once the socket is. */
    public val incoming: ReceiveChannel<ServerMessage>

    /** Sends [message]; false once the socket is closing and nothing more goes out. */
    public suspend fun send(message: ClientMessage): Boolean

    /**
     * Waits for the socket to be gone, and says how the server closed it: its close code, or null for a
     * connection lost with none.
     */
    public suspend fun closed(): Short?

    /** Closes the socket from this side. */
    public fun close()
}

/** Opens realtime sockets: the network's in the app, a fake's in tests. */
public fun interface PlayTransport {
    /** Opens a socket and says hello with [ticket]. Throws when none could be opened. */
    public suspend fun open(ticket: String): PlayConnection
}
