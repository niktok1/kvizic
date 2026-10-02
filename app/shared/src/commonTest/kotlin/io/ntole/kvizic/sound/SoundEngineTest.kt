package io.ntole.kvizic.sound

import io.ntole.kvizic.design.skin.SkinSound
import io.ntole.kvizic.design.sound.Cue
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the game's sound plays, and when: the policy over any platform's device. */
class SoundEngineTest {
    private val device = RecordingDevice()
    private var now = 0L
    private val buzzers = SkinSound(bank = "buzzers", level = 0.8f, jitter = 0.05f, trim = mapOf(Cue.TICK to 0.5f))
    private val notebook = SkinSound(bank = "notebook", level = 0.5f, jitter = 0f)

    private fun engine(): SoundEngine =
        SoundEngine(device, nowMillis = {
            now
        }, random = Random(1), samplesOf = { Cue.entries.associateWith { byteArrayOf() } })

    private suspend fun ready(
        engine: SoundEngine,
        sound: SkinSound = buzzers,
    ): SoundEngine =
        engine.apply {
            load(sound)
            skin = sound
        }

    @Test
    fun `a cue plays from the bank of the skin worn`() =
        runTest {
            val engine = ready(engine())

            engine.play(Cue.TAP)

            assertEquals(listOf("buzzers/tap"), device.played.map { it.key })
        }

    @Test
    fun `wearing another skin plays from its bank with nothing else changed`() =
        runTest {
            val engine = ready(engine())
            engine.load(notebook)

            engine.play(Cue.TAP)
            engine.skin = notebook
            now += 1_000
            engine.play(Cue.TAP)

            assertEquals(listOf("buzzers/tap", "notebook/tap"), device.played.map { it.key })
        }

    @Test
    fun `a skin whose bank is not loaded yet is silent until it is`() =
        runTest {
            val engine = ready(engine())

            engine.skin = notebook
            engine.play(Cue.TAP)
            assertTrue(device.played.isEmpty())

            engine.load(notebook)
            engine.play(Cue.TAP)
            assertEquals(listOf("notebook/tap"), device.played.map { it.key })
        }

    @Test
    fun `a bank is loaded once`() =
        runTest {
            val engine = ready(engine())
            val loadedOnce = device.loaded.size

            engine.load(buzzers)

            assertEquals(Cue.entries.size, loadedOnce)
            assertEquals(loadedOnce, device.loaded.size)
        }

    @Test
    fun `nothing plays with sound off or the app away`() =
        runTest {
            val engine = ready(engine())

            engine.enabled = false
            engine.play(Cue.TAP)
            engine.enabled = true
            engine.foreground = false
            engine.play(Cue.TAP)
            assertTrue(device.played.isEmpty())

            engine.foreground = true
            engine.play(Cue.TAP)
            assertEquals(1, device.played.size)
        }

    @Test
    fun `the same cue asked again too soon is dropped`() =
        runTest {
            val engine = ready(engine())

            engine.play(Cue.REACT_CLAP)
            now += Cue.REACT_CLAP.minGapMillis - 1L
            engine.play(Cue.REACT_CLAP)
            now += 1
            engine.play(Cue.REACT_CLAP)

            assertEquals(2, device.played.size)
        }

    @Test
    fun `another cue is not held up by one just played`() =
        runTest {
            val engine = ready(engine())

            engine.play(Cue.TAP)
            engine.play(Cue.TICK)

            assertEquals(2, device.played.size)
        }

    @Test
    fun `a cue plays at the skin's level and its own trim`() =
        runTest {
            val engine = ready(engine())

            engine.play(Cue.TAP)
            engine.play(Cue.TICK, volume = 0.5f)

            assertEquals(0.8f, device.played[0].volume, 1e-6f)
            assertEquals(0.8f * 0.5f * 0.5f, device.played[1].volume, 1e-6f)
        }

    @Test
    fun `only a varied cue is moved off pitch by no more than the skin's jitter`() =
        runTest {
            val engine = ready(engine())
            assertTrue(Cue.TAP_SOFT.varied && !Cue.TAP.varied)

            repeat(40) {
                now += 1_000
                engine.play(Cue.TAP_SOFT)
                engine.play(Cue.TAP)
            }

            val soft = device.played.filter { it.key.endsWith("/tap_soft") }.map { it.rate }
            assertTrue(soft.all { it in 0.95f..1.05f }, "within the jitter: $soft")
            assertTrue(soft.toSet().size > 1, "moved about: $soft")
            assertTrue(device.played.filter { it.key.endsWith("/tap") }.all { it.rate == 1f })
        }

    @Test
    fun `a skin with no jitter plays every cue as rendered`() =
        runTest {
            val engine = ready(engine(), notebook)

            repeat(10) {
                now += 1_000
                engine.play(Cue.TAP_SOFT)
            }

            assertTrue(device.played.all { it.rate == 1f })
        }

    @Test
    fun `a cue silenced by a zero volume is not played`() =
        runTest {
            val engine = ready(engine())

            engine.play(Cue.TAP, volume = 0f)

            assertTrue(device.played.isEmpty())
        }

    /** Every cue's own limit is sane: none holds itself off for long. */
    @Test
    fun `no cue holds itself off for more than a couple of seconds`() {
        assertTrue(Cue.entries.all { it.minGapMillis in 0..2_000 })
    }

    private class RecordingDevice : SoundDevice {
        class Played(
            val key: String,
            val volume: Float,
            val rate: Float,
        )

        val loaded = mutableListOf<String>()
        val played = mutableListOf<Played>()

        override suspend fun load(
            key: String,
            wav: ByteArray,
        ) {
            loaded += key
        }

        override fun play(
            key: String,
            volume: Float,
            rate: Float,
        ) {
            played += Played(key, volume, rate)
        }

        override fun release() = Unit
    }
}
