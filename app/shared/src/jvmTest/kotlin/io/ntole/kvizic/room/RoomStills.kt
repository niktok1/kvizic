package io.ntole.kvizic.room

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.design.component.Stage
import io.ntole.kvizic.design.skin.Skin
import io.ntole.kvizic.design.skin.Skins
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.renderSettled
import io.ntole.kvizic.theme.GameTheme
import org.jetbrains.skia.Image
import java.io.File

/**
 * The room drawn off screen, in a skin, on a small phone; with `KVIZIC_DESIGN_DIR` set, each still is written
 * there as a PNG. One class a stage of the room (the lobby, a question, the reveal and the results): the test
 * JVMs share out classes, never the tests of one, so the stages draw side by side.
 */
abstract class RoomStills {
    protected fun eachSkin(check: (Skin) -> Unit) = Skins.ALL.forEach(check)

    protected fun draw(
        skin: Skin,
        name: String,
        state: LobbySessionState.InLobby,
        actions: RoomActions = RoomActions(),
        topics: List<Topic> = TOPICS,
        width: Int = WIDTH,
        note: RoomNote? = null,
        check: (ImageComposeScene) -> Unit,
    ) {
        val scene =
            ImageComposeScene(width = width, height = HEIGHT, density = Density(1f)) {
                GameTheme(Language.DEFAULT, skin) {
                    Stage(Modifier.fillMaxSize()) {
                        RoomScreen(state, topics, note = note, bursts = emptyMap(), actions = actions)
                    }
                }
            }
        try {
            write("room-${skin.id}-$name", scene.renderSettled())
            check(scene)
        } finally {
            scene.close()
        }
    }

    private fun write(
        name: String,
        image: Image,
    ) {
        val directory = System.getenv("KVIZIC_DESIGN_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        directory.mkdirs()
        File(directory, "$name.png").writeBytes(checkNotNull(image.encodeToData()).bytes)
    }

    protected fun Int.seconds() = kotlin.time.Duration.parse("${this}s")

    protected companion object {
        const val WIDTH = 360
        const val HEIGHT = 640
    }
}
