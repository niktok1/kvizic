package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.lobby.Visibility
import io.ntole.kvizic.core.question.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LobbyRulesTest {
    @Test
    fun `a code is six digits, read through spaces and dashes`() {
        repeat(200) {
            val code = LobbyCode.generate { false }
            assertTrue(code.length == 6 && code.all(Char::isDigit), code)
        }
        assertEquals("482915", LobbyCode.parse("482 915"))
        assertEquals("482915", LobbyCode.parse(" 482-915 "))
        assertNull(LobbyCode.parse("48291"))
        assertNull(LobbyCode.parse("4829156"))
        assertNull(LobbyCode.parse("48291О"), "a Cyrillic O is no zero")
    }

    @Test
    fun `a new code is never one that is taken`() {
        val taken = mutableSetOf<String>()
        repeat(500) { taken += LobbyCode.generate { it in taken } }
        assertEquals(500, taken.size)
    }

    @Test
    fun `settings off the lists, below the members or naming unknown topics are refused`() {
        val topics = setOf("SPORT", "HISTORY")
        val fine =
            LobbySettingsDto(questionCount = 15, secondsPerQuestion = 20, topics = listOf("SPORT"), maxPlayers = 4)
        assertNull(settingsProblem(fine, LobbyKind.PRIVATE, topics, members = 4))
        assertNull(settingsProblem(LobbySettingsDto(), LobbyKind.PUBLIC, topics), "the defaults, Све, are fine")
        listOf(
            fine.copy(questionCount = 12),
            fine.copy(secondsPerQuestion = 25),
            fine.copy(maxPlayers = 9),
            fine.copy(maxPlayers = 1),
            fine.copy(topics = listOf("SPORT", "SPORT")),
            fine.copy(topics = listOf("COOKING")),
            fine.copy(visibility = Visibility.UNKNOWN),
        ).forEach { refused -> assertNotNull(settingsProblem(refused, LobbyKind.PRIVATE, topics), "$refused") }
        assertNotNull(settingsProblem(fine.copy(maxPlayers = 3), LobbyKind.PRIVATE, topics, members = 4))
        assertNull(settingsProblem(LobbySettingsDto.SOLO, LobbyKind.SOLO, topics))
        assertNull(settingsProblem(LobbySettingsDto.SOLO.copy(difficulty = Difficulty.HARD), LobbyKind.SOLO, topics))
        assertNotNull(
            settingsProblem(LobbySettingsDto.SOLO.copy(difficulty = Difficulty.UNKNOWN), LobbyKind.SOLO, topics),
        )
        assertNotNull(settingsProblem(fine, LobbyKind.SOLO, topics), "a solo run keeps its format")
    }
}
