package io.ntole.kvizic.server.rules

import kotlin.math.roundToInt

/**
 * How an answer scores, at most [MAX_POINTS] a question. Let `f` be the share of the answering time left
 * when the answer landed, 1 as the answers appear and 0 at the buzzer.
 *
 * - Right: [base] + [speed] · f, plus [orderBonus] for the first right answers, so both speed and beating
 *   the others to it count.
 * - Wrong: −([wrongMin] + [wrongSpeed] · f²) with the wrong-answer minus on, 0 with it off. The square
 *   keeps the minus small for a considered answer and large for a rushed one, so a blind click as the
 *   answers appear loses on average while a late guess barely pays. With fewer answers a blind hit is
 *   likelier, so the minus grows with it: scaled by 3 / (answers − 1), 1 for four answers.
 * - No answer: 0.
 *
 * `ScoringBalanceTest` pins what these numbers are for; retune them only with it.
 */
data class ScoringRules(
    val base: Int = 50,
    val speed: Int = 40,
    val orderBonus: List<Int> = listOf(10, 5, 2),
    val wrongMin: Int = 5,
    val wrongSpeed: Int = 35,
) {
    /** A right answer with [fractionLeft] of the time left, the [order]-th right answer (0 first), or later. */
    fun correct(
        fractionLeft: Double,
        order: Int?,
    ): Int {
        val f = fractionLeft.coerceIn(0.0, 1.0)
        val bonus = order?.let { orderBonus.getOrNull(it) } ?: 0
        return (base + speed * f).roundToInt() + bonus
    }

    /** A wrong answer with [fractionLeft] of the time left, to a question of [options] answers. */
    fun wrong(
        fractionLeft: Double,
        options: Int,
        penaltyOn: Boolean,
    ): Int {
        if (!penaltyOn) return 0
        val f = fractionLeft.coerceIn(0.0, 1.0)
        val scale = FOUR_ANSWERS_BLIND_ODDS / (options.coerceAtLeast(2) - 1)
        return -((wrongMin + wrongSpeed * f * f) * scale).roundToInt()
    }

    companion object {
        val DEFAULT: ScoringRules = ScoringRules()

        /** The most one question can score: a right answer as the answers appear, first. */
        const val MAX_POINTS: Int = 100

        /** Wrong answers per right one for a blind click on four answers, what the minus is sized for. */
        private const val FOUR_ANSWERS_BLIND_ODDS = 3.0
    }
}
