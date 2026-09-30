package io.ntole.kvizic.design

import androidx.compose.runtime.Composable
import io.ntole.kvizic.design.font.Faces
import io.ntole.kvizic.design.skin.Skin
import io.ntole.kvizic.design.skin.Skins
import io.ntole.kvizic.design.skins.notebook.NotebookSkin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The game's screens as the owner approves them before any real screen is built: drawn off screen at an
 * iPhone SE's 375 by 667 at twice a pixel a dp, in Buzzers, three of them in Notebook, and Home and play
 * in each display face to compare. Each is drawn in every run; with `KVIZIC_DESIGN_DIR` set each is also
 * written there as a PNG:
 * `KVIZIC_DESIGN_DIR=../design-review ./gradlew :app:designsystem:jvmTest --tests '*DesignShotsTest*' --rerun`.
 */
class DesignShotsTest {
    @Test
    fun `the screens draw for the owner to judge`() {
        SHOTS.forEach { shot ->
            val scene = stageScene(shot.skin, WIDTH, HEIGHT, DENSITY) { shot.content() }
            try {
                val image = scene.renderUpTo(shot.atMillis * MILLI)
                assertEquals(WIDTH, image.width, shot.name)
                // Something is drawn over the page: more than one colour shows.
                val colours = pixelsOf(image).toSet().size
                assertTrue(colours > MANY, "${shot.name} draws $colours colours")
                writeDesign(shot.name, image)
            } finally {
                scene.close()
            }
        }
    }

    private class Shot(
        val name: String,
        val skin: Skin,
        val atMillis: Long = SETTLED,
        val content: @Composable () -> Unit,
    )

    private companion object {
        const val DENSITY = 2f
        const val WIDTH = 750
        const val HEIGHT = 1334
        const val SETTLED = 1_000L
        const val MANY = 50

        val buzzers = Skins.Buzzers
        val notebook = NotebookSkin
        val oswald = Skins.Buzzers.withDisplay(Faces.Oswald)
        val sofia = Skins.Buzzers.withDisplay(Faces.SofiaExtraCondensed)

        val SHOTS =
            listOf(
                Shot("buzzers-01-home", buzzers) { HomeMock() },
                Shot("buzzers-10-long-reading", buzzers) { ReadingMock(LONG_QUESTION) },
                Shot("buzzers-11-long-answering", buzzers) { AnsweringMock(LONG_QUESTION, LONG_ANSWERS) },
                Shot("notebook-11-long-answering", notebook) { AnsweringMock(LONG_QUESTION, LONG_ANSWERS) },
                // A full room, seven of eight on one answer.
                Shot("buzzers-12-crowd-grid", buzzers) { CrowdMock() },
                Shot("buzzers-13-crowd-column", buzzers) { CrowdMock(LONG_QUESTION, LONG_ANSWERS) },
                Shot("buzzers-14-crowd-revealed", buzzers) { CrowdMock(revealed = true) },
                Shot("buzzers-15-crowd-column-revealed", buzzers) {
                    CrowdMock(LONG_QUESTION, LONG_ANSWERS, revealed = true)
                },
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
