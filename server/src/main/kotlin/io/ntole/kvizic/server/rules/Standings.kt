package io.ntole.kvizic.server.rules

/** One player's running totals in a game. */
data class Tally(
    val playerId: String,
    val score: Int = 0,
    val correct: Int = 0,
    val answered: Int = 0,
    /** The time their right answers took, together: less wins a tie. */
    val correctTimeMs: Long = 0,
)

/** A player's place: shared by exact ties, so 1, 2, 2, 4. */
data class Ranked(
    val tally: Tally,
    val rank: Int,
)

/**
 * The standings: by score, then by right answers, then by the time the right answers took, less first.
 * Players the same on all three share a rank; the next rank skips as many as shared it. Otherwise in the
 * order given, which keeps the order stable for players who tie.
 */
fun rank(tallies: List<Tally>): List<Ranked> {
    val order = compareByDescending<Tally> { it.score }.thenByDescending { it.correct }.thenBy { it.correctTimeMs }
    val sorted = tallies.sortedWith(order)
    return sorted.mapIndexed { index, tally ->
        val ahead = sorted.subList(0, index).count { order.compare(it, tally) < 0 }
        Ranked(tally, rank = ahead + 1)
    }
}
