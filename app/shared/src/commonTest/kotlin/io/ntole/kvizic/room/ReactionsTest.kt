package io.ntole.kvizic.room

import io.ntole.kvizic.core.domain.lobby.LobbyRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The room's reactions are the server's, each drawn, and the nudge to start is for the members alone. */
class ReactionsTest {
    @Test
    fun `every reaction the server takes has an icon here in its order`() {
        assertEquals(LobbyRules.REACTIONS, REACTIONS.map { it.id })
    }

    @Test
    fun `the nudge is no emote on the bar`() {
        assertFalse(EMOTES.any { it.id == LobbyRules.NUDGE })
        assertTrue(REACTIONS.any { it.id == LobbyRules.NUDGE })
    }
}
