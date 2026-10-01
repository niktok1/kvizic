package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.protocol.ServerMessage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * A socket's coming and going reach a lobby whose inbox a flood has filled. Its loop runs only when the
 * test lets it, and each delivery starts at once, so it meets the inbox full, as a loaded server's is while
 * its loop falls behind.
 */
class LobbyInboxTest {
    /** Dropped, the end left its member connected for good, their grace never begun. */
    @Test
    fun `a socket's end reaches a lobby whose inbox is full`() =
        runTest {
            val lobby = LobbyScenario(this)
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            val socket = boris.connection ?: error("boris has no socket")
            lobby.fill()
            assertFalse(lobby.lobby.send(LobbyCommand.Detach(socket)), "sent, it would be dropped")

            val told = async(start = CoroutineStart.UNDISPATCHED) { lobby.lobby.deliver(LobbyCommand.Detach(socket)) }
            lobby.settle()

            assertEquals(Delivery.QUEUED, told.await())
            val gone = ana.all<ServerMessage.MemberUpdated>().last { it.member.player == "boris" }
            assertFalse(gone.member.connected, "the others see him gone")
        }

    @Test
    fun `a socket's coming waits for room in a full inbox`() =
        runTest {
            val lobby = LobbyScenario(this)
            lobby.player("ana").join()
            val boris = lobby.player("boris")
            boris.reserve()
            lobby.fill()

            val socket = TestConnection(lobby.nextConnectionId(), boris.id, boris.sessionId)
            val attached =
                async(start = CoroutineStart.UNDISPATCHED) {
                    lobby.lobby.deliver(LobbyCommand.Attach(socket), within = ATTACH_WAIT)
                }
            lobby.settle()

            assertEquals(Delivery.QUEUED, attached.await())
            assertTrue(socket.messages.any { it is ServerMessage.Snapshot }, "he is let in")
        }

    @Test
    fun `a closed lobby says so to what is delivered`() =
        runTest {
            val lobby = LobbyScenario(this)
            lobby.player("ana").join().leave()
            assertTrue(lobby.closed)

            val socket = TestConnection(lobby.nextConnectionId(), "boris", "session-boris")
            assertEquals(Delivery.CLOSED, lobby.lobby.deliver(LobbyCommand.Attach(socket), within = ATTACH_WAIT))
        }

    /** Fills the lobby's inbox with ticks, which its loop has not been let run to take. */
    private fun LobbyScenario.fill() {
        while (lobby.send(LobbyCommand.Tick)) Unit
    }

    private companion object {
        val ATTACH_WAIT = 2.seconds
    }
}
