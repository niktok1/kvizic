package io.ntole.kvizic.sound

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * iOS's [SoundDevice]: `AVAudioPlayer`s over each sample's data, a few for each so one sound can overlap
 * itself. The session is the ambient one: it mixes with the player's music, which goes on under the game's
 * sounds, and it is silenced by the ring/silent switch, as a game's sounds should be.
 */
@Composable
internal actual fun rememberSoundDevice(): SoundDevice = remember { IosSoundDevice() }

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosSoundDevice : SoundDevice {
    private class Voices(
        val data: NSData,
        val players: MutableList<AVAudioPlayer> = mutableListOf(),
        var next: Int = 0,
    )

    private val samples = mutableMapOf<String, Voices>()

    init {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryAmbient, error = null)
        session.setActive(true, error = null)
    }

    override suspend fun load(
        key: String,
        wav: ByteArray,
    ) {
        val data = wav.usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = wav.size.toULong()) }
        val voices = Voices(data)
        // The first player is made now and primed, so the first play is as quick as the rest.
        playerFor(voices)?.prepareToPlay()
        if (voices.players.isNotEmpty()) samples[key] = voices
    }

    override fun play(
        key: String,
        volume: Float,
        rate: Float,
    ) {
        val voices = samples[key] ?: return
        val player =
            voices.players.firstOrNull { !it.playing }
                ?: (if (voices.players.size < VOICES_PER_SAMPLE) playerFor(voices) else null)
                ?: voices.players[voices.next++ % voices.players.size].also { it.currentTime = 0.0 }
        player.volume = volume
        player.rate = rate
        player.play()
    }

    override fun release() {
        samples.values.forEach { voices -> voices.players.forEach { it.stop() } }
        samples.clear()
    }

    /** A new player over [voices]' data, kept with the others, or none when the data cannot be played. */
    private fun playerFor(voices: Voices): AVAudioPlayer? {
        val player =
            try {
                AVAudioPlayer(data = voices.data, error = null)
            } catch (_: Throwable) {
                // Data the system cannot play: no player is made from it.
                return null
            }
        player.enableRate = true
        voices.players += player
        return player
    }

    private companion object {
        const val VOICES_PER_SAMPLE = 3
    }
}
