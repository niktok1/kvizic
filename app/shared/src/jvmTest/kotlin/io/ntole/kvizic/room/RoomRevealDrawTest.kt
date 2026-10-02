package io.ntole.kvizic.room

import androidx.compose.ui.semantics.SemanticsProperties
import io.ntole.kvizic.core.domain.lobby.FinalStanding
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.report.QuestionReportReason
import io.ntole.kvizic.descriptions
import io.ntole.kvizic.design.component.signed
import io.ntole.kvizic.design.skin.Skins
import io.ntole.kvizic.everyText
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.nodes
import io.ntole.kvizic.tap
import io.ntole.kvizic.texts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The reveal and the results drawn off screen in every skin: the right answer lit and the board, a question
 * reported, the winner and the way back, and a new game starting meanwhile.
 */
class RoomRevealDrawTest : RoomStills() {
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
                listOf(words.winner, "Нина", words.rightOf.fill(7, 10), words.backToRoom, words.leave).forEach {
                    assertTrue(it in shown, "${skin.id}: \"$it\" is not in $shown")
                }
                scene.tap(words.backToRoom)
            }
            assertEquals(listOf("back"), back, skin.id)
        }
    }

    @Test
    fun `the results fit eight players and the longest name, and say so when the player won`() {
        eachSkin { skin ->
            val words = stringsOf(Language.DEFAULT).game
            // The longest a name may be, in the widest letter there is.
            val longest = "Ш".repeat(24)
            val avatars = listOf("fox", "hedgehog", "bear", "owl", "stork", "wolf", "lynx", "deer")
            val standings =
                avatars.mapIndexed { i, avatar ->
                    val place = i + 1
                    FinalStanding(
                        if (place == 1) YOU else "p$place",
                        if (place == 1) longest else "Играч $place",
                        avatar,
                        2000 - place * 100,
                        10 - place,
                        place,
                        true,
                    )
                }
            val members = MEMBERS.map { if (it.playerId == YOU) it.copy(onResults = true) else it }
            val state =
                inLobby(
                    GamePhase.Waiting(RESULTS.copy(standings = standings)),
                    lobby = lobby(members = members),
                )
            draw(skin, "results-eight", state) { scene ->
                val shown = scene.everyText()
                assertTrue(words.youWon in shown, "${skin.id}: $shown")
                assertFalse(words.winner in shown, "${skin.id}: $shown")
                assertTrue(longest in shown, "${skin.id}: the longest name is cut: $shown")
                // Nothing is pushed off the phone: the way back stands whole at the foot.
                val back = scene.nodes().single { words.backToRoom in it.texts }.boundsInRoot
                assertTrue(back.height > 0 && back.bottom <= HEIGHT, "${skin.id}: the way back is at $back")
                val name = scene.nodes().first { longest in it.texts }.boundsInRoot
                assertTrue(name.width > 0 && name.right <= WIDTH, "${skin.id}: the winner's name is at $name")
            }
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
}
