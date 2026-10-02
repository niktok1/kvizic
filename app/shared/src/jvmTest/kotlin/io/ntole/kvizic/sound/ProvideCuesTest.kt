package io.ntole.kvizic.sound

import androidx.compose.ui.ImageComposeScene
import io.ntole.kvizic.analytics.RecordingAnalytics
import io.ntole.kvizic.core.network.InMemoryTokenStorage
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.Cues
import io.ntole.kvizic.design.sound.LocalCues
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.settle
import io.ntole.kvizic.theme.GameTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The player's switch is the game's sound: turned off it silences every cue at once, and on again it plays. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProvideCuesTest {
    @BeforeTest
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main, which has no implementation under test.
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `turning sound off silences every cue at once and on again plays them`() {
        val device = RecordingSoundDevice()
        val sounds = SoundViewModel(InMemoryTokenStorage(), RecordingAnalytics())
        var cues: Cues = Cues.None
        val scene =
            ImageComposeScene(width = 10, height = 10) {
                GameTheme(Language.DEFAULT) { ProvideCues(sounds, device) { cues = LocalCues.current } }
            }
        try {
            scene.settle()
            // The bank loads in the background, a sample at a time: nothing plays before it has.
            val until = System.nanoTime() + 5_000_000_000
            while (device.loaded.size < Cue.entries.size && System.nanoTime() < until) {
                Thread.sleep(10)
                scene.settle()
            }
            assertEquals(Cue.entries.size, device.loaded.size, "the bank is loaded")

            cues.play(Cue.TAP)
            assertEquals(1, device.played.size, "sound on")

            sounds.setEnabled(false)
            scene.settle()
            Cue.entries.forEach { cues.play(it) }
            assertEquals(1, device.played.size, "sound off")

            sounds.setEnabled(true)
            scene.settle()
            cues.play(Cue.TAP_PRIMARY)
            assertTrue(device.played.size == 2, "sound on again")
        } finally {
            scene.close()
        }
    }
}
