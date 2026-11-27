package io.ntole.kvizic.server.rules

import io.ntole.kvizic.server.config.ServerConfig
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CoinRulesTest {
    private fun finishers(vararg ranks: Int) = ranks.mapIndexed { i, rank -> Ranked(Tally("p$i"), rank) }

    @Test
    fun `places pay for each other finisher, and tied players share their places' pay`() {
        val cases =
            listOf(
                Triple(listOf(10), finishers(1, 2), mapOf("p0" to 10)),
                Triple(listOf(10), finishers(1, 2, 3, 4), mapOf("p0" to 30)),
                Triple(listOf(10), finishers(1, 1), mapOf("p0" to 5, "p1" to 5)),
                Triple(listOf(10), finishers(1, 1, 3), mapOf("p0" to 10, "p1" to 10)),
                Triple(listOf(10), finishers(1, 1, 1), mapOf("p0" to 6, "p1" to 6, "p2" to 6)),
                Triple(listOf(10), finishers(1), emptyMap()),
                Triple(listOf(10, 5), finishers(1, 2, 2), mapOf("p0" to 20, "p1" to 5, "p2" to 5)),
                Triple(listOf(0), finishers(1, 2), emptyMap()),
            )
        cases.forEach { (pay, ranked, expected) ->
            assertEquals(expected, CoinRules(pay).earned(ranked), "$pay $ranked")
        }
    }

    @Test
    fun `COIN_PAY sets the pay, and anything but whole numbers from 0 refuses to start`() {
        fun pay(raw: String?) =
            ServerConfig
                .fromEnvironment(mapOf("COIN_PAY" to raw)::get)
                .game.coins.pay

        mapOf(
            null to listOf(10),
            " " to listOf(10),
            "0" to listOf(0),
            " 10, 5 ,2" to listOf(10, 5, 2),
        ).forEach { (raw, expected) ->
            assertEquals(expected, pay(raw), "\"$raw\"")
        }
        listOf("-1", "10;5", "ten", "10,", "1.5").forEach { raw ->
            assertContains(assertFailsWith<IllegalArgumentException>(raw) { pay(raw) }.message.orEmpty(), "COIN_PAY")
        }
    }
}
