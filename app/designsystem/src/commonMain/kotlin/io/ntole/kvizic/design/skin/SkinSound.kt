package io.ntole.kvizic.design.skin

import androidx.compose.runtime.Immutable
import io.ntole.kvizic.design.sound.Cue

/**
 * How a skin sounds: the [bank] of samples it plays its [Cue]s from, and how loud. Wearing another skin
 * is playing from another bank, so the whole voice of the game turns with the look, and nothing else
 * has to know.
 */
@Immutable
data class SkinSound(
    /**
     * The folder of the skin's samples, `files/sound/<bank>/<cue>.wav`, one for every [Cue]. Stable, as a
     * skin's id is.
     */
    val bank: String,
    /** The skin's loudness, 0 to 1: a soft skin sits lower, so its samples need no re-rendering. */
    val level: Float,
    /** What a [Cue]'s [Cue.varied] samples are moved off pitch by, at most, as a share (0.04 is 4%). */
    val jitter: Float,
    /** A cue's own trim, 0 to 1, where the mix of this skin needs one; none is whole. */
    val trim: Map<Cue, Float> = emptyMap(),
) {
    init {
        require(level in 0f..1f) { "a level is 0 to 1: $level" }
        require(jitter in 0f..MAX_JITTER) { "a jitter is 0 to $MAX_JITTER: $jitter" }
        require(trim.values.all { it in 0f..1f }) { "a trim is 0 to 1" }
    }

    /** How loud [cue] plays in this skin, 0 to 1. */
    fun gain(cue: Cue): Float = level * (trim[cue] ?: 1f)

    private companion object {
        const val MAX_JITTER = 0.2f
    }
}
