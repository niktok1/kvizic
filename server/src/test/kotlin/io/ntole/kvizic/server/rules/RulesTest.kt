package io.ntole.kvizic.server.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RulesTest {
    @Test
    fun `an answer's time is the time since the answers went out less the round trip, capped and clamped`() {
        val cap = 300.milliseconds
        assertEquals(800.milliseconds, answerTime(1.seconds, 200.milliseconds, cap, 15.seconds))
        assertEquals(700.milliseconds, answerTime(1.seconds, 5.seconds, cap, 15.seconds), "the cap")
        assertEquals(0.milliseconds, answerTime(100.milliseconds, 250.milliseconds, cap, 15.seconds), "never negative")
        assertEquals(
            15.seconds,
            answerTime(15.4.seconds, 0.milliseconds, cap, 15.seconds),
            "inside the grace counts as at the buzzer",
        )
        assertEquals(1.0, fractionLeft(0.milliseconds, 15.seconds))
        assertEquals(0.5, fractionLeft(7.5.seconds, 15.seconds))
        assertEquals(0.0, fractionLeft(20.seconds, 15.seconds))
    }

    @Test
    fun `the scoring's numbers are what the design says`() {
        val rules = ScoringRules.DEFAULT
        assertEquals(100, rules.correct(1.0, order = 0))
        assertEquals(95, rules.correct(1.0, order = 1))
        assertEquals(92, rules.correct(1.0, order = 2))
        assertEquals(90, rules.correct(1.0, order = 3), "the bonus is for the first three")
        assertEquals(50, rules.correct(0.0, order = null))
        assertEquals(-40, rules.wrong(1.0, 4, penaltyOn = true))
        assertEquals(-25, rules.wrong(0.75, 4, penaltyOn = true))
        assertEquals(-14, rules.wrong(0.5, 4, penaltyOn = true))
        assertEquals(-5, rules.wrong(0.0, 4, penaltyOn = true))
        assertEquals(-60, rules.wrong(1.0, 3, penaltyOn = true))
        assertEquals(-120, rules.wrong(1.0, 2, penaltyOn = true))
    }

    @Test
    fun `standings go by score, then right answers, then time, and exact ties share a rank`() {
        val ranked =
            rank(
                listOf(
                    Tally("a", score = 200, correct = 2, correctTimeMs = 5_000),
                    Tally("b", score = 300, correct = 3, correctTimeMs = 9_000),
                    Tally("c", score = 200, correct = 3, correctTimeMs = 9_000),
                    Tally("d", score = 200, correct = 2, correctTimeMs = 4_000),
                    Tally("e", score = 200, correct = 2, correctTimeMs = 4_000),
                    Tally("f", score = -40),
                ),
            )
        assertEquals(listOf("b", "c", "d", "e", "a", "f"), ranked.map { it.tally.playerId })
        assertEquals(listOf(1, 2, 3, 3, 5, 6), ranked.map { it.rank })
    }
}
