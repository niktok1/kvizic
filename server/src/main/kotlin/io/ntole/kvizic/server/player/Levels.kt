package io.ntole.kvizic.server.player

import kotlin.math.sqrt

/**
 * A player's level, worked out from the experience they have earned: a game finished in a room earns
 * [PER_GAME], each right answer in it [PER_CORRECT] and a win [PER_WIN]; a solo run earns nothing. The total
 * experience that level `n` begins at is `10 · (n − 1)²`, so the first levels come with the first games (the
 * second after one, the fifth after five) and each later one takes more than the one before: the tenth takes
 * about 25 games, the twentieth about 120, the fiftieth about 800.
 */
object Levels {
    const val PER_GAME: Int = 20
    const val PER_CORRECT: Int = 1
    const val PER_WIN: Int = 20

    private const val STEP: Int = 10

    /** Where a player stands: [number] is the level, [into] the experience earned in it, [span] what it takes. */
    data class Progress(
        val number: Int,
        val into: Int,
        val span: Int,
    )

    /** What a finished game in a room earns a player who answered [correct] right, [won] it or not. */
    fun award(
        correct: Int,
        won: Boolean,
    ): Int = PER_GAME + correct * PER_CORRECT + if (won) PER_WIN else 0

    /** The experience level [number] begins at, in a long: the levels past 14 600 are beyond an int's. */
    fun startOf(number: Int): Long = STEP.toLong() * (number - 1) * (number - 1)

    /** Where a player with [xp] experience stands. */
    fun of(xp: Int): Progress {
        val earned = xp.coerceAtLeast(0).toLong()
        var number = sqrt(earned.toDouble() / STEP).toInt() + 1
        // The root of a double can be a hair off a whole number.
        while (startOf(number + 1) <= earned) number++
        while (startOf(number) > earned) number--
        return Progress(number, (earned - startOf(number)).toInt(), (startOf(number + 1) - startOf(number)).toInt())
    }
}
