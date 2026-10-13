package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.PhaseView
import io.ntole.kvizic.core.protocol.ServerMessage
import io.ntole.kvizic.core.region.ContentLanguages
import io.ntole.kvizic.core.region.Country
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Each member reads a question in their own language, the answers in the one order the game shuffled, and
 * the questions are asked for the countries and languages of whoever is in the room.
 */
class LobbyLanguagesTest {
    /** A player from [country] who reads [language]. */
    private fun LobbyScenario.reader(
        name: String,
        country: Country = Country.RS,
        language: String = ContentLanguages.SERBIAN,
    ): TestPlayer =
        player(name).also {
            it.country = country
            it.language = language
        }

    private fun TestPlayer.resynced(): PhaseView {
        say(ClientMessage.Resync)
        return checkNotNull(lastSnapshot()).phase
    }

    @Test
    fun `a Croat and an old client each read the question, its answers and its explanation in their own words`() =
        runTest {
            val lobby = LobbyScenario(this, LobbySettingsDto(questionCount = 2), questions = translatedQuestions(2))
            val ana = lobby.reader("ana", language = ContentLanguages.CROATIAN).join()
            // An old client's hello says no language: it reads Serbian Cyrillic.
            val boris = lobby.player("boris").join()
            val question = lobby.questions.first()
            val croatian = question.textIn(ContentLanguages.CROATIAN)

            ana.start()
            lobby.wait(lobby.timings.countdown)
            assertEquals(croatian.text, ana.last<ServerMessage.QuestionShown>().question.text)
            assertEquals(question.text, boris.last<ServerMessage.QuestionShown>().question.text)
            assertEquals(croatian.text, (ana.resynced() as PhaseView.Reading).question.text)
            assertEquals(question.text, (boris.resynced() as PhaseView.Reading).question.text)

            lobby.wait(lobby.timings.readMax)
            val anaOptions = ana.last<ServerMessage.AnswersOpened>().options
            val borisOptions = boris.last<ServerMessage.AnswersOpened>().options
            assertEquals(croatian.options.toSet(), anaOptions.toSet())
            assertEquals(question.options.toSet(), borisOptions.toSet())
            val anaAnswering = ana.resynced() as PhaseView.Answering
            assertEquals(croatian.text, anaAnswering.question.text)
            assertEquals(anaOptions, anaAnswering.options)
            assertEquals(borisOptions, (boris.resynced() as PhaseView.Answering).options)

            ana.answerRight()
            boris.answerRight()
            val anaReveal = ana.last<ServerMessage.Revealed>().reveal
            val borisReveal = boris.last<ServerMessage.Revealed>().reveal
            assertEquals(croatian.text, anaReveal.text)
            assertEquals(anaOptions, anaReveal.options)
            assertEquals(croatian.explanation, anaReveal.explanation)
            assertEquals(question.text, borisReveal.text)
            assertEquals(borisOptions, borisReveal.options)
            assertEquals(question.explanation, borisReveal.explanation)
            assertEquals(
                borisReveal,
                anaReveal.copy(text = question.text, options = borisOptions, explanation = question.explanation),
                "everything but the words is the same reveal",
            )
            assertEquals(anaReveal, (ana.resynced() as PhaseView.Revealing).reveal)
            assertEquals(borisReveal, (boris.resynced() as PhaseView.Revealing).reveal)
            lobby.assertInvariants()
        }

