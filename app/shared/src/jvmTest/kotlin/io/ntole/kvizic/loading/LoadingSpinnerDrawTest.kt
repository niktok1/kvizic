package io.ntole.kvizic.loading

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.CountedBy
import io.ntole.kvizic.Recompositions
import io.ntole.kvizic.everyText
import io.ntole.kvizic.language.KvizicStrings
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.renderAt
import io.ntole.kvizic.renderSettled
import io.ntole.kvizic.theme.ShellTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A slow first load: a free Render service sleeps and its first request takes up to a minute, so a spinner
 * that has turned for 5 seconds says one short line under it, and nothing else. The scene's frame clock is
 * stepped a frame at a time, as a phone draws them, never waited for.
 */
class LoadingSpinnerDrawTest {
    @Test
    fun `a spinner says a moment more once it has turned for 5 seconds and not before`() {
        Language.entries.forEach { language ->
            val still = stringsOf(language).stillLoading
            val scene = scene(language)
            try {
                stepTo(scene, SLOW_AFTER - 100.milliseconds)
                assertFalse(still in scene.everyText(), "$language: said too soon")

                scene.renderSettled()
                assertEquals(1, scene.everyText().count { it == still }, "$language: ${scene.everyText()}")
            } finally {
                scene.close()
            }
        }
    }

    /**
     * Its frames are drawn and never composed, a phone's accessibility service hearing nothing of them: the
     * one composition before the line shows is the line's own.
     */
    @Test
    fun `a spinner's frames compose nothing until its line shows`() {
        val recompositions = Recompositions()
        val scene =
            ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
                CountedBy(recompositions) {
                    ShellTheme { KvizicStrings(Language.DEFAULT) { LoadingSpinner(name = "spinner") } }
                }
            }
        try {
            scene.renderAt(0)
            val before = recompositions.scopesEntered

            stepTo(scene, SLOW_AFTER - 100.milliseconds, from = FRAME)

            assertEquals(before, recompositions.scopesEntered, "a frame of the spinner composed something")
            recompositions.assertCounting(scene, (SLOW_AFTER - 100.milliseconds).inWholeNanoseconds)
        } finally {
            scene.close()
        }
    }

    @Test
    fun `the line waits 5 seconds`() {
        assertEquals(5.seconds, SLOW_AFTER)
        assertEquals("Још мало…", stringsOf(Language.SERBIAN_CYRILLIC).stillLoading)
    }

    private fun scene(language: Language): ImageComposeScene =
        ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
            ShellTheme { KvizicStrings(language) { LoadingSpinner() } }
        }

    /** Draws a frame every [FRAME] from [from] to [to], as a phone does. */
    private fun stepTo(
        scene: ImageComposeScene,
        to: Duration,
        from: Duration = Duration.ZERO,
    ) {
        var at = from
        while (at <= to) {
            scene.renderAt(at.inWholeNanoseconds)
            at += FRAME
        }
    }

    private companion object {
        const val WIDTH = 375
        const val HEIGHT = 599
        val FRAME = 16.milliseconds
    }
}
