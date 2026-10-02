package io.ntole.kvizic.room

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Who is heard reacting: a player once in thirty seconds, and the room one at a time. */
class ReactionVoicesTest {
    private var now = 0L
    private val voices = ReactionVoices(nowMillis = { now })

    @Test
    fun `a player is heard once in thirty seconds whatever they send`() {
        assertTrue(voices.allow("nina"))
        repeat(20) {
            now += 1_000
            assertFalse(voices.allow("nina"), "${now / 1_000} s after the first")
        }
        now += 9_000
        assertFalse(voices.allow("nina"), "29 s after")
        now += 1_000
        assertTrue(voices.allow("nina"), "30 s after")
    }

    @Test
    fun `players are heard each in their own turn`() {
        assertTrue(voices.allow("nina"))
        now += 1_000
        assertTrue(voices.allow("bojan"), "another player is not held up by nina")
        now += 1_000
        assertFalse(voices.allow("nina"))
        assertFalse(voices.allow("bojan"))
    }

    @Test
    fun `the room is heard one reaction at a time`() {
        assertTrue(voices.allow("nina"))
        now += 399
        assertFalse(voices.allow("bojan"), "within the shared gap")
        now += 1
        assertTrue(voices.allow("bojan"), "past it")
    }

    @Test
    fun `a reaction that was not heard does not count against its player`() {
        assertTrue(voices.allow("nina"))
        now += 100
        assertFalse(voices.allow("bojan"), "held off by nina's")
        now += 400
        assertTrue(voices.allow("bojan"), "bojan was never heard, so he is now")
    }

    @Test
    fun `eight players sending at once are heard one after another`() {
        val players = List(8) { "p$it" }
        val heard = players.count { voices.allow(it) }
        assertTrue(heard == 1, "heard $heard of 8 at one instant")
    }
}
