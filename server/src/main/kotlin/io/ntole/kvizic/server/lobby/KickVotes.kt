package io.ntole.kvizic.server.lobby

/**
 * The votes to put one member out of a lobby, as the room is told them: how many count now, and how many
 * it takes. [NONE] while nobody votes them out.
 */
internal data class KickTally(
    val votes: Int,
    val needed: Int,
) {
    /** Whether the room has voted them out. */
    val reached: Boolean get() = votes > 0 && votes >= needed

    companion object {
        val NONE: KickTally = KickTally(0, 0)
    }
}

/**
 * How many of [voters], the members who may vote someone out, it takes: more than half, and never fewer
 * than [MIN_KICK_VOTES], so of two players neither can put the other out.
 */
internal fun kickVotesNeeded(voters: Int): Int = maxOf(MIN_KICK_VOTES, voters / 2 + 1)

/** The fewest votes that put a member out. */
internal const val MIN_KICK_VOTES: Int = 2