    @Test
    fun `the same index is the same answer in every language`() =
        runTest {
            val lobby = LobbyScenario(this, LobbySettingsDto(questionCount = 5), questions = translatedQuestions(5))
            val ana = lobby.reader("ana", language = ContentLanguages.CROATIAN).join()
            val boris = lobby.player("boris").join()
            ana.start()
            lobby.wait(lobby.timings.countdown)

            repeat(5) { index ->
                lobby.wait(lobby.timings.readMax)
                val anaOptions = ana.last<ServerMessage.AnswersOpened>().options
                val borisOptions = boris.last<ServerMessage.AnswersOpened>().options
                assertEquals(anaOptions.map(::slotOf), borisOptions.map(::slotOf), "question $index, one order")

                // Both pick the same place: the same answer, so both right or both wrong, alike.
                val picked = index % anaOptions.size
                ana.answer(picked)
                boris.answer(picked)
                val reveal = ana.last<ServerMessage.Revealed>().reveal
                assertEquals(boris.last<ServerMessage.Revealed>().reveal.correct, reveal.correct)
                val results = reveal.results.associateBy { it.player }
                assertEquals(picked, results.getValue("ana").option)
                assertEquals(picked, results.getValue("boris").option)
                assertEquals(results.getValue("ana").points > 0, results.getValue("boris").points > 0)
                assertEquals(picked == reveal.correct, results.getValue("ana").points > 0)
                assertEquals(0, slotOf(reveal.options[reveal.correct]), "the right one is the source's first")
                lobby.wait(reveal.nextInMs.milliseconds)
            }
            lobby.assertInvariants()
        }

    @Test
    fun `a member who comes back in another language reads it from then on`() =
        runTest {
            val lobby = LobbyScenario(this, LobbySettingsDto(questionCount = 3), questions = translatedQuestions(3))
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            ana.start()
            lobby.wait(lobby.timings.countdown)
            assertEquals(lobby.questions[0].text, ana.last<ServerMessage.QuestionShown>().question.text)

            ana.drop()
            ana.language = ContentLanguages.CROATIAN
            ana.attach()
            val croatian = lobby.questions[0].textIn(ContentLanguages.CROATIAN)
            assertEquals(croatian.text, (checkNotNull(ana.lastSnapshot()).phase as PhaseView.Reading).question.text)

            lobby.wait(lobby.timings.readMax)
            assertEquals(croatian.options.toSet(), ana.last<ServerMessage.AnswersOpened>().options.toSet())
            assertEquals(lobby.questions[0].options.toSet(), boris.last<ServerMessage.AnswersOpened>().options.toSet())
            lobby.assertInvariants()
        }

    @Test
    fun `a language with no text of its own reads the nearest, and the source last`() =
        runTest {
            val lobby = LobbyScenario(this, LobbySettingsDto(questionCount = 1), questions = translatedQuestions(1))
            // Bosnian has no text of its own here, and looks in Croatian next.
            val ana = lobby.reader("ana", language = ContentLanguages.BOSNIAN).join()
            val boris = lobby.reader("boris", language = ContentLanguages.MACEDONIAN).join()
            ana.start()
            lobby.wait(lobby.timings.countdown)

            val croatian = lobby.questions[0].textIn(ContentLanguages.CROATIAN)
            assertEquals(croatian.text, ana.last<ServerMessage.QuestionShown>().question.text)
            // Macedonian looks in Serbian before Croatian, and Serbian is the source.
            assertEquals(lobby.questions[0].text, boris.last<ServerMessage.QuestionShown>().question.text)
        }

    @Test
    fun `the question is read for as long as the longest of its readers' texts takes`() =
        runTest {
            val lobby = LobbyScenario(this, LobbySettingsDto(questionCount = 1), questions = translatedQuestions(1))
            val ana = lobby.player("ana").join()
            val boris = lobby.player("boris").join()
            val question = lobby.questions[0]
            ana.start()
            lobby.wait(lobby.timings.countdown)
            assertEquals(
                lobby.timings.readTime(question.text).inWholeMilliseconds,
                boris.last<ServerMessage.QuestionShown>().readMs,
                "a room of Serbian readers reads the Serbian",
            )

            val mixed = LobbyScenario(this, LobbySettingsDto(questionCount = 1), questions = translatedQuestions(1))
            val ceca = mixed.player("ceca").join()
            mixed.reader("dragan", language = ContentLanguages.CROATIAN).join()
            ceca.start()
            mixed.wait(mixed.timings.countdown)
            val longer = mixed.timings.readTime(question.textIn(ContentLanguages.CROATIAN).text)
            assertEquals(longer.inWholeMilliseconds, ceca.last<ServerMessage.QuestionShown>().readMs)
        }

