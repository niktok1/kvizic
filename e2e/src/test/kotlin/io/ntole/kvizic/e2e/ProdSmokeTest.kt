package io.ntole.kvizic.e2e

import io.ktor.client.engine.okhttp.OkHttp
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.domain.lobby.LobbyVisibility
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * The check after a deploy, against the server `kvizic.smoke.baseUrl` names (`./gradlew :e2e:smokeTest`,
 * never part of `test`): two guests, one over Android's engine, one over the desktop's, sit in a private
 * room of the first's, the second by its code, the game starts and both are asked the same first question
 * with the same answers over their sockets. Then both leave before answering, so no answer of a check ever
 * counts towards how hard a real question plays; the two guests are cleaned like any other.
 */
class ProdSmokeTest {
    @Test
    fun `two guests sit in a room and are asked the same first question`(): Unit =
        runBlocking {
            val baseUrl =
                checkNotNull(System.getProperty("kvizic.smoke.baseUrl")?.takeIf { it.isNotBlank() }) {
                    "kvizic.smoke.baseUrl names no server"
                }
            E2ePlayer("host", baseUrl, socketEngine = { OkHttp.create() }).use { host ->
                E2ePlayer("guest", baseUrl).use { guest ->
                    host.lobby.create(
                        LobbySettings(questionCount = 5, secondsPerQuestion = 10, visibility = LobbyVisibility.PRIVATE),
                    )
                    val code =
                        (
                            host.awaitState(
                                SLOW,
                                "the new room",
                            ) { it is LobbySessionState.InLobby } as LobbySessionState.InLobby
                        ).lobby.code
                    guest.lobby.join(code)
                    host.awaitState(SLOW, "the guest in") { state ->
                        state is LobbySessionState.InLobby && state.lobby.members.size == 2 &&
                            state.lobby.members.all { it.connected }
                    }

                    host.lobby.start()
                    val asked =
                        listOf(host, guest).map { player ->
                            player.awaitPhase(SLOW, "the first question's answers") {
                                it is GamePhase.Answering && it.question.index == 0
                            } as GamePhase.Answering
                        }
                    assertEquals(asked[0].question, asked[1].question, "the same question for both")
                    assertEquals(asked[0].options, asked[1].options, "the same answers, in the same order")

                    // A leave, not a dropped socket, which the server would wait out with the game going on.
                    guest.lobby.leave()
                    host.awaitState(SLOW, "the guest gone") { state ->
                        state is LobbySessionState.InLobby && state.lobby.members.size == 1
                    }
                    host.lobby.leave()
                    delay(LEAVE_SAID)
                    println("prod check: room $code asked question 1 of ${asked[0].question.count} of both, both left")
                }
            }
        }

    private companion object {
        /** A countdown, a question's longest read and a cold network with room to spare. */
        val SLOW = 30.seconds

        /** Longer than the session waits for the server to close a socket it said its leave on. */
        val LEAVE_SAID = 4.seconds
    }
}
