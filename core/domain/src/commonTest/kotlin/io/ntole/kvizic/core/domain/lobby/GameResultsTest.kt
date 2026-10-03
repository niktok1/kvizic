package io.ntole.kvizic.core.domain.lobby

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameResultsTest {
    private fun standing(
        id: String,
        rank: Int,
        finished: Boolean = true,
    ) = FinalStanding(id, id, "fox", score = 0, correct = 0, rank = rank, finished = finished)

    private fun results(vararg standings: FinalStanding) =
        GameResults("game", 10, standings.toList(), endedEarly = false, personalBest = null)

    @Test
    fun `the one alone at the top of two or more who stayed wins`() {
        assertEquals("ana", results(standing("ana", 1), standing("boris", 2)).winner?.playerId)
    }

    @Test
    fun `nobody wins a tie at the top`() {
        assertNull(results(standing("ana", 1), standing("boris", 1), standing("ceca", 3)).winner)
    }

    @Test
    fun `nobody who left wins nor one left alone`() {
        // As a server before finishers were ranked first had it: the one who left first of a tie.
        assertNull(results(standing("ana", 1, finished = false), standing("boris", 1)).winner)
        assertNull(results(standing("solo", 1)).winner)
    }

    @Test
    fun `a first run sets a best and beats none`() {
        assertFalse(PersonalBest(score = -130, previous = null, isNew = true).beaten)
        assertTrue(PersonalBest(score = 300, previous = 200, isNew = true).beaten)
        assertFalse(PersonalBest(score = 100, previous = 200, isNew = false).beaten)
    }
}
