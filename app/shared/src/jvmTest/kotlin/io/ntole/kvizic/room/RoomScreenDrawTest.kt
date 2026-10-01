package io.ntole.kvizic.room

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.core.domain.report.QuestionReportReason
import io.ntole.kvizic.descriptions
import io.ntole.kvizic.design.component.Stage
import io.ntole.kvizic.design.component.signed
import io.ntole.kvizic.design.skin.Skin
import io.ntole.kvizic.design.skin.Skins
import io.ntole.kvizic.everyText
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.nodes
import io.ntole.kvizic.renderSettled
import io.ntole.kvizic.tap
import io.ntole.kvizic.texts
import io.ntole.kvizic.theme.GameTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The room drawn off screen at each step, in every skin, on a small phone: the lobby waiting, for a member
 * and for its host, the countdown, a question read, answered and locked in, watched by a late joiner, the
 * reveal, and the results with the way back. With `KVIZIC_DESIGN_DIR` set, each is written there as a PNG.
 */
class RoomScreenDrawTest {
    @Test
    fun `the lobby shows its code, its seats, its settings and the reactions, and waits for the host`() {
        eachSkin { skin ->
            val words = stringsOf(Language.DEFAULT).game
            draw(skin, "lobby", inLobby(GamePhase.Waiting(null))) { scene ->
                val shown = scene.everyText()
                listOf(
                    words.privateRoom,
                    words.roomCode,
                    words.players,
                    "5 / 8",
                    words.waitingForHost,
                    words.host,
                    words.you,
                ).forEach { assertTrue(it in shown, "${skin.id}: \"$it\" is not in $shown") }
                EMOTES.forEach { assertTrue(it.name(words) in scene.descriptions(), "${skin.id}: ${it.id}") }
                assertTrue(words.reactionNudge in shown, "${skin.id}: a member cannot nudge the host")
                assertFalse(words.start in shown, "${skin.id}: a member has no Start")
            }
        }
    }

    @Test
    fun `the host starts the game, and taps a member to choose what to do with them`() {
        eachSkin { skin ->
            val words = stringsOf(Language.DEFAULT).game
            var started = 0
            val kicked = mutableListOf<String>()
            val state = inLobby(GamePhase.Waiting(null), lobby = lobby(host = YOU))
            draw(skin, "lobby-host", state, RoomActions(start = { started++ }, kick = { kicked += it })) { scene ->
                assertTrue(words.start in scene.texts(), "${skin.id}: ${scene.texts()}")
                assertFalse(words.reactionNudge in scene.texts(), "${skin.id}: the host can nudge themselves")
                scene.tap(words.start)
                val nina = scene.nodes().first { "Нина" in it.texts }
                nina.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action?.invoke()
                scene.renderSettled()
                scene.tap(words.removePlayer)
            }
            assertEquals(1, started, skin.id)
            assertEquals(listOf("nina"), kicked, skin.id)
        }
    }

    @Test
    fun `the countdown takes the start's place`() {
        eachSkin { skin ->
            val words = stringsOf(Language.DEFAULT).game
            draw(
                skin,
                "countdown",
                inLobby(GamePhase.Countdown(deadline(3.seconds()), null), lobby = lobby(host = YOU)),
            ) { scene ->
                assertTrue(words.gameStarting in scene.everyText(), "${skin.id}: ${scene.everyText()}")
                assertFalse(words.start in scene.everyText(), skin.id)
            }
        }
    }

    @Test
    fun `a question read alone shows its text, its clock and where its answers will stand`() {
        eachSkin { skin ->
            val words = stringsOf(Language.DEFAULT).game
            draw(skin, "reading", inLobby(reading())) { scene ->
                val shown = scene.everyText()
                listOf(QUESTION.text, words.answersComing, words.question, "Географија").forEach {
                    assertTrue(it in shown, "${skin.id}: \"$it\" is not in $shown")
                }
            }
        }
    }

    @Test
    fun `an answer tapped is sent, and the strip shows who the question still waits for`() {
        eachSkin { skin ->
            val words = stringsOf(Language.DEFAULT).game
            val answers = mutableListOf<Int>()
            draw(skin, "answering", inLobby(answering()), RoomActions(answer = { answers += it })) { scene ->
                OPTIONS.forEach { assertTrue(it in scene.everyText(), "${skin.id}: $it") }
                val waiting = scene.descriptions().single { it.startsWith(words.waitingFor.fill("")) }
                // The player, Нина and Тиха Рода have not answered; Сова and Бојан have.
                listOf("Нина", "Марко", "Тиха Рода").forEach { assertTrue(it in waiting, "${skin.id}: $waiting") }
                assertFalse("Бојан" in waiting, "${skin.id}: $waiting")
                scene.tap(OPTIONS[2])
            }
            assertEquals(listOf(2), answers, skin.id)
        }
    }

