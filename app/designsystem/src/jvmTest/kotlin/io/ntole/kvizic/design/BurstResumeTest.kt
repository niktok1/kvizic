package io.ntole.kvizic.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.design.component.ReactionBurst
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicSkin
import io.ntole.kvizic.design.skin.Skins
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A reaction's burst composed anew, as after an Android activity is made again, goes on from when the
 * reaction arrived: one long over shows nothing, where it used to burst again, and one just heard bursts.
 */
class BurstResumeTest {
    @Test
    fun `a burst that is over stays gone when composed anew`() {
        Skins.ALL.forEach { skin ->
            val shown = litPixelsOverTime(skin) { TimeSource.Monotonic.markNow() - 10.seconds }
            assertTrue(shown.all { it == 0 }, "${skin.id}: an old reaction burst again: $shown")
        }
    }

    @Test
    fun `a burst just heard plays`() {
        Skins.ALL.forEach { skin ->
            val shown = litPixelsOverTime(skin) { TimeSource.Monotonic.markNow() }
            assertTrue(shown.any { it > 0 }, "${skin.id}: a new reaction never showed: $shown")
        }
    }

    /**
     * How many pixels the burst draws at a few moments of its first half second, on a transparent scene, the
     * reaction heard at [startedAt]'s mark. The scene is drawn once before then: the burst reads real time,
     * and a test JVM's first scene, on a busy machine, can take longer than a whole burst to draw.
     */
    private fun litPixelsOverTime(
        skin: io.ntole.kvizic.design.skin.Skin,
        startedAt: () -> TimeMark,
    ): List<Int> {
        var heard by mutableStateOf<TimeMark?>(null)
        val scene =
            ImageComposeScene(width = SIZE, height = SIZE, density = Density(1f)) {
                KvizicSkin(skin) {
                    heard?.let { ReactionBurst(KvizicIcons.ThumbUp, burstKey = 1, startedAt = it) }
                }
            }
        try {
            scene.renderAt(0)
            heard = startedAt()
            return MOMENTS.map { millis ->
                val time = millis * MILLI
                scene.renderAt(time)
                pixelsOf(scene.render(time)).count { it ushr ALPHA_SHIFT != 0 }
            }
        } finally {
            scene.close()
        }
    }

    private companion object {
        const val SIZE = 160
        const val ALPHA_SHIFT = 24
        val MOMENTS = listOf(0L, 50L, 150L, 300L, 500L)
    }
}
