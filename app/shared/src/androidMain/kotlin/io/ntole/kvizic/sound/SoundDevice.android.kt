package io.ntole.kvizic.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Android's [SoundDevice]: a `SoundPool`, which keeps every sample decoded and plays one in a few
 * milliseconds. It plays as a game's sound (`USAGE_GAME`) and asks for no audio focus, so the player's music
 * goes on under it; and it plays nothing while the phone is set to silent or vibrate, as the system's own
 * touch sounds do not.
 */
@Composable
internal actual fun rememberSoundDevice(): SoundDevice {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidSoundDevice(context) }
}

private class AndroidSoundDevice(
    private val context: Context,
) : SoundDevice {
    private val pool: SoundPool =
        SoundPool
            .Builder()
            .setMaxStreams(VOICES)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            ).build()
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val lock = Any()
    private val sounds = mutableMapOf<String, Int>()
    private val loaded = mutableSetOf<Int>()
    private val waiting = mutableMapOf<Int, CancellableContinuation<Unit>>()

    init {
        // Called on a thread of the pool's own: a sample is playable once it says so.
        pool.setOnLoadCompleteListener { _, soundId, status ->
            synchronized(lock) {
                if (status == 0) loaded += soundId
                waiting.remove(soundId)?.resume(Unit)
            }
        }
    }

    override suspend fun load(
        key: String,
        wav: ByteArray,
    ) {
        try {
            val file = withContext(Dispatchers.IO) { cached(key, wav) }
            val id = pool.load(file.path, 1)
            if (id == 0) return
            suspendCancellableCoroutine { continuation ->
                synchronized(lock) {
                    if (id in loaded) {
                        continuation.resume(Unit)
                    } else {
                        waiting[id] = continuation
                        continuation.invokeOnCancellation { synchronized(lock) { waiting.remove(id) } }
                    }
                }
            }
            synchronized(lock) { if (id in loaded) sounds[key] = id }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A sample that cannot be written or decoded is one the player does not hear.
        }
    }

    override fun play(
        key: String,
        volume: Float,
        rate: Float,
    ) {
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = synchronized(lock) { sounds[key] } ?: return
        val level = volume.coerceIn(0f, 1f)
        pool.play(id, level, level, 1, 0, rate.coerceIn(MIN_RATE, MAX_RATE))
    }

    override fun release() {
        synchronized(lock) {
            sounds.clear()
            loaded.clear()
            waiting.values.forEach { if (it.isActive) it.resume(Unit) }
            waiting.clear()
        }
        pool.release()
    }

    /** The sample as a file in the app's cache, which `SoundPool` loads from; one already there is as it was. */
    private fun cached(
        key: String,
        wav: ByteArray,
    ): File {
        val folder = File(context.cacheDir, "sound").apply { mkdirs() }
        val file = File(folder, key.replace('/', '_') + ".wav")
        if (file.length() != wav.size.toLong()) file.writeBytes(wav)
        return file
    }

    private companion object {
        /** At most this many sounds at once; one more takes the oldest's place. */
        const val VOICES = 6
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2f
    }
}
