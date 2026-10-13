package io.ntole.kvizic.e2e

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ntole.kvizic.core.network.ClientBuild
import io.ntole.kvizic.core.network.realtime.KtorPlayTransport
import io.ntole.kvizic.core.network.realtime.PlayConnection
import io.ntole.kvizic.core.network.realtime.PlayTransport
import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.Protocol
import io.ntole.kvizic.core.protocol.ProtocolJson
import io.ntole.kvizic.core.protocol.ServerMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlin.time.Duration.Companion.seconds

/**
 * The realtime socket as the app's [KtorPlayTransport] opens it, its hello naming the UI [language] a player
 * reads in: the app's own transport names none yet. Frames go and come as there, over [client].
 */
internal class LanguageTransport(
    apiBaseUrl: String,
    private val build: ClientBuild,
    private val language: String,
    private val client: HttpClient,
) : PlayTransport {
    private val url = KtorPlayTransport.socketUrlOf(apiBaseUrl)

    override suspend fun open(ticket: String): PlayConnection {
        val connection = SessionConnection(client.webSocketSession(url))
        val hello = ClientMessage.Hello(ticket, Protocol.VERSION, build.platform, build.number, language)
        if (!connection.send(hello)) {
            connection.close()
            throw IllegalStateException("the socket closed before its hello")
        }
        return connection
    }
}

/** One socket, its frames decoded into [incoming] by a reader of its own, as the app's connection does. */
private class SessionConnection(
    private val session: DefaultClientWebSocketSession,
) : PlayConnection {
    private val decoded = Channel<ServerMessage>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<ServerMessage> = decoded

    private val reader =
        session.launch {
            try {
                for (frame in session.incoming) {
                    val text = (frame as? Frame.Text)?.readText() ?: continue
                    decodeOrNull(text)?.let { decoded.send(it) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (ended: Exception) {
                // The connection went: closed() says how.
            } finally {
                decoded.close()
            }
        }

    override suspend fun send(message: ClientMessage): Boolean =
        try {
            session.send(Frame.Text(ProtocolJson.encodeToString(ClientMessage.serializer(), message)))
            true
        } catch (cancelled: CancellationException) {
            if (currentCoroutineContext().isActive) false else throw cancelled
        } catch (closed: Exception) {
            false
        }

    override suspend fun closed(): Short? {
        reader.join()
        return try {
            withTimeoutOrNull(2.seconds) { session.closeReason.await() }?.code
        } catch (cancelled: CancellationException) {
            if (currentCoroutineContext().isActive) null else throw cancelled
        } catch (broken: Exception) {
            null
        }
    }

    override fun close() {
        session.launch {
            withContext(NonCancellable) {
                runCatching { session.close(CloseReason(CloseReason.Codes.NORMAL, "")) }
            }
        }
    }

    private fun decodeOrNull(text: String): ServerMessage? =
        try {
            ProtocolJson.decodeFromString(ServerMessage.serializer(), text)
        } catch (malformed: SerializationException) {
            null
        } catch (malformed: IllegalArgumentException) {
            null
        }
}
