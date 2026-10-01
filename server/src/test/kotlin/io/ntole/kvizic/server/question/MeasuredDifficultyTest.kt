package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.question.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals

/** How hard a question plays: its author's level at first, then what its players make of it. */
class MeasuredDifficultyTest {
    private fun level(
        authored: Difficulty,
        answered: Int,
        correct: Int,
        unanswered: Int = 0,
        options: Int = 4,
    ) = MeasuredDifficulty.of(authored, options, answered, correct, unanswered)

    @Test
    fun `a question no one has been asked plays at its author's level`() {
        Difficulty.entries.forEach { authored ->
            val expected = if (authored == Difficulty.UNKNOWN) Difficulty.MEDIUM else authored
            assertEquals(expected, level(authored, answered = 0, correct = 0), "$authored")
        }
    }

    @Test
    fun `a room's answers move it a little, many rooms' decide it`() {
        // One room no better than a guess, then many.
        assertEquals(Difficulty.EASY, level(Difficulty.EASY, answered = 8, correct = 2))
        assertEquals(Difficulty.HARD, level(Difficulty.EASY, answered = 200, correct = 50))
        assertEquals(Difficulty.EASY, level(Difficulty.HARD, answered = 200, correct = 190))
        assertEquals(Difficulty.MEDIUM, level(Difficulty.HARD, answered = 200, correct = 120))
    }

    @Test
    fun `a silence counts as not knowing`() {
        assertEquals(Difficulty.EASY, level(Difficulty.MEDIUM, answered = 100, correct = 90))
        assertEquals(Difficulty.HARD, level(Difficulty.MEDIUM, answered = 100, correct = 90, unanswered = 150))
    }

    @Test
    fun `a guess's share is easy to reach with two answers and hard with four`() {
        // Eight in ten right: far above a guess among four, not so far above a coin's toss.
        assertEquals(Difficulty.MEDIUM, level(Difficulty.MEDIUM, answered = 300, correct = 240, options = 2))
        assertEquals(Difficulty.EASY, level(Difficulty.MEDIUM, answered = 300, correct = 240, options = 4))
        assertEquals(Difficulty.HARD, level(Difficulty.MEDIUM, answered = 300, correct = 150, options = 2))
    }
}
