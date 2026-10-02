package io.ntole.kvizic.design

import io.ntole.kvizic.design.skin.Skins
import kotlin.test.Test

/** The game in Buzzers for the owner to judge, screen by screen, from Home to the results (`DesignShots.kt`). */
class DesignShotsTest {
    @Test
    fun `the screens draw for the owner to judge`() = drawShots(SHOTS)

    private companion object {
        val buzzers = Skins.Buzzers

        val SHOTS =
            listOf(
                Shot("buzzers-01-home", buzzers) { HomeMock() },
                // Mid-way through a reaction's burst over a seat.
                Shot("buzzers-02-lobby", buzzers, atMillis = 420) { LobbyMock() },
                // The lights three quarters up as the question is read.
                Shot("buzzers-03-reading", buzzers, atMillis = 300) { ReadingMock() },
                Shot("buzzers-04-answering", buzzers) { AnsweringMock() },
                Shot("buzzers-05-locked-in", buzzers) { LockedInMock() },
                Shot("buzzers-06-reveal", buzzers) { RevealMock() },
                Shot("buzzers-07-true-false", buzzers) { TrueFalseMock() },
                Shot("buzzers-08-three-answers", buzzers) { ThreeAnswersMock() },
                Shot("buzzers-09-results", buzzers) { ResultsMock() },
            )
    }
}
