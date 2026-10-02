package io.ntole.kvizic.sound

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import js.buffer.toArrayBuffer
import kotlinx.coroutines.CancellationException
import web.audio.AudioBuffer
import web.audio.AudioBufferSourceNode
import web.audio.AudioContext
import web.audio.AudioContextState
import web.audio.decodeAudioData
import web.audio.running

/**
 * A browser's [SoundDevice]: Web Audio, each sample decoded once into a buffer and played from a source of
 * its own through a gain. A browser lets a page make sound only after the player has touched it, so the
 * context is asked to resume at each play, and a cue that comes before it runs is dropped, never queued to
 * burst out at the first tap: the first press of a session is silent, the rest are heard.
 */
@Composable
internal actual fun rememberSoundDevice(): SoundDevice = remember { WebSoundDevice() }

private class WebSoundDevice : SoundDevice {
    private var context: AudioContext? = null
    private val buffers = mutableMapOf<String, AudioBuffer>()

    private fun context(): AudioContext? =
        context ?: try {
            AudioContext().also { context = it }
        } catch (_: Throwable) {
            null
        }

    override suspend fun load(
        key: String,
        wav: ByteArray,
    ) {
        val audio = context() ?: return
        try {
            buffers[key] = audio.decodeAudioData(wav.toArrayBuffer())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // A sample the browser cannot decode is one the player does not hear.
        }
    }

    override fun play(
        key: String,
        volume: Float,
        rate: Float,
    ) {
        val audio = context ?: return
        val buffer = buffers[key] ?: return
        try {
            if (audio.state != AudioContextState.running) {
                // Allowed inside the player's touch: this press makes the next ones heard.
                audio.resumeAsync()
                return
            }
            val source = AudioBufferSourceNode(audio)
            source.buffer = buffer
            source.playbackRate.value = rate
            val gain = audio.createGain()
            gain.gain.value = volume
            source.connect(gain)
            gain.connect(audio.destination)
            source.start()
        } catch (_: Throwable) {
            // Nothing to hear, and nothing to stop the game for.
        }
    }

    override fun release() {
        buffers.clear()
        context?.closeAsync()
        context = null
    }
}
