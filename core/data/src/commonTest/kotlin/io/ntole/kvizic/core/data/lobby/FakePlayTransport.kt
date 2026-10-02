package io.ntole.kvizic.core.data.lobby

import io.ntole.kvizic.core.network.realtime.PlayConnection
import io.ntole.kvizic.core.network.realtime.PlayTransport
import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.CloseCodes
import io.ntole.kvizic.core.protocol.CloseReason
import io.ntole.kvizic.core.protocol.ProtocolJson
import io.ntole.kvizic.core.protocol.ServerMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

/** Sockets a test plays the server on: it opens each [FakeConnection] a ticket asks for, or refuses. */
internal class FakePlayTransport : PlayTransport {
    val opened = mutableListOf<FakeConnection>()

    /** Set, every socket fails to open, as with no network. */
    var refuse = false

    override suspend fun open(ticket: String): PlayConnection {
        if (refuse) throw IllegalStateException("no network")
        return FakeConnection(ticket).also { opened += it }
    }

    /** The socket opened last. */
    fun last(): FakeConnection = opened.last()
}

/**
 * One socket, with the test as the server: what it [push]es arrives in order, round-tripped through the
 * protocol's JSON as it would travel, and every message the client sent is in [sent].
 */
internal class FakeConnection(
    val ticket: String,
) : PlayConnection {
    private val inbox = Channel<ServerMessage>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<ServerMessage> = inbox
    private val closedWith = CompletableDeferred<Short?>()
    val sent = mutableListOf<ClientMessage>()
    var closedByClient = false
        private set

    val isClosed: Boolean get() = closedWith.isCompleted

    override suspend fun send(message: ClientMessage): Boolean {
        if (closedWith.isCompleted) return false
        val wire = ProtocolJson.encodeToString(ClientMessage.serializer(), message)
        sent += ProtocolJson.decodeFromString(ClientMessage.serializer(), wire)
        return true
    }

    override suspend fun closed(): Short? = closedWith.await()

    override fun close() {
        if (closedWith.isCompleted) return
        closedByClient = true
        finish(CloseCodes.NORMAL)
    }

    fun push(vararg messages: ServerMessage) {
        messages.forEach { message ->
            val wire = ProtocolJson.encodeToString(ServerMessage.serializer(), message)
            inbox.trySend(ProtocolJson.decodeFromString(ServerMessage.serializer(), wire))
        }
    }

    /** The server closes the socket with [code], having said [reason] first when there is one. */
    fun closeWith(
        code: Short?,
        reason: CloseReason? = null,
    ) {
        reason?.let { push(ServerMessage.Closing(it)) }
        finish(code)
    }

    /** The connection is lost, with no close frame. */
    fun drop() = finish(null)

    /** The connection breaks with [cause] and no close frame, as OkHttp's does when the network drops. */
    fun breakWith(cause: Throwable) {
        inbox.close()
        closedWith.completeExceptionally(cause)
    }

    inline fun <reified T : ClientMessage> sentOf(): List<T> = sent.filterIsInstance<T>()

    private fun finish(code: Short?) {
        inbox.close()
        closedWith.complete(code)
    }
}
