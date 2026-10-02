package io.ntole.kvizic.design

import io.ntole.kvizic.design.font.Faces
import io.ntole.kvizic.design.skin.Skins
import io.ntole.kvizic.design.skins.notebook.NotebookSkin
import kotlin.test.Test

/**
 * What the owner compares the game in Buzzers with: three screens in Notebook, Home and play in each other
 * display face, and the other tile scheme (`DesignShots.kt`).
 */
class DesignShotsCompareTest {
    @Test
    fun `the other skin, faces and tiles draw for the owner to compare`() = drawShots(SHOTS)

    private companion object {
        val notebook = NotebookSkin
        val oswald = Skins.Buzzers.withDisplay(Faces.Oswald)
        val sofia = Skins.Buzzers.withDisplay(Faces.SofiaExtraCondensed)

        val SHOTS =
            listOf(
                Shot("notebook-01-home", notebook) { HomeMock() },
                Shot("notebook-04-answering", notebook) { AnsweringMock() },
                Shot("notebook-09-results", notebook) { ResultsMock() },
                Shot("face-oswald-01-home", oswald) { HomeMock() },
                Shot("face-oswald-04-answering", oswald) { AnsweringMock() },
                Shot("face-sofia-01-home", sofia) { HomeMock() },
                Shot("face-sofia-04-answering", sofia) { AnsweringMock() },
                // The other tile scheme: each buzzer in its answer's colour.
                Shot("tiles-coloured-04-answering", ColouredBuzzers) { AnsweringMock() },
                Shot("tiles-coloured-05-locked-in", ColouredBuzzers) { LockedInMock() },
                Shot("tiles-coloured-07-true-false", ColouredBuzzers) { TrueFalseMock() },
            )
    }
}
