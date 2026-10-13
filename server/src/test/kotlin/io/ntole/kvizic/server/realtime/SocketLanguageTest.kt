package io.ntole.kvizic.server.realtime

import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.ServerMessage
import io.ntole.kvizic.core.region.ContentLanguages
import io.ntole.kvizic.server.lobby.slotOf
import io.ntole.kvizic.server.lobby.textIn
import io.ntole.kvizic.server.lobby.translatedQuestions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A socket's hello names the UI language its player uses, and the lobby sends them the game in what that reads. */
class SocketLanguageTest {
    @Test
    fun `each socket reads in the content language its hello's UI language reads`() =
        runGameServer("socket-language", questions = translatedQuestions(5)) { server ->
            val question = translatedQuestions(5).first()
            val ticket = server.create(server.guest(), LobbySettingsDto(questionCount = 5, secondsPerQuestion = 10))
            val ana = server.connect(ticket, language = "hr").also { it.await<ServerMessage.Snapshot>() }
            // An old client says none; Serbian Latin is made from the Cyrillic on the client.
            val boris =
                server.connect(server.join(server.guest(), ticket.code)).also { it.await<ServerMessage.Snapshot>() }
            val ceca =
                server.connect(server.join(server.guest(), ticket.code), language = "sr-Latn").also {
                    it.await<ServerMessage.Snapshot>()
                }

            ana.send(ClientMessage.Start(ana.nextId()))
            val croatian = question.textIn(ContentLanguages.CROATIAN)
            assertEquals(croatian.text, ana.await<ServerMessage.QuestionShown>().question.text)
            assertEquals(question.text, boris.await<ServerMessage.QuestionShown>().question.text)
            assertEquals(question.text, ceca.await<ServerMessage.QuestionShown>().question.text)

            val opened = listOf(ana, boris, ceca).map { it.await<ServerMessage.AnswersOpened>() }
            assertEquals(croatian.options.toSet(), opened[0].options.toSet())
            assertEquals(question.options.toSet(), opened[1].options.toSet())
            assertEquals(1, opened.map { it.options.map(::slotOf) }.toSet().size, "one order for every language")

            listOf(ana, boris, ceca).forEach { socket ->
                socket.send(ClientMessage.Answer(socket.nextId(), 0, rightOptionOf(opened[1])))
            }
            val reveals = listOf(ana, boris, ceca).map { it.await<ServerMessage.Revealed>().reveal }
            assertEquals(croatian.explanation, reveals[0].explanation)
            assertEquals(question.explanation, reveals[1].explanation)
            assertEquals(1, reveals.map { it.correct }.toSet().size)
            assertTrue(reveals.first().results.all { it.points > 0 }, "the same index, the right answer for all")
            assertEquals(setOf(ContentLanguages.CROATIAN, ContentLanguages.SERBIAN), server.asked.single().languages)
        }
}
