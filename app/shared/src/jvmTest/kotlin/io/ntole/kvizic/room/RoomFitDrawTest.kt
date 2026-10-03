package io.ntole.kvizic.room

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.LobbyDifficulty
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.domain.report.QuestionReportReason
import io.ntole.kvizic.cutTexts
import io.ntole.kvizic.everyText
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.nodes
import io.ntole.kvizic.renderSettled
import io.ntole.kvizic.tap
import io.ntole.kvizic.texts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The lobby and the room's dialogs with every word whole, in every skin, on the phones and the tablet the
 * game was tried on (393 and 800 wide) and on the narrowest at a phone's large font: the settings' chips wrap
 * rather than squeeze one another to „..“, the host's and a member's choices about a seat and the reasons to
 * report a question are read whole.
 */
class RoomFitDrawTest : RoomStills() {
    @Test
    fun `the lobby's chips are whole with three topics and a difficulty`() {
        eachSkin { skin ->
            val words = stringsOf(Language.DEFAULT).game
            val state = inLobby(GamePhase.Waiting(null), lobby = lobby(host = YOU, settings = BUSY))
            LOBBY_SIZES.forEach { (width, fontScale) ->
                val where = "${skin.id} at $width, $fontScale"
                draw(skin, "fit-lobby-$width-$fontScale", state, width = width, fontScale = fontScale) { scene ->
                    assertEquals(emptyList(), cutBeyondSeats(scene), "$where: cut")
                    val shown = scene.everyText()
                    listOf(words.levelName(LobbyDifficulty.HARD), words.penaltyOn).forEach {
                        assertTrue(it in shown, "$where: no \"$it\" in $shown")
                    }
                    val start = scene.nodes().single { words.start in it.texts }.boundsInRoot
                    assertTrue(start.bottom <= HEIGHT, "$where: Start runs off the screen at $start")
                }
            }
        }
    }

    @Test
    fun `the host's choices about a member are whole on a phone and a tablet`() {
        eachSkin { skin ->
            val words = stringsOf(Language.DEFAULT).game
            val state = inLobby(GamePhase.Waiting(null), lobby = lobby(host = YOU))
            DIALOG_SIZES.forEach { (width, fontScale) ->
                val where = "${skin.id} at $width, $fontScale"
                draw(skin, "fit-member-$width-$fontScale", state, width = width, fontScale = fontScale) { scene ->
                    tapSeat(scene, "Нина")
                    write("room-${skin.id}-fit-member-$width-$fontScale-open", scene.renderSettled())
                    assertEquals(emptyList(), cutBeyondSeats(scene), "$where: cut")
                    assertTrue(words.removePlayer in scene.everyText(), "$where: no way to remove her")
                }
            }
        }
    }

    @Test
    fun `a member's vote to put a player out is whole on the narrowest phone`() {
        eachSkin { skin ->
            val words = stringsOf(Language.DEFAULT).game
            listOf(1f, LARGE_FONT).forEach { fontScale ->
                val where = "${skin.id} at $fontScale"
                draw(skin, "fit-vote-$fontScale", inLobby(GamePhase.Waiting(null)), fontScale = fontScale) { scene ->
                    tapSeat(scene, "Бојан")
                    write("room-${skin.id}-fit-vote-$fontScale-open", scene.renderSettled())
                    assertEquals(emptyList(), cutBeyondSeats(scene), "$where: cut")
                    assertTrue(words.voteKick in scene.everyText(), "$where: no vote")
                }
            }
        }
    }

    @Test
    fun `every reason to report a question is read whole on the narrowest phone`() {
        eachSkin { skin ->
            val strings = stringsOf(Language.DEFAULT)
            val words = strings.game
            val dialog = QuestionReportReason.entries.map { words.reasonText(it) } + strings.cancel + words.reportWhy
            listOf(1f, LARGE_FONT).forEach { fontScale ->
                val where = "${skin.id} at $fontScale"
                draw(skin, "fit-report-$fontScale", inLobby(revealing()), fontScale = fontScale) { scene ->
                    scene.tap(words.reportQuestion)
                    write("room-${skin.id}-fit-report-$fontScale-open", scene.renderSettled())
                    val shown = scene.everyText()
                    dialog.forEach { assertTrue(it in shown, "$where: no \"$it\" in $shown") }
                    assertEquals(emptyList(), scene.cutTexts().filter { it in dialog }, "$where: cut")
                }
            }
        }
    }

    /** What the scene cuts but a seat's name, which a seat ends in an ellipsis when long and says whole. */
    private fun cutBeyondSeats(scene: ImageComposeScene): List<String> =
        scene.cutTexts().filterNot { text -> MEMBERS.any { it.name == text } }

    /** Taps the seat of the member named [name], as a finger would. */
    private fun tapSeat(
        scene: ImageComposeScene,
        name: String,
    ) {
        val seat = scene.nodes().first { name in it.texts }
        seat.config[SemanticsActions.OnClick].action?.invoke()
        scene.renderSettled()
    }

    private companion object {
        /** Three topics, the summary „Географија, Историја +1“, and a difficulty: the lobby's most chips. */
        val BUSY =
            LobbySettings(topics = listOf("GEOGRAPHY", "HISTORY", "FOOD"), difficulty = LobbyDifficulty.HARD)

        /** The phone the chips were crushed on, the narrowest, and the narrowest at a large font. */
        val LOBBY_SIZES = listOf(393 to 1f, WIDTH to 1f, WIDTH to LARGE_FONT)

        /** The phone and the tablet the host's Kick was squeezed on, and the narrowest at a large font. */
        val DIALOG_SIZES = listOf(393 to 1f, 800 to 1f, WIDTH to LARGE_FONT)
    }
}
