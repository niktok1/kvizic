package io.ntole.kvizic.room

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.CountedBy
import io.ntole.kvizic.Recompositions
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.descriptions
import io.ntole.kvizic.design.component.Stage
import io.ntole.kvizic.design.skin.Skins
import io.ntole.kvizic.everyText
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.renderAt
import io.ntole.kvizic.theme.GameTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A game's steps give way to each other rather than cut: the question read rises into its answers, which
 * come up one by one, and the answers fade into their reveal. Mid-change both steps are drawn, after it
 * only the new one, and a frame of the change composes nothing.
 */
class RoomStagesTest {
    private val words = stringsOf(Language.DEFAULT).game

    @Test
    fun `the question read rises into its answers`() {
        Skins.ALL.forEach { skin ->
            stages(skin, inLobby(reading())) { scene, change ->
                assertTrue(words.answersComing in scene.everyText(), "${skin.id}: the read is not shown")
                change(inLobby(answering()), MIDWAY) { mid ->
                    val shown = scene.everyText()
                    assertTrue(words.answersComing in shown, "${skin.id}: the read went at once: $shown")
                    assertTrue(OPTIONS[0] in shown, "${skin.id}: the answers did not come with it: $shown")
                    mid()
                }
                val shown = scene.everyText()
                assertFalse(words.answersComing in shown, "${skin.id}: the read stayed: $shown")
                OPTIONS.forEach { assertTrue(it in shown, "${skin.id}: $it is not shown") }
            }
        }
    }

    /**
     * The reveal's count to the next question ticks on the device's clock, not the scene's: a slow machine
     * took a second over these frames, and the tick composed among them. Its count has run out here.
     */
    @Test
    fun `the answers fade into their reveal`() {
        Skins.ALL.forEach { skin ->
            stages(skin, inLobby(answering(myPick = 1))) { scene, change ->
                change(inLobby(revealing().copy(next = deadline(Duration.ZERO, 5.seconds))), MIDWAY) { mid -> mid() }
                assertTrue(words.reportQuestion in scene.descriptions(), "${skin.id}: the reveal is not shown")
            }
        }
    }

    /**
     * The room drawn at [first], and [steps] given a way to change its state and step the scene through the
     * change: frames between the change's first two and its last compose nothing.
     */
    private fun stages(
        skin: io.ntole.kvizic.design.skin.Skin,
        first: LobbySessionState.InLobby,
        steps: (
            scene: ImageComposeScene,
            change: (LobbySessionState.InLobby, Long, (() -> Unit) -> Unit) -> Unit,
        ) -> Unit,
    ) {
        var state by mutableStateOf(first)
        val recompositions = Recompositions()
        val scene =
            ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
                CountedBy(recompositions) {
                    GameTheme(Language.DEFAULT, skin) {
                        Stage(Modifier.fillMaxSize()) {
                            RoomScreen(state, TOPICS, note = null, bursts = emptyMap(), actions = RoomActions())
                        }
                    }
                }
            }
        var time = 0L

        fun until(nanos: Long) {
            while (time < nanos) {
                time += FRAME
                scene.renderAt(time)
            }
        }
        try {
            until(SETTLE)
            steps(scene) { next, midway, atMidway ->
                state = next
                val start = time
                // The frames that compose the change and start its animations.
                until(start + FRAME * 3)
                val before = recompositions.scopesEntered
                until(start + midway)
                atMidway {
                    assertEquals(before, recompositions.scopesEntered, "${skin.id}: a frame of the change composed")
                }
                until(start + SETTLE)
            }
        } finally {
            scene.close()
        }
    }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 900
        const val FRAME = 1_000_000_000L / 60
        const val SETTLE = 2_000_000_000L

        /** Into the change, short of the skins' stage time. */
        const val MIDWAY = 120_000_000L
    }
}