    @Test
    fun `an answer locked in takes no more taps and shows who picked what`() {
        eachSkin { skin ->
            val words = stringsOf(Language.DEFAULT).game
            val answers = mutableListOf<Int>()
            val locked =
                answering(
                    answered = setOf("sova", "bojan", YOU),
                    myPick = 0,
                    picks =
                        mapOf(
                            "sova" to 0,
                            "bojan" to 1,
                        ),
                )
            draw(skin, "locked-in", inLobby(locked), RoomActions(answer = { answers += it })) { scene ->
                val tile = scene.nodes().single { OPTIONS[0] in it.texts }
                assertEquals(
                    words.yourAnswer,
                    tile.config.getOrElse(SemanticsProperties.StateDescription) { "" },
                    skin.id,
                )
                assertFalse(
                    androidx.compose.ui.semantics.SemanticsActions.OnClick in tile.config,
                    "${skin.id}: still tappable",
                )
                val waiting = scene.descriptions().single { it.startsWith(words.waitingFor.fill("")) }
                assertFalse("Марко" in waiting, "${skin.id}: the player still waited for: $waiting")
            }
            assertEquals(emptyList(), answers, skin.id)
        }
    }

    @Test
    fun `a member who joined during the game watches it`() {
        eachSkin { skin ->
            val words = stringsOf(Language.DEFAULT).game
            val watching = answering(players = PLAYERS - YOU)
            draw(skin, "spectating", inLobby(watching)) { scene ->
                assertTrue(words.spectating in scene.everyText(), "${skin.id}: ${scene.everyText()}")
                val tile = scene.nodes().single { OPTIONS[0] in it.texts }
                assertFalse(androidx.compose.ui.semantics.SemanticsActions.OnClick in tile.config, skin.id)
            }
        }
    }

    @Test
    fun `the reveal lights the right answer and boards every player with what it gave them and when the next comes`() {
        eachSkin { skin ->
            val words = stringsOf(Language.DEFAULT).game
            draw(skin, "reveal", inLobby(revealing())) { scene ->
                val shown = scene.everyText()
                assertTrue(signed(-25) in shown, "${skin.id}: the player's points are not on the board: $shown")
                MEMBERS.forEach { member ->
                    assertTrue(member.name in shown, "${skin.id}: ${member.name} is not on the board: $shown")
                }
                assertTrue(
                    scene.descriptions().any { it.startsWith(words.nextQuestionIn.substringBefore("{0}")) },
                    "${skin.id}: ${scene.descriptions()}",
                )
                val right = scene.nodes().single { OPTIONS[0] in it.texts }
                assertEquals(
                    words.rightAnswer,
                    right.config.getOrElse(SemanticsProperties.StateDescription) { "" },
                    skin.id,
                )
            }
        }
    }

    @Test
    fun `a question is reported from its reveal, for the reason picked`() {
        val words = stringsOf(Language.DEFAULT).game
        val reported = mutableListOf<Pair<String, QuestionReportReason>>()
        draw(
            Skins.Default,
            "report",
            inLobby(revealing()),
            RoomActions(report = { id, reason ->
                reported +=
                    id to reason
            }),
        ) { scene ->
            scene.tap(words.reportQuestion)
            assertTrue(words.reportWhy in scene.everyText(), "${scene.everyText()}")
            scene.tap(words.reportAmbiguous)
        }
        assertEquals(listOf("q1" to QuestionReportReason.AMBIGUOUS), reported)
    }

    @Test
    fun `the results crown the winner and give the player their way back`() {
        eachSkin { skin ->
            val words = stringsOf(Language.DEFAULT).game
            val back = mutableListOf<String>()
            val members = MEMBERS.map { if (it.playerId == YOU) it.copy(onResults = true) else it }
            val state = inLobby(GamePhase.Waiting(RESULTS), lobby = lobby(members = members))
            draw(
                skin,
                "results",
                state,
                RoomActions(backToLobby = { back += "back" }, leave = { back += "leave" }),
            ) { scene ->
                val shown = scene.everyText()
                listOf(words.winner.fill("Нина"), words.rightOf.fill(7, 10), words.backToRoom, words.leave).forEach {
                    assertTrue(it in shown, "${skin.id}: \"$it\" is not in $shown")
                }
                scene.tap(words.backToRoom)
            }
            assertEquals(listOf("back"), back, skin.id)
        }
    }

    @Test
    fun `a new game starting while the player looks at the results lets them in`() {
        val words = stringsOf(Language.DEFAULT).game
        val members = MEMBERS.map { if (it.playerId == YOU) it.copy(onResults = true) else it }
        val state = inLobby(GamePhase.Countdown(deadline(5.seconds()), RESULTS), lobby = lobby(members = members))
        draw(Skins.Default, "results-new-game", state) { scene ->
            assertTrue(words.joinNewGame in scene.everyText(), "${scene.everyText()}")
        }
    }

    @Test
    fun `a dropped connection says it is being made again`() {
        val words = stringsOf(Language.DEFAULT).game
        draw(Skins.Default, "reconnecting", inLobby(GamePhase.Waiting(null), reconnecting = true)) { scene ->
            assertTrue(words.reconnecting in scene.everyText(), "${scene.everyText()}")
        }
    }

    private fun eachSkin(check: (Skin) -> Unit) = Skins.ALL.forEach(check)

    private fun draw(
        skin: Skin,
        name: String,
        state: LobbySessionState.InLobby,
        actions: RoomActions = RoomActions(),
        check: (ImageComposeScene) -> Unit,
    ) {
        val scene =
            ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
                GameTheme(Language.DEFAULT, skin) {
                    Stage(Modifier.fillMaxSize()) {
                        RoomScreen(state, TOPICS, note = null, bursts = emptyMap(), actions = actions)
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

    private fun Int.seconds() = kotlin.time.Duration.parse("${this}s")

    private companion object {
        const val WIDTH = 360
        const val HEIGHT = 640
    }
}
