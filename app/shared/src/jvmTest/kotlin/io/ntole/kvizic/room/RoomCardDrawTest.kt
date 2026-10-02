package io.ntole.kvizic.room

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.GameError
import io.ntole.kvizic.core.domain.lobby.LobbyDifficulty
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.domain.lobby.PublicLobbies
import io.ntole.kvizic.core.domain.lobby.PublicLobby
import io.ntole.kvizic.descriptions
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
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A room shown before a seat is taken in it, on a small phone: the public list with each room's name and every
 * one of its settings, and the join screen with the room its code names.
 */
class RoomCardDrawTest {
    private val named =
        PublicLobby(
            code = "482915",
            hostName = "Нина",
            hostAvatar = "fox",
            players = 3,
            maxPlayers = 8,
            inGame = false,
            settings =
                LobbySettings(
                    questionCount = 15,
                    secondsPerQuestion = 20,
                    topics = listOf("GEOGRAPHY", "HISTORY"),
                    difficulty = LobbyDifficulty.HARD,
                    wrongAnswerPenalty = false,
                    name = "Петак увече",
                ),
        )
    private val plain = PublicLobby("193746", "Бојан", "bear", 8, 8, true, LobbySettings())

    @Test
    fun `the public list names a room and puts every setting on its chips`() {
        Language.entries.forEach { language ->
            val words = stringsOf(language).game
            val scene =
                scene(language) {
                    PublicRoomsScreen(
                        lobbies = PublicLobbies(listOf(named, plain), online = 12, searching = 3),
                        topics = TOPICS,
                        failure = null,
                        entry = Entry.None,
                        onJoin = { joined += it },
                        onQuickPlay = {},
                        onCreate = {},
                        onDismissFailure = {},
                    )
                }
            try {
                write("rooms-${language.name.lowercase()}", scene.renderSettled())
                val shown = scene.everyText()
                val expected =
                    listOf(
                        shownIn("Петак увече", language),
                        shownIn("Нина", language),
                        words.questionCount.of(15, language),
                        words.seconds.fill(20),
                        words.levelName(LobbyDifficulty.HARD),
                        words.penaltyOff,
                        "3 / 8",
                        // A room with no name of its own is called by its host, and says it is full and under way.
                        shownIn("Бојан", language),
                        "8 / 8",
                        words.inGame,
                        words.penaltyOn,
                    )
                expected.forEach { assertTrue(it in shown, "$language: \"$it\" is not in $shown") }
                assertEquals(1, shown.count { it == shownIn("Нина", language) }, "$language: the host is named once")
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun `a tap on a room joins it by its code, and a full room takes none`() {
        val words = stringsOf(Language.DEFAULT).game
        joined.clear()
        val scene =
            scene(Language.DEFAULT) {
                PublicRoomsScreen(
                    PublicLobbies(listOf(named, plain), 12, 3),
                    TOPICS,
                    null,
                    Entry.None,
                    { joined += it },
                    {},
                    {},
                    {},
                )
            }
        try {
            scene.tap("Петак увече")
            assertEquals(listOf("482915"), joined)
            // The full room is shown, and to a finger and a screen reader it is switched off.
            val full = scene.nodes().single { words.publicRoomOf.fill("Бојан") in it.descriptions }
            assertTrue(SemanticsProperties.Disabled in full.config, "a full room can be tapped")
        } finally {
            scene.close()
        }
    }

    @Test
    fun `the join screen shows the room its code names before the player joins it`() {
        val words = stringsOf(Language.DEFAULT).game
        val found =
            scene(Language.DEFAULT) {
                JoinScreen("482915", {}, {}, Entry.None, {}, preview = RoomPreview.Found(named), topics = TOPICS)
            }
        try {
            write("join-preview", found.renderSettled())
            val shown = found.everyText()
            listOf("Петак увече", "Нина", words.questionCount.of(15, Language.DEFAULT), "3 / 8", words.join).forEach {
                assertTrue(it in shown, "\"$it\" is not in $shown")
            }
            // The keypad is whole under it, on a small phone.
            val join = found.nodes().single { words.join in it.texts }.boundsInRoot
            assertTrue(join.bottom <= HEIGHT, "Join runs off the screen: $join")
        } finally {
            found.close()
        }

        // A code no room has says so at once; a read that failed says nothing, and joining still asks the server.
        val missing =
            scene(Language.DEFAULT) {
                JoinScreen("123456", {}, {}, Entry.None, {}, preview = RoomPreview.Missing(GameError.LOBBY_NOT_FOUND))
            }
        val offline =
            scene(Language.DEFAULT) {
                JoinScreen("123456", {}, {}, Entry.None, {}, preview = RoomPreview.Missing(CoreError.NETWORK))
            }
        try {
            assertTrue(words.roomNotFound in missing.everyText(), "${missing.everyText()}")
            assertFalse(words.roomNotFound in offline.everyText(), "${offline.everyText()}")
        } finally {
            missing.close()
            offline.close()
        }
    }

    @Test
    fun `the host names the room in the settings and a name is cut to what a room may be called`() {
        var changed: LobbySettings? = null
        val scene =
            scene(Language.DEFAULT) {
                SettingsScreen(LobbySettings(), TOPICS, onChange = { changed = it }, doneLabel = "OK", onDone = {})
            }
        try {
            val words = stringsOf(Language.DEFAULT).game
            assertTrue(words.roomName in scene.everyText(), "${scene.everyText()}")
            scene.type(0, "Петак увече")
            assertEquals("Петак увече", changed?.name)
            scene.type(0, "Ш".repeat(40))
            assertEquals(24, changed?.name?.length, "a room's name is at most 24 long")
            scene.type(0, "")
            assertEquals(null, changed?.name, "an empty name is none")
        } finally {
            scene.close()
        }
    }

    private val joined = mutableListOf<String>()

    private fun scene(
        language: Language,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ): ImageComposeScene =
        ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
            GameTheme(language) { content() }
        }.also { it.renderSettled() }

    private fun write(
        name: String,
        image: Image,
    ) {
        val directory = System.getenv("KVIZIC_DESIGN_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        directory.mkdirs()
        File(directory, "$name.png").writeBytes(checkNotNull(image.encodeToData()).bytes)
    }

    private companion object {
        /** A small phone, 360 by 640, less its status bar. */
        const val WIDTH = 360
        const val HEIGHT = 616
    }
}
