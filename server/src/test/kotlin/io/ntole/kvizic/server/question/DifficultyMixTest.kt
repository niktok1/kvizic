package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.Difficulty.EASY
import io.ntole.kvizic.core.question.Difficulty.HARD
import io.ntole.kvizic.core.question.Difficulty.MEDIUM
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** A game's mix of levels: mostly the one its host chose, the rest beside it, after who has seen what. */
class DifficultyMixTest {
    private val mix = DifficultyMix(Random(7))

    /** Questions named for their level: `e1` is easy, `m2` medium, `h3` hard. */
    private fun levelOf(id: String) =
        when (id.first()) {
            'e' -> EASY
            'm' -> MEDIUM
            else -> HARD
        }

    private fun questions(
        prefix: Char,
        count: Int,
    ) = (1..count).map { "$prefix$it" }

    private fun levels(picked: List<String>) = picked.groupingBy(::levelOf).eachCount()

    @Test
    fun `a game is mostly at the level chosen and the rest beside it`() {
        assertEquals(mapOf(EASY to 6, MEDIUM to 3, HARD to 1), mix.quotas(EASY, 10))
        assertEquals(mapOf(EASY to 2, MEDIUM to 6, HARD to 2), mix.quotas(MEDIUM, 10))
        assertEquals(mapOf(EASY to 1, MEDIUM to 3, HARD to 6), mix.quotas(HARD, 10))
        assertEquals(mapOf(EASY to 4, MEDIUM to 12, HARD to 4), mix.quotas(MEDIUM, 20))
        assertEquals(mix.quotas(MEDIUM, 10), mix.quotas(Difficulty.UNKNOWN, 10), "no level is medium")
    }

    @Test
    fun `a share that does not come out whole falls at random among the largest remainders`() {
        val easyFives = (1..200).map { mix.quotas(EASY, 5) }.toSet()
        assertEquals(
            setOf(mapOf(EASY to 3, MEDIUM to 2, HARD to 0), mapOf(EASY to 3, MEDIUM to 1, HARD to 1)),
            easyFives,
        )
        KvizicApi.Limits.QUESTION_COUNTS.forEach { count ->
            DifficultyMix.LEVELS.forEach { chosen ->
                assertEquals(count, mix.quotas(chosen, count).values.sum(), "$count at $chosen")
            }
        }
    }

    @Test
    fun `a tier with every level gives each its share`() {
        val tier = questions('e', 10) + questions('m', 10) + questions('h', 10)
        assertEquals(
            mapOf(EASY to 1, MEDIUM to 3, HARD to 6),
            levels(mix.pick(listOf(tier.shuffled(Random(1))), 10, HARD, ::levelOf)),
        )
    }

    @Test
    fun `a tier the game has room for is taken whole, and the levels farthest from the choice give way`() {
        val unseen = questions('h', 3)
        val seen = questions('e', 10) + questions('m', 10) + questions('h', 10).map { "${it}x" }
        val picked = mix.pick(listOf(unseen, seen), 10, EASY, ::levelOf)
        assertEquals(10, picked.size)
        assertEquals(unseen, picked.take(3), "the unseen, hard as they are")
        assertEquals(mapOf(EASY to 6, MEDIUM to 1, HARD to 3), levels(picked))
    }

    @Test
    fun `a level the tier lacks takes the nearest it has`() {
        val tier = questions('e', 10) + questions('m', 10)
        assertEquals(mapOf(EASY to 1, MEDIUM to 9), levels(mix.pick(listOf(tier), 10, HARD, ::levelOf)))
    }

    @Test
    fun `among questions alike the tier's order decides`() {
        assertEquals(setOf("m1", "m2", "m3"), mix.pick(listOf(questions('m', 9)), 3, MEDIUM, ::levelOf).toSet())
    }
}
