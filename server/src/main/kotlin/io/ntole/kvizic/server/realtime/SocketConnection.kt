package io.ntole.kvizic.server.realtime

import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ntole.kvizic.core.protocol.ProtocolJson
import io.ntole.kvizic.core.protocol.ServerMessage
import io.ntole.kvizic.server.lobby.LobbyConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/**
 * A member's socket, as its lobby sees it. [send] only ever offers a frame to the session's bounded
 * outgoing channel and returns: a socket too slow to take it is failed, and the lobby drops the member,
 * who resyncs on coming back. The heartbeat's pongs measure the round trip ([rtt]), which is taken off
 * the member's answer times, up to a cap.
 */
internal class SocketConnection(
    override val id: Long,
    override val playerId: String,
    override val sessionId: String,
    private val session: DefaultWebSocketSession,
    /** Where a closed socket is cut, should its peer never answer the close frame. */
    private val watchdog: CoroutineScope,
    private val closeWait: Duration,
    openedAt: ComparableTimeMark,
) : LobbyConnection {
    private val closedWith = AtomicReference<Short?>(null)
    private val pings = AtomicLong()
    private val pingsSent = ConcurrentHashMap<Long, ComparableTimeMark>()
    private val samples = ArrayDeque<Duration>()

    /** The median of the last few round trips, or zero until a pong has come. */
    @Volatile
    var rtt: Duration = ZERO
        private set

    val closeCode: Short? get() = closedWith.get()

    /** When the client last sent anything at all. */
    @Volatile
    var lastHeard: ComparableTimeMark = openedAt

    override fun send(message: ServerMessage): Boolean {
        if (closedWith.get() != null) return true
        val text = ProtocolJson.encodeToString(ServerMessage.serializer(), message)
        return session.outgoing.trySend(Frame.Text(text)).isSuccess
    }

    override fun close(code: Short) {
        if (!closedWith.compareAndSet(null, code)) return
        // A socket so full it cannot take the close frame either is simply cut: its client sees the
        // connection lost, and reconnects as it would for 4429.
        val sent = session.outgoing.trySend(Frame.Close(CloseReason(code, ""))).isSuccess
        if (!sent) {
            session.cancel()
            return
        }
        watchdog.launch {
            delay(closeWait)
            session.cancel()
        }
    }

    /** The next heartbeat ping, stamped [at] to be matched with its pong. */
    fun ping(at: ComparableTimeMark): ServerMessage.Ping {
        val seq = pings.incrementAndGet()
        pingsSent[seq] = at
        pingsSent.keys.removeIf { it <= seq - KEPT_PINGS }
        return ServerMessage.Ping(seq, rtt.inWholeMilliseconds)
    }

    /** The pong to ping [seq], come [at]: one more round trip measured. */
    fun pong(
        seq: Long,
        at: ComparableTimeMark,
    ) {
        val sent = pingsSent.remove(seq) ?: return
        synchronized(samples) {
            samples.addLast(at - sent)
            while (samples.size > SAMPLES) samples.removeFirst()
            rtt = samples.sorted()[samples.size / 2]
        }
    }

    private companion object {
        const val SAMPLES = 5
        const val KEPT_PINGS = 4
    }
}
