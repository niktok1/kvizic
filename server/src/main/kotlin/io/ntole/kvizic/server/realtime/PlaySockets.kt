package io.ntole.kvizic.server.realtime

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.CloseCodes
import io.ntole.kvizic.core.protocol.Protocol
import io.ntole.kvizic.core.protocol.ProtocolJson
import io.ntole.kvizic.server.lobby.Delivery
import io.ntole.kvizic.server.lobby.GameTimings
import io.ntole.kvizic.server.lobby.Lobby
import io.ntole.kvizic.server.lobby.LobbyCommand
import io.ntole.kvizic.server.lobby.LobbyLimits
import io.ntole.kvizic.server.lobby.LobbyRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The realtime socket, `/v1/play`. A socket's first frame must be a `hello` within
 * [GameTimings.helloTimeout], naming a protocol this server still speaks, a build its platform still
 * serves, and a one-time ticket REST issued for a seat; then it is its lobby's until either closes it.
 *
 * Every frame a client sends spends from the socket's frame budget; a socket that floods is closed with
 * 4429, one that sends what is no protocol frame with 4400, and one silent past [GameTimings.silentAfter]
 * with 4408, its seat kept through the lobby's grace. The server pings every [GameTimings.pingEvery], so
 * Cloudflare never finds the socket idle, and the pongs measure the round trip. One address holds at
 * most [LobbyLimits.maxSocketsPerAddress] sockets at once.
 */
