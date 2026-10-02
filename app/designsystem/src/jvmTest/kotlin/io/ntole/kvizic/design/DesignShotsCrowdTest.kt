package io.ntole.kvizic.design

import io.ntole.kvizic.design.skin.Skins
import io.ntole.kvizic.design.skins.notebook.NotebookSkin
import kotlin.test.Test

/** The longest texts and a full room for the owner to judge, answering and revealed (`DesignShots.kt`). */
class DesignShotsCrowdTest {
    @Test
    fun `the longest texts and a full room draw for the owner to judge`() = drawShots(SHOTS)

    private companion object {
        val buzzers = Skins.Buzzers

        val SHOTS =
            listOf(
                Shot("buzzers-10-long-reading", buzzers) { ReadingMock(LONG_QUESTION) },
                Shot("buzzers-11-long-answering", buzzers) { AnsweringMock(LONG_QUESTION, LONG_ANSWERS) },
                Shot("notebook-11-long-answering", NotebookSkin) { AnsweringMock(LONG_QUESTION, LONG_ANSWERS) },
                // A full room, seven of eight on one answer.
                Shot("buzzers-12-crowd-grid", buzzers) { CrowdMock() },
                Shot("buzzers-13-crowd-column", buzzers) { CrowdMock(LONG_QUESTION, LONG_ANSWERS) },
                Shot("buzzers-14-crowd-revealed", buzzers) { CrowdMock(revealed = true) },
                Shot("buzzers-15-crowd-column-revealed", buzzers) {
                    CrowdMock(LONG_QUESTION, LONG_ANSWERS, revealed = true)
                },
            )
    }
}
