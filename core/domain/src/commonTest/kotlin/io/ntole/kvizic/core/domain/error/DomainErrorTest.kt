package io.ntole.kvizic.core.domain.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A failure's name is its code in analytics, so the platform's and the game's never share one. */
class DomainErrorTest {
    @Test
    fun `no failure of the platform's and the game's has the same name`() {
        val core = CoreError.entries.map(DomainError::name)
        val game = GameError.entries.map(DomainError::name)

        assertEquals(emptySet(), core.toSet() intersect game.toSet())
    }

    @Test
    fun `an exception names its failure unless given a message`() {
        assertEquals("LOBBY_FULL", KvizicException(GameError.LOBBY_FULL).message)
        assertEquals("offline", KvizicException(CoreError.NETWORK, "offline").message)
        assertTrue(KvizicException(CoreError.RATE_LIMITED).retryAfter == null)
    }
}
