package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.question.Difficulty

/**
 * How hard a question plays: its author's level until its players say otherwise. Of the players it was
 * asked, a silence counted as not knowing, the share who got it right is set against the share a guess
 * would get among its answers, so a true/false question is not easy for being a coin's toss. The author's
 * level stands for [PRIOR_PLAYERS] players at its typical share, so a room's answers move it a little and
 * many rooms' decide it. Read when a game is picked and when the moderator looks; never stored.
 */
object MeasuredDifficulty {
    /** How many players' worth the author's level weighs. */
    const val PRIOR_PLAYERS = 40

    /** At or above this share above a guess a question plays easy, below [HARD_BELOW] hard. */
    const val EASY_FROM = 0.6
    const val HARD_BELOW = 0.3

    /** Each level's typical share above a guess, as far from the thresholds on either side. */
    private val TYPICAL =
        mapOf(
            Difficulty.EASY to 0.75,
            Difficulty.MEDIUM to 0.45,
            Difficulty.HARD to 0.15,
        )

    /**
     * The level a question written as [authored], with [options] answers, plays at, of [correct] right
     * among [answered] answers and [unanswered] silences. A level the author left out is taken as medium.
     */
    fun of(
        authored: Difficulty,
        options: Int,
        answered: Int,
        correct: Int,
        unanswered: Int,
    ): Difficulty {
        val typical = TYPICAL[authored] ?: TYPICAL.getValue(Difficulty.MEDIUM)
        val asked = answered + unanswered
        val skill =
            if (asked == 0 || options < 2) {
                typical
            } else {
                val guess = 1.0 / options
                (correct.toDouble() / asked - guess) / (1 - guess)
            }
        val measured = (skill * asked + typical * PRIOR_PLAYERS) / (asked + PRIOR_PLAYERS)
        return when {
            measured >= EASY_FROM -> Difficulty.EASY
            measured < HARD_BELOW -> Difficulty.HARD
            else -> Difficulty.MEDIUM
        }
    }
}
