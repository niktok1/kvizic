package io.ntole.kvizic.e2e

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.time.Duration

/**
 * A TCP proxy in front of the server's port, for one player's connections, that the test can make slow
 * or cut: [latency] is added to every chunk each way, and [cutAll] drops every connection at once, as a
 * phone losing its network does. While [refusing], new connections are shut at once.
 */
internal class FaultProxy(
    private val targetPort: Int,
) : AutoCloseable {
    private val listener = ServerSocket(0, BACKLOG, InetAddress.getLoopbackAddress())
    private val open = CopyOnWriteArrayList<Socket>()

    val port: Int get() = listener.localPort
    val baseUrl: String get() = "http://127.0.0.1:$port"

    @Volatile
    var latency: Duration = Duration.ZERO

    @Volatile
    var refusing: Boolean = false

    private val acceptor =
        thread(isDaemon = true, name = "fault-proxy-$port") {
            while (!listener.isClosed) {
                val client =
                    try {
                        listener.accept()
                    } catch (closed: IOException) {
                        break
                    }
                if (refusing) {
                    client.close()
                    continue
                }
                val upstream = Socket(InetAddress.getLoopbackAddress(), targetPort)
                client.tcpNoDelay = true
                upstream.tcpNoDelay = true
                open += client
                open += upstream
                pump(client, upstream)
                pump(upstream, client)
            }
        }

    private fun pump(
        from: Socket,
        to: Socket,
    ) = thread(isDaemon = true) {
        val buffer = ByteArray(CHUNK)
        try {
            val input = from.getInputStream()
            val output = to.getOutputStream()
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                val delay = latency
                if (delay.isPositive()) Thread.sleep(delay.inWholeMilliseconds)
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (lost: IOException) {
            // Cut, or closed on the other side.
        } finally {
            runCatching { to.close() }
            runCatching { from.close() }
            open.remove(from)
            open.remove(to)
        }
    }

    /** Drops every connection through the proxy now. */
    fun cutAll() {
        open.forEach { socket -> runCatching { socket.close() } }
        open.clear()
    }

    override fun close() {
        listener.close()
        cutAll()
        acceptor.join(JOIN_MS)
    }

    private companion object {
        const val BACKLOG = 50
        const val CHUNK = 16_384
        const val JOIN_MS = 2_000L
    }
}
