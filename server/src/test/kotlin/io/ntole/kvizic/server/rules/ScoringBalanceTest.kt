package io.ntole.kvizic.server.rules

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What the scoring is for, pinned: fast random clicking must not pay, knowing must, and the minus must
 * stay small. Retune [ScoringRules] only with these holding.
 */
class ScoringBalanceTest {
    private val rules = ScoringRules.DEFAULT
    private val fractions = (0..100).map { it / 100.0 }

    /** A blind pick among [options] with [f] of the time left, on average; [first] if a hit would be the first. */
    private fun blindAverage(
        f: Double,
        options: Int = 4,
        first: Boolean = false,
    ): Double {
        val hit = 1.0 / options
        return hit * rules.correct(f, if (first) 0 else null) + (1 - hit) * rules.wrong(f, options, penaltyOn = true)
    }

    @Test
    fun `a blind click as the answers appear loses on average, first or not, for 2, 3 or 4 answers`() {
        listOf(2, 3, 4).forEach { options ->
            assertTrue(
                blindAverage(1.0, options, first = true) < 0,
                "$options answers, first: ${blindAverage(1.0, options, true)}",
            )
            assertTrue(blindAverage(1.0, options) < 0, "$options answers")
        }
    }

    @Test
    fun `a blind guess never averages more than about 10 a question, and knowing earns 50 to 100`() {
        fractions.forEach { f -> assertTrue(blindAverage(f) <= 10.0, "at $f: ${blindAverage(f)}") }
        fractions.forEach { f ->
            assertTrue(rules.correct(f, order = null) in 50..90)
            assertTrue(rules.correct(f, order = 0) in 60..100)
        }
    }

    @Test
    fun `a right answer beats none and a faster right answer beats a slower one`() {
        fractions.zipWithNext().forEach { (slower, faster) ->
            assertTrue(rules.correct(faster, null) >= rules.correct(slower, null))
            assertTrue(rules.wrong(faster, 4, true) <= rules.wrong(slower, 4, true), "a rushed wrong answer costs more")
        }
        assertTrue(rules.correct(0.0, null) > 0)
    }

    @Test
    fun `ruling out wrong answers pays at any speed`() {
        fractions.forEach { f ->
            val oneOfThree = (1.0 / 3) * rules.correct(f, null) + (2.0 / 3) * rules.wrong(f, 4, true)
            val oneOfTwo = 0.5 * rules.correct(f, null) + 0.5 * rules.wrong(f, 4, true)
            assertTrue(oneOfThree > 0, "one of three left at $f: $oneOfThree")
            assertTrue(oneOfTwo > oneOfThree)
        }
    }

    @Test
    fun `no wrong answer to four answers costs more than 40, and the minus off costs nothing`() {
        fractions.forEach { f ->
            assertTrue(rules.wrong(f, 4, penaltyOn = true) >= -40)
            assertTrue(rules.wrong(f, 4, penaltyOn = false) == 0)
        }
        assertTrue(rules.correct(1.0, 0) == ScoringRules.MAX_POINTS)
    }

    @Test
    fun `an expert beats a fast blind clicker in more than 99 percent of 10-question games`() {
        val random = Random(2026)
        val games = 10_000
        val expertWins =
            (1..games).count {
                var expert = 0
                var clicker = 0
                repeat(10) {
                    val f = 0.3 + random.nextDouble() * 0.6
                    expert += if (random.nextDouble() < 0.8) rules.correct(f, null) else rules.wrong(f, 4, true)
                    clicker += if (random.nextInt(4) == 0) rules.correct(1.0, 0) else rules.wrong(1.0, 4, true)
                }
                expert > clicker
            }
        assertTrue(expertWins > games * 0.99, "expert won $expertWins of $games")
    }
}