    @Test
    fun `a reveal is shown longer when a reader of it has an explanation, and none goes where there is none`() =
        runTest {
            val plain = translatedQuestions(1, explained = false).single()
            val explainedInCroatian =
                plain.copy(
                    texts =
                        plain.texts.mapValues { (_, text) -> text.copy(explanation = "Objašnjenje-hr samo") },
                )
            val lobby =
                LobbyScenario(this, LobbySettingsDto(questionCount = 1), questions = listOf(explainedInCroatian))
            val ana = lobby.reader("ana", language = ContentLanguages.CROATIAN).join()
            val boris = lobby.player("boris").join()
            ana.start()
            lobby.wait(lobby.timings.countdown + lobby.timings.readMax)
            ana.answerRight()
            boris.answerRight()

            val anaReveal = ana.last<ServerMessage.Revealed>().reveal
            assertEquals("Objašnjenje-hr samo", anaReveal.explanation)
            assertNull(boris.last<ServerMessage.Revealed>().reveal.explanation)
            assertEquals(lobby.timings.revealWithExplanation.inWholeMilliseconds, anaReveal.nextInMs)
        }

    @Test
    fun `the questions are asked for the room's countries and languages`() =
        runTest {
            val lobby = LobbyScenario(this, LobbySettingsDto(questionCount = 2), questions = translatedQuestions(2))
            val ana = lobby.reader("ana", Country.HR, ContentLanguages.CROATIAN).join()
            lobby.reader("boris", Country.BA, ContentLanguages.BOSNIAN).join()
            // An old client: in Serbia, reading Serbian Cyrillic.
            lobby.player("ceca").join()
            // A seat held with no socket yet: nobody the room has seen.
            lobby.reader("dragan", Country.SI, ContentLanguages.SLOVENIAN).reserve()

            ana.start()
            val asked = lobby.asked.single()
            assertEquals(setOf(Country.HR, Country.BA, Country.RS), asked.countries)
            assertEquals(
                setOf(ContentLanguages.CROATIAN, ContentLanguages.BOSNIAN, ContentLanguages.SERBIAN),
                asked.languages,
            )
            assertEquals(setOf("ana", "boris", "ceca"), asked.players)
        }

    @Test
    fun `a seat taken again says the country it is taken from, and a member away keeps their language`() =
        runTest {
            val lobby = LobbyScenario(this, LobbySettingsDto(questionCount = 1), questions = translatedQuestions(1))
            val boris = lobby.player("boris").join()
            val ana = lobby.reader("ana", language = ContentLanguages.CROATIAN).join()

            // A rejoin from Croatia: the held seat plays from there now.
            boris.country = Country.HR
            assertEquals(ReserveResult.Reserved, boris.reserve())
            // Away when the game starts: what Ana reads is what her last socket said.
            ana.drop()

            boris.start()
            val asked = lobby.asked.single()
            assertEquals(setOf(Country.RS, Country.HR), asked.countries, "Ana's seat is still in Serbia")
            assertEquals(setOf(ContentLanguages.CROATIAN, ContentLanguages.SERBIAN), asked.languages)
        }

    @Test
    fun `a solo run is asked for its player's own country and language`() =
        runTest {
            val lobby =
                LobbyScenario(this, LobbySettingsDto.SOLO, kind = LobbyKind.SOLO, questions = translatedQuestions(10))
            val ana = lobby.reader("ana", Country.MK, ContentLanguages.MACEDONIAN).join()
            ana.start()

            val asked = lobby.asked.single()
            assertEquals(setOf(Country.MK), asked.countries)
            assertEquals(setOf(ContentLanguages.MACEDONIAN), asked.languages)
            assertEquals("ana", asked.soloPlayer)
        }
}
