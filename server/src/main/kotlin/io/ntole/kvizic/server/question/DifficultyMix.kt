package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.question.Difficulty
import kotlin.math.abs
import kotlin.random.Random

/**
 * A game's mix of levels, never a filter: the level its host chose for most of its questions, the
 * levels beside it for the rest, in [SHARES]. Within the questions a game may take, the mix comes second
 * to who has seen what, so a game asks an unseen question off its level before a seen one on it.
 */
class DifficultyMix(
    private val random: Random = Random.Default,
) {
    /**
     * How many of [count] questions each level gets when [chosen] is picked: its [SHARES] of [count], a
     * share that does not come out whole going to the largest remainders, ties at random.
     */
    fun quotas(
        chosen: Difficulty,
        count: Int,
    ): Map<Difficulty, Int> {
        val shares = SHARES.getValue(known(chosen))
        val whole = shares.map { count * it / PERCENT }
        val remainders = shares.map { count * it % PERCENT }
        val bonus =
            LEVELS.indices
                .shuffled(random)
                .sortedByDescending { remainders[it] }
                .take(count - whole.sum())
                .toSet()
        return LEVELS.withIndex().associate { (i, level) -> level to whole[i] + if (i in bonus) 1 else 0 }
    }

    /**
     * [count] questions from [tiers], each tier better than the next (asked in the topics, seen by fewer
     * players): a tier the game has room for is taken whole, and the one that fills the game is picked
     * from by the levels [chosen]'s [quotas] still want. A level that tier is short of takes the nearest
     * level it has; one the tiers above gave too many of leaves the levels farthest from [chosen] fewer.
     */
    fun <T> pick(
        tiers: List<List<T>>,
        count: Int,
        chosen: Difficulty,
        levelOf: (T) -> Difficulty,
    ): List<T> {
        val aim = known(chosen)
        val wanted = quotas(aim, count).toMutableMap()
        val picked = mutableListOf<T>()
        for (tier in tiers) {
            val room = count - picked.size
            if (room == 0) break
            val taken = if (tier.size <= room) tier else mixed(tier, trimmed(wanted, room, aim), aim, levelOf)
            taken.forEach { question ->
                val level = known(levelOf(question))
                wanted[level] = (wanted.getValue(level) - 1).coerceAtLeast(0)
            }
            picked += taken
        }
        return picked
    }

    /** [wanted] cut to [room] in all, from the levels farthest from [aim] first, ties at random. */
    private fun trimmed(
        wanted: Map<Difficulty, Int>,
        room: Int,
        aim: Difficulty,
    ): Map<Difficulty, Int> {
        val left = wanted.toMutableMap()
        while (left.values.sum() > room) {
            val farthest = LEVELS.filter { left.getValue(it) > 0 }.shuffled(random).maxBy { distance(it, aim) }
            left[farthest] = left.getValue(farthest) - 1
        }
        return left
    }

    /** [wanted] from [tier], which holds more: in its order, the nearest level standing in for one it lacks. */
    private fun <T> mixed(
        tier: List<T>,
        wanted: Map<Difficulty, Int>,
        aim: Difficulty,
        levelOf: (T) -> Difficulty,
    ): List<T> {
        fun off(
            question: T,
            from: Difficulty,
        ) = distance(known(levelOf(question)), from)

        val left = tier.toMutableList()
        val taken = mutableListOf<T>()
        val lacking = mutableListOf<Difficulty>()
        LEVELS.forEach { level ->
            repeat(wanted.getValue(level)) {
                val at = left.indexOfFirst { off(it, level) == 0 }
                if (at < 0) lacking += level else taken += left.removeAt(at)
            }
        }
        lacking.sortedBy { distance(it, aim) }.forEach { level ->
            val nearest = left.indices.minWith(compareBy({ off(left[it], level) }, { off(left[it], aim) }))
            taken += left.removeAt(nearest)
        }
        return taken
    }

    companion object {
        /** The levels, from easy to hard. */
        val LEVELS: List<Difficulty> = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)

        /** Each level's share of a game, in percent, by [LEVELS], for each level a host may choose. */
        val SHARES: Map<Difficulty, List<Int>> =
            mapOf(
                Difficulty.EASY to listOf(60, 30, 10),
                Difficulty.MEDIUM to listOf(20, 60, 20),
                Difficulty.HARD to listOf(10, 30, 60),
            )

        private const val PERCENT = 100

        /** [level], or medium for one with no level. */
        fun known(level: Difficulty): Difficulty = if (level in LEVELS) level else Difficulty.MEDIUM

        private fun distance(
            a: Difficulty,
            b: Difficulty,
        ): Int = abs(LEVELS.indexOf(a) - LEVELS.indexOf(b))
    }
}
