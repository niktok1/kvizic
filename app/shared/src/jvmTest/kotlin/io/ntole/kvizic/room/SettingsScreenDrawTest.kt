package io.ntole.kvizic.room

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.core.domain.lobby.LobbyDifficulty
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.domain.lobby.LobbyVisibility
import io.ntole.kvizic.cutTexts
import io.ntole.kvizic.descriptions
import io.ntole.kvizic.design.component.Stage
import io.ntole.kvizic.design.skin.Skin
import io.ntole.kvizic.everyText
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.nodes
import io.ntole.kvizic.renderSettled
import io.ntole.kvizic.tap
import io.ntole.kvizic.texts
import io.ntole.kvizic.theme.GameTheme
import io.ntole.kvizic.type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsScreenDrawTest : RoomStills() {
    @Test
    fun `the tiles show every setting whole in every language and at a large font`() {
        eachSkin { skin ->
            Language.entries.forEach { language ->
                listOf(1f, LARGE_FONT).forEach { scale ->
                    val scene =
                        scene(
                            skin,
                            LobbySettings(visibility = LobbyVisibility.PUBLIC),
                            language = language,
                            scale = scale,
                        )
                    try {
                        val image = scene.renderSettled()
                        if (language == Language.DEFAULT) write("settings-${skin.id}-${(scale * 100).toInt()}", image)
                        val strings = stringsOf(language)
                        val words = strings.game
                        val shown = scene.everyText()
                        listOf(
                            words.questions,
                            words.time,
                            words.difficulty,
                            words.players,
                            strings.roomSettings.entry,
                            words.penaltyOn,
                        ).forEach {
                            assertTrue(
                                it in shown,
                                "${skin.id} $language $scale: no tab \"$it\" in $shown",
                            )
                        }
                        assertEquals(emptyList(), scene.cutTexts(), "${skin.id} $language $scale")
                    } finally {
                        scene.close()
                    }
                }
            }
        }
    }

    @Test
    fun `at the font's own size every tile fits a small phone over the done button`() {
        eachSkin { skin ->
            val scene = scene(skin, LobbySettings())
            try {
                scene.renderSettled()
                val done = scene.nodes().first { "OK" in it.texts }.boundsInRoot
                val last = scene.nodes().first { WORDS.penaltyOn in it.texts }.boundsInRoot
                assertTrue(
                    last.bottom <= done.top,
                    "${skin.id}: the last tile at $last is under the done button at $done",
                )
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun `a room with no name shows the host's name as what it goes by`() {
        eachSkin { skin ->
            var settings by mutableStateOf(LobbySettings())
            val scene = scene(skin, { settings }, hostName = "Марко") { settings = it }
            try {
                scene.renderSettled()
                assertTrue("Марко" in scene.everyText(), "${skin.id}: ${scene.everyText()}")
                assertTrue(SAID.unnamed in scene.everyText(), skin.id)
                scene.type(0, "Петак")
                assertFalse(SAID.unnamed in scene.everyText(), "${skin.id}: a named room still goes by its host")
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun `the step keys move a setting one step and stop at its ends`() {
        eachSkin { skin ->
            var settings by mutableStateOf(
                LobbySettings(questionCount = 15, maxPlayers = 5, difficulty = LobbyDifficulty.HARD),
            )
            val scene = scene(skin, { settings }, minPlayers = 5) { settings = it }
            try {
                scene.renderSettled()
                scene.tap("${WORDS.questions}, ${SAID.more}")
                assertEquals(20, settings.questionCount, skin.id)
                assertTrue(disabled(scene, "${WORDS.questions}, ${SAID.more}"), "${skin.id}: more than 20 questions")
                scene.tap("${WORDS.time}, ${SAID.less}")
                assertEquals(10, settings.secondsPerQuestion, skin.id)
                assertTrue(disabled(scene, "${WORDS.time}, ${SAID.less}"), "${skin.id}: less than 10 seconds")
                assertTrue(disabled(scene, "${WORDS.difficulty}, ${SAID.more}"), "${skin.id}: harder than hard")
                scene.tap("${WORDS.difficulty}, ${SAID.less}")
                assertEquals(LobbyDifficulty.MEDIUM, settings.difficulty, skin.id)
                assertTrue(disabled(scene, "${WORDS.players}, ${SAID.less}"), "${skin.id}: fewer seats than members")
                scene.tap("${WORDS.players}, ${SAID.more}")
                assertEquals(6, settings.maxPlayers, skin.id)
                write("settings-${skin.id}-stepped", scene.renderSettled())
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun `the switches flip who may come in and the minus`() {
        eachSkin { skin ->
            var settings by mutableStateOf(LobbySettings())
            val scene = scene(skin, { settings }) { settings = it }
            try {
                scene.renderSettled()
                scene.tap(WORDS.publicShort)
                assertEquals(LobbyVisibility.PUBLIC, settings.visibility, skin.id)
                scene.tap(WORDS.wrongAnswerPenalty)
                assertFalse(settings.wrongAnswerPenalty, skin.id)
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun `a tap on a tile's picture says what it is in a bubble, and a change or a tap on it puts it away`() {
        eachSkin { skin ->
            var settings by mutableStateOf(LobbySettings())
            val scene = scene(skin, { settings }) { settings = it }
            try {
                scene.renderSettled()
                val time = SAID.timeSaid.fill(WORDS.seconds.fill(15))
                assertFalse(time in scene.everyText(), skin.id)
                scene.tap("${WORDS.time}, $time")
                assertTrue(time in scene.everyText(), "${skin.id}: no bubble says \"$time\"")
                write("settings-${skin.id}-explained", scene.renderSettled())
                scene.nodes().first { time in it.texts && WORDS.time in it.texts }.let { bubble ->
                    bubble.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action?.invoke()
                }
                scene.renderSettled()
                assertFalse(time in scene.everyText(), "${skin.id}: a tap on the bubble left it")
                scene.tap("${WORDS.time}, $time")
                scene.tap("${WORDS.time}, ${SAID.more}")
                assertFalse(
                    SAID.timeSaid.fill(WORDS.seconds.fill(20)) in scene.texts(),
                    "${skin.id}: a change left the bubble",
                )
            } finally {
                scene.close()
            }
        }
    }

    private fun disabled(
        scene: ImageComposeScene,
        description: String,
    ): Boolean =
        scene.nodes().first { description in it.descriptions }.config.contains(
            androidx.compose.ui.semantics.SemanticsProperties.Disabled,
        )

    private fun scene(
        skin: Skin,
        settings: LobbySettings,
        language: Language = Language.DEFAULT,
        scale: Float = 1f,
    ): ImageComposeScene = scene(skin, { settings }, language = language, scale = scale, hostName = "Марко") {}

    private fun scene(
        skin: Skin,
        settings: () -> LobbySettings,
        minPlayers: Int = 2,
        language: Language = Language.DEFAULT,
        scale: Float = 1f,
        hostName: String? = null,
        onChange: (LobbySettings) -> Unit,
    ): ImageComposeScene =
        ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f, scale)) {
            GameTheme(language, skin) {
                Stage(Modifier.fillMaxSize()) {
                    SettingsScreen(
                        settings(),
                        TOPICS,
                        onChange = onChange,
                        doneLabel = "OK",
                        onDone = {},
                        minPlayers = minPlayers,
                        title = stringsOf(language).game.newRoom,
                        onBack = {},
                        hostName = hostName,
                    )
                }
            }
        }

    private companion object {
        val WORDS = stringsOf(Language.DEFAULT).game
        val SAID = stringsOf(Language.DEFAULT).roomSettings
    }
}
