package io.ntole.kvizic.sound

/** A [SoundDevice] that keeps what it is asked, for a test to read: every sample loaded, every sound played. */
internal class RecordingSoundDevice : SoundDevice {
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
