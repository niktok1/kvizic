package io.ntole.kvizic.core.data.lobby

import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.domain.lobby.LobbyDifficulty
import io.ntole.kvizic.core.domain.lobby.LobbyRules
import io.ntole.kvizic.core.protocol.Reactions
import io.ntole.kvizic.core.question.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals

/** The domain's copies of the wire's settings limits, which the screens offer, are the wire's. */
class LobbyRulesTest {
    @Test
    fun `the settings a room may have are the wire's`() {
        assertEquals(KvizicApi.Limits.QUESTION_COUNTS, LobbyRules.QUESTION_COUNTS)
        assertEquals(KvizicApi.Limits.ANSWER_SECONDS, LobbyRules.ANSWER_SECONDS)
        assertEquals(KvizicApi.Limits.MIN_MAX_PLAYERS, LobbyRules.MIN_PLAYERS)
        assertEquals(KvizicApi.Limits.MAX_PLAYERS, LobbyRules.MAX_PLAYERS)
        assertEquals(KvizicApi.Limits.MAX_ROOM_NAME_LENGTH, LobbyRules.MAX_ROOM_NAME_LENGTH)
        assertEquals(
            Difficulty.entries.filter { it != Difficulty.UNKNOWN }.map { it.name },
            LobbyDifficulty.entries.map { it.name },
        )
    }

    @Test
    fun `the reactions a room may send are the wire's`() {
        assertEquals(Reactions.ALL, LobbyRules.REACTIONS)
        assertEquals(Reactions.NUDGE, LobbyRules.NUDGE)
    }
}
