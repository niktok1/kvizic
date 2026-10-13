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
import org.jetbrains.skia.Image
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The lobby and the room's dialogs with every word whole, in every skin, in a language, at the sizes a test
 * names: the settings' chips wrap rather than squeeze one another to „..“, the host's and a member's choices
 * about a seat and the reasons to report a question are read whole. [RoomFitDrawTest] draws them in Serbian
 * Cyrillic on every size the game was tried on; [RoomFitLanguagesDrawTest], every other language on the
 * narrowest phone at the usual font and a large one, so a language's long word fails a test.
 */
abstract class RoomFitChecks : RoomStills() {
    /** The lobby's chips whole with every setting under a long name, and Start on the screen, at each of [sizes]. */
    protected fun lobbyChipsWhole(
        language: Language,
        sizes: List<Pair<Int, Float>>,
    ) {
        eachSkin { skin ->
            val words = stringsOf(language).game
            val state = inLobby(GamePhase.Waiting(null), lobby = lobby(host = YOU, settings = BUSY))
            sizes.forEach { (width, fontScale) ->
                val where = "$language, ${skin.id} at $width, $fontScale"
                val name = still("fit-lobby-$width-$fontScale", language)
                draw(skin, name, state, width = width, fontScale = fontScale, language = language) { scene ->
                    assertEquals(emptyList(), cutBeyondSeats(scene, language), "$where: cut")
                    val shown = scene.everyText()
                    listOf(
                        words.levelName(LobbyDifficulty.HARD),
                        words.penaltyOn,
                        words.seatCount.of(BUSY.maxPlayers, language),
                        words.privateShort,
                    ).forEach {
                        assertTrue(it in shown, "$where: no \"$it\" in $shown")
                    }
                    val start = scene.nodes().single { words.start in it.texts }.boundsInRoot
                    assertTrue(start.bottom <= HEIGHT, "$where: Start runs off the screen at $start")
                }
            }
        }
    }

    /** The host's choices about a member, opened from her seat, whole at each of [sizes]. */
    protected fun memberChoicesWhole(
        language: Language,
        sizes: List<Pair<Int, Float>>,
    ) {
        eachSkin { skin ->
            val words = stringsOf(language).game
            val state = inLobby(GamePhase.Waiting(null), lobby = lobby(host = YOU))
            sizes.forEach { (width, fontScale) ->
                val where = "$language, ${skin.id} at $width, $fontScale"
                val name = still("fit-member-$width-$fontScale", language)
                draw(skin, name, state, width = width, fontScale = fontScale, language = language) { scene ->
                    write("room-${skin.id}-$name-open", tapSeat(scene, "Нина", language))
                    assertEquals(emptyList(), cutBeyondSeats(scene, language), "$where: cut")
                    assertTrue(words.removePlayer in scene.everyText(), "$where: no way to remove her")
                }
            }
        }
    }

    /** A member's vote to put a player out, opened from his seat, whole on the narrowest phone at both font sizes. */
    protected fun voteWhole(language: Language) {
        eachSkin { skin ->
            val words = stringsOf(language).game
            val state = inLobby(GamePhase.Waiting(null))
            listOf(1f, LARGE_FONT).forEach { fontScale ->
                val where = "$language, ${skin.id} at $fontScale"
                val name = still("fit-vote-$fontScale", language)
                draw(skin, name, state, fontScale = fontScale, language = language) { scene ->
                    write("room-${skin.id}-$name-open", tapSeat(scene, "Бојан", language))
                    assertEquals(emptyList(), cutBeyondSeats(scene, language), "$where: cut")
                    assertTrue(words.voteKick in scene.everyText(), "$where: no vote")
                }
            }
        }
    }

    /** Every reason to report a question, and the dialog's title and its way out, whole on the narrowest phone. */
    protected fun reportWhole(language: Language) {
        eachSkin { skin ->
            val strings = stringsOf(language)
            val words = strings.game
            val dialog = QuestionReportReason.entries.map { words.reasonText(it) } + strings.cancel + words.reportWhy
            listOf(1f, LARGE_FONT).forEach { fontScale ->
                val where = "$language, ${skin.id} at $fontScale"
                val name = still("fit-report-$fontScale", language)
                draw(skin, name, inLobby(revealing()), fontScale = fontScale, language = language) { scene ->
                    scene.tap(words.reportQuestion)
                    // The dialog comes in over time: drawn settled before what it shows is read.
                    write("room-${skin.id}-$name-open", scene.renderSettled())
                    val shown = scene.everyText()
                    dialog.forEach { assertTrue(it in shown, "$where: no \"$it\" in $shown") }
                    assertEquals(emptyList(), scene.cutTexts().filter { it in dialog }, "$where: cut")
                }
            }
        }
    }

    /** A still's name: the Serbian Cyrillic one's as it always was, another language's with its tag. */
    private fun still(
        name: String,
        language: Language,
    ): String = if (language == Language.DEFAULT) name else "$name-${language.tag}"

    /** What the scene cuts but a seat's name, which a seat ends in an ellipsis when long and says whole. */
    private fun cutBeyondSeats(
        scene: ImageComposeScene,
        language: Language,
    ): List<String> = scene.cutTexts().filterNot { text -> MEMBERS.any { shownIn(it.name, language) == text } }

    /** Taps the seat of the member named [name], as [language] shows it, as a finger would, and draws it settled. */
    private fun tapSeat(
        scene: ImageComposeScene,
        name: String,
        language: Language,
    ): Image {
        val seat = scene.nodes().first { shownIn(name, language) in it.texts }
        seat.config[SemanticsActions.OnClick].action?.invoke()
        return scene.renderSettled()
    }

    protected companion object {
        /**
         * Three topics, the summary „Географија, Историја +1“, a difficulty, the seats and who can join: the
         * lobby's most chips, under a name as long as a room's may be.
         */
        val BUSY =
            LobbySettings(
                topics = listOf("GEOGRAPHY", "HISTORY", "FOOD"),
                difficulty = LobbyDifficulty.HARD,
                name = "Квиз петком код Мирјане",
            )

        /** The narrowest phone at the usual font and at a large one: where a word is cut first. */
        val NARROWEST = listOf(WIDTH to 1f, WIDTH to LARGE_FONT)
    }
}
