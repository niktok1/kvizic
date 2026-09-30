package io.ntole.kvizic.server.lobby

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * The socket's one-time tickets. REST issues one for a seat it has held, bound to the player, their
 * session and the lobby; the socket's first frame redeems it. Kept in memory by the SHA-256 of their
 * value, so a heap dump holds no working ticket; redeeming removes it, so it works once.
 */
class Tickets(
    private val timeSource: TimeSource.WithComparableMarks,
    private val ttl: Duration,
) {
    data class Ticket(
        val playerId: String,
        val sessionId: String,
        val lobbyId: String,
        val expiresAt: ComparableTimeMark,
    )

    private val byHash = ConcurrentHashMap<String, Ticket>()
    private val random = SecureRandom()

    /** A new ticket's value, for the player to present once within [ttl]. */
    fun issue(
        playerId: String,
        sessionId: String,
        lobbyId: String,
    ): String {
        val bytes = ByteArray(TICKET_BYTES).also(random::nextBytes)
        val value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        byHash[hash(value)] = Ticket(playerId, sessionId, lobbyId, timeSource.markNow() + ttl)
        return value
    }

    /** The ticket [value] names, spent now, or null for one unknown, spent already or expired. */
    fun redeem(value: String): Ticket? {
        if (value.isEmpty() || value.length > MAX_VALUE_LENGTH) return null
        val ticket = byHash.remove(hash(value)) ?: return null
        return ticket.takeIf { timeSource.markNow() < it.expiresAt }
    }

    /** Forgets every expired ticket. */
    fun sweep() {
        val now = timeSource.markNow()
        byHash.values.removeIf { it.expiresAt <= now }
    }

    val size: Int get() = byHash.size

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TICKET_BYTES = 32
        const val MAX_VALUE_LENGTH = 128
    }
}
