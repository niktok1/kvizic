package io.ntole.kvizic.core.network.realtime

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.network.ClientBuild
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
 * The realtime socket over Ktor's WebSockets, `wss://…/v1/play` beside the API. A client of its own,
 * with no bearer and no timeouts: a socket outlives every request bound, and its ticket, sent in the
 * first frame, is all that lets it in. Nothing secret goes in the URL. The build is named in the hello,
 * since a browser cannot set a socket's headers.
 */
public class KtorPlayTransport(
    apiBaseUrl: String,
    private val build: ClientBuild?,
    private val client: HttpClient = realtimeClient(),
) : PlayTransport {
    private val url = socketUrlOf(apiBaseUrl)

    override suspend fun open(ticket: String): PlayConnection {
        val session = client.webSocketSession(url)
        val connection = KtorPlayConnection(session)
        val hello =
            ClientMessage.Hello(
                ticket = ticket,
                protocol = Protocol.VERSION,
                platform = build?.platform.orEmpty(),
                build = build?.number ?: 0,
            )
        if (!connection.send(hello)) {
            connection.close()
            throw IllegalStateException("the socket closed before its hello")
        }
        return connection
    }

    public companion object {
        /** The socket's URL for the API at [apiBaseUrl]: the same host, `ws` for `http`, `wss` for `https`. */
        public fun socketUrlOf(apiBaseUrl: String): String {
            val base = apiBaseUrl.trimEnd('/')
            val socket =
                when {
                    base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
                    base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
                    else -> throw IllegalArgumentException("no http(s) API: $apiBaseUrl")
                }
            return socket + KvizicApi.Paths.PLAY
        }

        /**
         * A client for sockets alone. Ktor's pings are off, since the server pings in the protocol and the
         * client answers there. No frame size is set: the browser's and OkHttp's engines refuse to open a
         * socket that sets one, and the server, the one that sends, holds its own frames small.
         */
        public fun realtimeClient(engine: HttpClientEngine? = null): HttpClient {
            val config: io.ktor.client.HttpClientConfig<*>.() -> Unit = { install(WebSockets) }
            return if (engine == null) HttpClient(config) else HttpClient(engine, config)
        }
    }
}

/** One socket over Ktor: frames decoded into [incoming] by a reader of its own. */
internal class KtorPlayConnection(
    private val session: DefaultClientWebSocketSession,
) : PlayConnection {
    private val decoded = Channel<ServerMessage>(Channel.UNLIMITED)
    override val incoming: ReceiveChannel<ServerMessage> = decoded

    private val reader =
        session.launch {
            try {
                for (frame in session.incoming) {
                    val text = (frame as? Frame.Text)?.readText() ?: continue
                    // A frame this build cannot read is skipped: an unknown message decodes to Unknown
                    // anyway, so only a broken one lands here, and the versions show what it cost.
                    val message = decodeOrNull(text) ?: continue
                    decoded.send(message)
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
            // A socket cancelled under the call fails it as a cancellation too: only the caller's own
            // is one to pass on.
            if (currentCoroutineContext().isActive) false else throw cancelled
        } catch (closed: Exception) {
            false
        }

    override suspend fun closed(): Short? {
        reader.join()
        return withTimeoutOrNull(CLOSE_REASON_WAIT) { session.closeReason.await() }?.code
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

    private companion object {
        val CLOSE_REASON_WAIT = 2.seconds
    }
}
