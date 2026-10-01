package io.ntole.kvizic.room

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.core.domain.topic.TopicGroup
import io.ntole.kvizic.design.component.Stage
import io.ntole.kvizic.design.skin.Skins
import io.ntole.kvizic.everyText
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.nodes
import io.ntole.kvizic.renderSettled
import io.ntole.kvizic.tap
import io.ntole.kvizic.texts
import io.ntole.kvizic.theme.GameTheme
import io.ntole.kvizic.type
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The topics' picker in every skin: its groups with their topics, a group's chip picking all of it or none,
 * Све теме picking none, and the search finding a topic typed in Latin with no accents.
 */
class TopicPickerDrawTest {
    private val words = stringsOf(Language.DEFAULT).game

    @Test
    fun `the groups list their topics and a group's chip picks all of it or none`() {
        Skins.ALL.forEach { skin ->
            var picked by mutableStateOf(listOf<String>())
            val scene = scene(skin, { picked }) { picked = it }
            try {
                write("topics-${skin.id}", scene.renderSettled())
                val shown = scene.everyText()
                listOf("Знање", "Забава", "Географија", "Музика", words.allTopics).forEach {
                    assertTrue(it in shown, "${skin.id}: \"$it\" is not shown in $shown")
                }
                scene.nodes().first { words.wholeGroup in it.texts }.let { scene.tapNode(it) }
                assertEquals(listOf("GEOGRAPHY", "HISTORY", "SCIENCE"), picked, skin.id)
                scene.nodes().first { words.wholeGroup in it.texts }.let { scene.tapNode(it) }
                assertEquals(emptyList(), picked, skin.id)
                scene.tap("Музика")
                assertEquals(listOf("MUSIC"), picked, skin.id)
                scene.tap(words.allTopics)
                assertEquals(emptyList(), picked, skin.id)
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun `the search finds a topic typed in Latin with no accents`() {
        Skins.ALL.forEach { skin ->
            val scene = scene(skin, { emptyList() }) {}
            try {
                scene.renderSettled()
                scene.type(0, "knjizev")
                val shown = scene.everyText()
                assertTrue("Језик и књижевност" in shown, "${skin.id}: $shown")
                assertFalse("Географија" in shown, "${skin.id}: a topic the search does not find is shown")
                scene.type(0, "zzz")
                assertTrue(words.noTopicFound in scene.everyText(), skin.id)
            } finally {
                scene.close()
            }
        }
    }

    private fun scene(
        skin: io.ntole.kvizic.design.skin.Skin,
        picked: () -> List<String>,
        onChange: (List<String>) -> Unit,
    ): ImageComposeScene =
        ImageComposeScene(width = 360, height = 720, density = Density(1f)) {
            GameTheme(Language.DEFAULT, skin) {
                Stage(Modifier.fillMaxSize()) {
                    TopicPicker(picked(), PICKED_FROM, GROUPS, questionCount = 10, onChange = onChange, onDone = {})
                }
            }
        }

    private fun ImageComposeScene.tapNode(node: androidx.compose.ui.semantics.SemanticsNode) {
        node.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action?.invoke()
        renderSettled()
    }

    private fun write(
        name: String,
        image: Image,
    ) {
        val directory = System.getenv("KVIZIC_DESIGN_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        directory.mkdirs()
        File(directory, "$name.png").writeBytes(checkNotNull(image.encodeToData()).bytes)
    }

    private companion object {
        val GROUPS =
            listOf(
                TopicGroup("KNOWLEDGE", "Знање", "Knowledge"),
                TopicGroup("ENTERTAINMENT", "Забава", "Entertainment"),
            )
        val PICKED_FROM =
            listOf(
                Topic("GEOGRAPHY", "Географија", "Geography", 40, groupId = "KNOWLEDGE"),
                Topic("HISTORY", "Историја", "History", 30, groupId = "KNOWLEDGE"),
                Topic("SCIENCE", "Наука и технологија", "Science", 4, groupId = "KNOWLEDGE"),
                Topic("MUSIC", "Музика", "Music", 25, groupId = "ENTERTAINMENT"),
                Topic("LANGUAGE", "Језик и књижевност", "Language", 12),
            )
    }
}