class PlaySockets(
    private val registry: LobbyRegistry,
    private val timings: GameTimings,
    private val limits: LobbyLimits,
    private val minClientVersions: Map<String, Int>,
    /** Where a closed socket's clean-up runs, after its handler has returned. */
    private val watchdog: CoroutineScope,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    private val ids = AtomicLong()
    private val perAddress = ConcurrentHashMap<String, AtomicInteger>()

    /** How many sockets are open now, their hello pending included. */
    val open: Int get() = perAddress.values.sumOf { it.get() }

    /**
     * Waits until every socket has closed, or [timeout]: once a drain has closed the lobbies, their last
     * frames, a game's results and the close itself, are still on their way to the players, and would be
     * lost with the engine.
     */
    suspend fun awaitClosed(timeout: Duration) {
        withTimeoutOrNull(timeout) {
            while (open > 0) delay(CLOSED_POLL)
        }
    }

    suspend fun serve(
        session: DefaultWebSocketServerSession,
        address: String,
    ) {
        if (!claim(address)) {
            session.close(CloseReason(CloseCodes.TOO_MUCH, "too many sockets"))
            return
        }
        try {
            val connection = handshake(session) ?: return
            val lobby = registry.lobby(connection.second) ?: return session.closeWith(CloseCodes.LOBBY_GONE)
            attachAndRead(session, connection.first, lobby)
        } finally {
            release(address)
            // A peer that never answers the close frame would hold the session open for good.
            watchdog.launch {
                delay(CLOSE_WAIT)
                session.cancel()
            }
        }
    }

    /** The socket's connection and its lobby's id, or null once the socket is closed for its hello. */
    private suspend fun handshake(session: DefaultWebSocketServerSession): Pair<SocketConnection, String>? {
        val first =
            withTimeoutOrNull(timings.helloTimeout) { session.incoming.receiveCatching() }
                ?: return null.also { session.closeWith(CloseCodes.TIMEOUT) }
        val frame = first.getOrNull() ?: return null
        val hello =
            (frame as? Frame.Text)?.let { decode(it.readText()) } as? ClientMessage.Hello
                ?: return null.also { session.closeWith(CloseCodes.PROTOCOL_ERROR) }
        if (hello.protocol < Protocol.MIN_SUPPORTED || isTooOld(hello)) {
            return null.also { session.closeWith(CloseCodes.UPGRADE_REQUIRED) }
        }
        val ticket =
            registry.tickets.redeem(hello.ticket)
                ?: return null.also { session.closeWith(CloseCodes.TICKET_INVALID) }
        val connection =
            SocketConnection(
                id = ids.incrementAndGet(),
                playerId = ticket.playerId,
                sessionId = ticket.sessionId,
                session = session,
                watchdog = watchdog,
                closeWait = CLOSE_WAIT,
                openedAt = timeSource.markNow(),
            )
        return connection to ticket.lobbyId
    }

    private suspend fun attachAndRead(
        session: DefaultWebSocketServerSession,
        connection: SocketConnection,
        lobby: Lobby,
    ) {
        try {
            // A lobby busy with a flood is waited for a while: told it is gone, the player would leave it.
            when (lobby.deliver(LobbyCommand.Attach(connection), within = ATTACH_WAIT)) {
                Delivery.QUEUED -> Unit
                Delivery.NO_ROOM -> return session.closeWith(CloseCodes.TOO_MUCH)
                Delivery.CLOSED -> return session.closeWith(CloseCodes.LOBBY_GONE)
            }
            coroutineScope {
                val heartbeat =
                    launch {
                        while (isActive) {
                            delay(timings.pingEvery)
                            val now = timeSource.markNow()
                            if (now - connection.lastHeard > timings.silentAfter) {
                                connection.close(CloseCodes.TIMEOUT)
                                break
                            }
                            if (!connection.send(connection.ping(now))) connection.close(CloseCodes.TOO_MUCH)
                        }
                    }
                read(session, connection, lobby)
                heartbeat.cancel()
            }
        } finally {
            // Never dropped for a full inbox, and harmless for a socket the lobby never took: the attach
            // above may have been queued even as its wait ran out.
            lobby.deliver(LobbyCommand.Detach(connection))
        }
    }

    private suspend fun read(
        session: DefaultWebSocketServerSession,
        connection: SocketConnection,
        lobby: Lobby,
    ) {
        val budget = FrameBudget(limits.framesPerSecond, limits.frameBurst)
        try {
            for (frame in session.incoming) {
                val now = timeSource.markNow()
                connection.lastHeard = now
                if (connection.closeCode != null) continue
                if (!budget.take(now)) {
                    connection.close(CloseCodes.TOO_MUCH)
                    continue
                }
                val message = (frame as? Frame.Text)?.let { decode(it.readText()) }
                when {
                    message == null || message is ClientMessage.Hello -> {
                        connection.close(CloseCodes.PROTOCOL_ERROR)
                    }

                    message is ClientMessage.Pong -> {
                        connection.pong(message.seq, now)
                    }

                    !lobby.send(LobbyCommand.FromClient(connection, message, now, connection.rtt)) -> {
                        connection.close(CloseCodes.TOO_MUCH)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            // The session ended under the loop: a frame past the size limit (closed 1009 by Ktor), or
            // the connection lost.
            log.debug("socket {} of player {} ended: {}", connection.id, connection.playerId, failed.toString())
        }
    }

    private fun isTooOld(hello: ClientMessage.Hello): Boolean {
        val minimum = minClientVersions[hello.platform.trim().lowercase()] ?: return false
        return hello.build in 1 until minimum
    }

    private fun decode(text: String): ClientMessage? =
        try {
            ProtocolJson.decodeFromString(ClientMessage.serializer(), text)
        } catch (malformed: SerializationException) {
            null
        } catch (malformed: IllegalArgumentException) {
            null
        }

    private fun claim(address: String): Boolean {
        val count = perAddress.computeIfAbsent(address) { AtomicInteger() }
        if (count.incrementAndGet() <= limits.maxSocketsPerAddress) return true
        release(address)
        return false
    }

    private fun release(address: String) {
        perAddress.computeIfPresent(address) { _, count -> count.takeIf { it.decrementAndGet() > 0 } }
    }

    private suspend fun DefaultWebSocketServerSession.closeWith(code: Short) {
        close(CloseReason(code, ""))
    }

    /** A burst of [burst] frames, then [perSecond] a second. */
    private class FrameBudget(
        private val perSecond: Int,
        private val burst: Int,
    ) {
        private var tokens = burst.toDouble()
        private var refilledAt: ComparableTimeMark? = null

        fun take(at: ComparableTimeMark): Boolean {
            val last = refilledAt
            if (last !=
                null
            ) {
                tokens =
                    (tokens + (at - last).inWholeMilliseconds * perSecond / 1_000.0).coerceAtMost(burst.toDouble())
            }
            refilledAt = at
            if (tokens < 1) return false
            tokens -= 1
            return true
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(PlaySockets::class.java)
        val CLOSE_WAIT: Duration = 5.seconds

        /** How long a socket waits for room in its lobby's full inbox before it is told to come again. */
        val ATTACH_WAIT: Duration = 2.seconds
        val CLOSED_POLL: Duration = 50.milliseconds
    }
}
