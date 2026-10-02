package io.ntole.kvizic.server.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The curve: quick at first, then each level takes more than the one before. */
class LevelsTest {
    @Test
    fun `the first game is the second level and the second game the third`() {
        assertEquals(1, Levels.of(0).number)
        val one = Levels.award(correct = 7, won = false)
        assertEquals(2, Levels.of(one).number)
        assertEquals(3, Levels.of(one + Levels.award(correct = 7, won = false)).number)
    }

    @Test
    fun `a level begins where ten times the square of the one before it says`() {
        listOf(1 to 0, 2 to 10, 3 to 40, 5 to 160, 10 to 810, 20 to 3_610, 50 to 24_010).forEach { (level, xp) ->
            assertEquals(xp.toLong(), Levels.startOf(level))
            assertEquals(level, Levels.of(xp).number, "level $level begins at $xp")
            if (level > 1) assertEquals(level - 1, Levels.of(xp - 1).number, "and the one before ends just short")
        }
    }

    @Test
    fun `progress is how far into the level and how much the level takes`() {
        assertEquals(Levels.Progress(number = 1, into = 0, span = 10), Levels.of(0))
        assertEquals(Levels.Progress(number = 2, into = 10, span = 30), Levels.of(20))
        assertEquals(Levels.Progress(number = 3, into = 0, span = 50), Levels.of(40))
        assertEquals(Levels.Progress(number = 3, into = 49, span = 50), Levels.of(89))
    }

    @Test
    fun `every level takes more than the one before, from the second on`() {
        val spans = (1..200).map { Levels.of(Levels.startOf(it).toInt()).span }
        assertTrue(spans.zipWithNext().all { (a, b) -> b > a }, "the spans only grow")
    }

    @Test
    fun `no experience is no level below the first, and a huge amount is no overflow`() {
        assertEquals(1, Levels.of(-5).number)
        assertEquals(14_655, Levels.of(Int.MAX_VALUE).number)
    }

    @Test
    fun `a game earns for playing, for each right answer and for the win`() {
        assertEquals(20, Levels.award(correct = 0, won = false))
        assertEquals(27, Levels.award(correct = 7, won = false))
        assertEquals(47, Levels.award(correct = 7, won = true))
    }
}
