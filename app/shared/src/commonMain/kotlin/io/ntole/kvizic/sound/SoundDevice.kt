package io.ntole.kvizic.sound

import androidx.compose.runtime.Composable

/**
 * One platform's way to play a short sample, and nothing else: the policy (what plays, how loud, how often)
 * is [SoundEngine]'s, so it is the same everywhere and tested once. A device never throws: a sound that
 * cannot play is a sound the player does not hear, which is no reason to stop a game.
 */
internal interface SoundDevice {
    /**
     * Prepares [wav] (16-bit mono PCM) as [key], and returns once it can be played, or when it never will:
     * a sample that cannot be decoded is left out, and [play] of its key does nothing.
     */
    suspend fun load(
        key: String,
        wav: ByteArray,
    )

    /** Plays [key] at [volume] (0 to 1) and [rate] (1 is as rendered, 1.05 a little higher), over what plays. */
    fun play(
        key: String,
        volume: Float,
        rate: Float,
    )

    /** Lets go of every sample and voice. */
    fun release()
}

/** This platform's [SoundDevice], released when it leaves the composition. */
@Composable
internal expect fun rememberSoundDevice(): SoundDevice

/** Plays nothing: for a platform with no way to, and for a test of what the engine asks of a device. */
internal object SilentSoundDevice : SoundDevice {
    override suspend fun load(
        key: String,
        wav: ByteArray,
    ) = Unit

    override fun play(
        key: String,
        volume: Float,
        rate: Float,
    ) = Unit

    override fun release() = Unit
}
