package io.ntole.kvizic.sound

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.FloatControl
import javax.sound.sampled.LineEvent
import kotlin.math.log10

/**
 * The desktop's [SoundDevice]: `javax.sound`'s clips over samples decoded once. A clip is opened for each
 * play, on a thread of its own so the window never waits for the sound card, and closed as it ends; at most
 * [VOICES] at once. The pitch of a varied cue stays as rendered. A machine with no sound device plays
 * nothing.
 */
@Composable
internal actual fun rememberSoundDevice(): SoundDevice = remember { JvmSoundDevice() }

private class JvmSoundDevice : SoundDevice {
    private class Decoded(
        val format: AudioFormat,
        val pcm: ByteArray,
    )

    private val samples = ConcurrentHashMap<String, Decoded>()
    private val playing = AtomicInteger()
    private val thread =
        Executors.newSingleThreadExecutor { task -> Thread(task, "kvizic-sound").apply { isDaemon = true } }

    override suspend fun load(
        key: String,
        wav: ByteArray,
    ) {
        try {
            withContext(Dispatchers.IO) {
                AudioSystem.getAudioInputStream(ByteArrayInputStream(wav)).use { stream ->
                    samples[key] = Decoded(stream.format, stream.readAllBytes())
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A sample that cannot be decoded is one the player does not hear.
        }
    }

    override fun play(
        key: String,
        volume: Float,
        rate: Float,
    ) {
        val sample = samples[key] ?: return
        if (playing.get() >= VOICES) return
        playing.incrementAndGet()
        try {
            thread.execute {
                try {
                    val clip = AudioSystem.getClip()
                    clip.open(sample.format, sample.pcm, 0, sample.pcm.size)
                    if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                        val gain = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                        gain.value =
                            (20f * log10(volume.coerceAtLeast(MIN_VOLUME))).coerceIn(gain.minimum, gain.maximum)
                    }
                    clip.addLineListener { event ->
                        if (event.type == LineEvent.Type.STOP) {
                            clip.close()
                            playing.decrementAndGet()
                        }
                    }
                    clip.start()
                } catch (_: Exception) {
                    // No sound device, or none free: nothing to hear.
                    playing.decrementAndGet()
                }
            }
        } catch (_: Exception) {
            playing.decrementAndGet()
        }
    }

    override fun release() {
        samples.clear()
        thread.shutdown()
    }

    private companion object {
        const val VOICES = 8
        const val MIN_VOLUME = 0.001f
    }
}
