package io.ntole.kvizic.design.sound

import io.ntole.kvizic.design.resources.Res
import io.ntole.kvizic.design.skin.SkinSound
import kotlinx.coroutines.CancellationException

/**
 * Every cue's sample in this skin's bank, as WAV bytes (16-bit mono PCM, the one format every platform
 * decodes), keyed by cue. A cue whose file cannot be read is left out, and the game plays on without it:
 * `SoundBankTest` holds every bank whole.
 */
suspend fun SkinSound.samples(): Map<Cue, ByteArray> =
    buildMap {
        for (cue in Cue.entries) {
            val bytes =
                try {
                    Res.readBytes(path(cue))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    continue
                }
            put(cue, bytes)
        }
    }

/** Where the sample of [cue] is among the compose resources. */
internal fun SkinSound.path(cue: Cue): String = "files/sound/$bank/${cue.file}.wav"
