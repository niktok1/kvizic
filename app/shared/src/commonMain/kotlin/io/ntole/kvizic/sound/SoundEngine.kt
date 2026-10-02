package io.ntole.kvizic.sound

import io.ntole.kvizic.design.skin.SkinSound
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.Cues
import io.ntole.kvizic.design.sound.samples
import kotlin.random.Random

/**
 * The game's sound: every [Cue] a screen asks for, played from the bank of the skin worn ([skin]) through
 * the platform's [SoundDevice], or dropped. It is a [Cues] the app provides, and decides here, once for
 * every platform:
 *
 *  - nothing plays while sound is off ([enabled]) or the app is not in the foreground ([foreground]);
 *  - nothing plays that is not ready: a bank is loaded, a skin's at a time, in the background as the skin
 *    is worn ([load]), so wearing another skin is the one change, with no other part of the game knowing;
 *  - a cue asked again sooner than its [Cue.minGapMillis] is dropped, so a crowd of reactions or a hammered
 *    key is one sound and not a roar;
 *  - a [Cue.varied] cue is played a little off pitch each time, up to the skin's jitter.
 *
 * Used on the main thread alone, as the screens are.
 */
internal class SoundEngine(
    private val device: SoundDevice,
    private val nowMillis: () -> Long,
    private val random: Random = Random.Default,
    /** Where a bank's samples come from: the skin's own, from the compose resources. */
    private val samplesOf: suspend (SkinSound) -> Map<Cue, ByteArray> = { it.samples() },
) : Cues {
    /** Whether the player wants sound: their switch in the settings. */
    var enabled: Boolean = true

    /** Whether the game is shown: the activity started, the window or the page visible. */
    var foreground: Boolean = true

    /** The skin's sound worn now: what [play] plays from. */
    var skin: SkinSound? = null

    private val ready = mutableSetOf<String>()
    private val lastPlayed = mutableMapOf<Cue, Long>()

    /**
     * Loads [sound]'s bank, a sample at a time, each playable as it is loaded; a bank already loaded is not
     * loaded again, and one half loaded by a cancelled call finishes with the next.
     */
    suspend fun load(sound: SkinSound) {
        for ((cue, wav) in samplesOf(sound)) {
            val key = keyOf(sound, cue)
            if (key in ready) continue
            device.load(key, wav)
            ready += key
        }
    }

    override fun play(
        cue: Cue,
        volume: Float,
    ) {
        if (!enabled || !foreground) return
        val sound = skin ?: return
        val key = keyOf(sound, cue)
        if (key !in ready) return
        val now = nowMillis()
        val last = lastPlayed[cue]
        if (last != null && now - last < cue.minGapMillis) return
        lastPlayed[cue] = now
        val loudness = sound.gain(cue) * volume.coerceIn(0f, 1f)
        if (loudness <= 0f) return
        val rate = if (cue.varied && sound.jitter > 0f) 1f + (random.nextFloat() * 2f - 1f) * sound.jitter else 1f
        device.play(key, loudness, rate)
    }

    internal companion object {
        /** What a sample is called to the device: its bank and its cue. */
        fun keyOf(
            sound: SkinSound,
            cue: Cue,
        ): String = "${sound.bank}/${cue.file}"
    }
}
