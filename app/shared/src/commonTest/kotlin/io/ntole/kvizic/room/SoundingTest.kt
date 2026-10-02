package io.ntole.kvizic.room

import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.Cues
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The seconds running out under a question: a tick each for the last few, the buzzer at the end. */
@OptIn(ExperimentalCoroutinesApi::class)
class SoundingTest {
    private class Played(
        val cue: Cue,
        val atMillis: Long,
    )

    /** Runs [sounding] over a question of [total] time on virtual time, and what it played and when. */
    private fun kotlinx.coroutines.test.TestScope.run(
        total: Duration,
        answeredAt: Long? = null,
    ): List<Played> {
        val played = mutableListOf<Played>()
        val cues =
            object : Cues {
                override fun play(
                    cue: Cue,
                    volume: Float,
                ) {
                    played += Played(cue, currentTime)
                }
            }
        val remaining = { (total - currentTime.milliseconds).coerceAtLeast(Duration.ZERO) }
        launch {
            sounding(
                remaining,
                warnSeconds = 5,
                answered = { answeredAt != null && currentTime >= answeredAt },
                cues = cues,
            )
        }
        advanceUntilIdle()
        return played
    }

    @Test
    fun `the last five seconds tick once each and the buzzer sounds at the end`() =
        runTest {
            val played = run(15.seconds)

            assertEquals(List(5) { Cue.TICK } + Cue.TIME_UP, played.map { it.cue })
            // On the seconds that are left: 5, 4, 3, 2, 1 and 0 to go, a millisecond past each.
            assertEquals(listOf(10_001L, 11_001L, 12_001L, 13_001L, 14_001L, 15_001L), played.map { it.atMillis })
        }

    @Test
    fun `a question joined late ticks from the seconds it has left`() =
        runTest {
            val played = run(3.seconds)

            assertEquals(List(3) { Cue.TICK } + Cue.TIME_UP, played.map { it.cue })
        }

    @Test
    fun `a player who answered hears neither ticks nor the buzzer`() =
        runTest {
            val played = run(15.seconds, answeredAt = 12_500)

            // Ticks at 5, 4 and 3 to go were before the answer, the rest and the buzzer are not heard.
            assertEquals(List(3) { Cue.TICK }, played.map { it.cue })
        }

    @Test
    fun `a question whose time is up sounds the buzzer at once`() =
        runTest {
            val played = run(Duration.ZERO)

            assertEquals(listOf(Cue.TIME_UP), played.map { it.cue })
        }
}
