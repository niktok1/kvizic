package io.ntole.kvizic.server.rules

/**
 * What a room's finished game pays in coins. [pay] is what each place earns for every other player who stayed
 * to the end, first place first; a place past the list earns nothing. Players tied share what their places pay
 * together, each the same, rounded down. A room of one pays nothing, and a solo run is never asked. The default
 * pays the winner alone, 10 for each other finisher; `COIN_PAY` sets it on a server.
 */
data class CoinRules(
    val pay: List<Int> = listOf(10),
) {
    init {
        require(pay.all { it >= 0 }) { "a place pays 0 coins or more, not $pay" }
    }

    /** What each of [finishers], ranked among themselves, earns, by player id: none for one who earns nothing. */
    fun earned(finishers: List<Ranked>): Map<String, Int> {
        val others = finishers.size - 1
        if (others < 1) return emptyMap()
        return finishers
            .groupBy { it.rank }
            .flatMap { (rank, tied) ->
                val places = rank - 1 until rank - 1 + tied.size
                val each = places.sumOf { pay.getOrElse(it) { 0 } } * others / tied.size
                tied.map { it.tally.playerId to each }
            }.filter { (_, coins) -> coins > 0 }
            .toMap()
    }

    companion object {
        val DEFAULT: CoinRules = CoinRules()
    }
}
