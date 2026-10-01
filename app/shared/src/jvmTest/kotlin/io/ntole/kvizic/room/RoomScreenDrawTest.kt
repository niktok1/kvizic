package io.ntole.kvizic.room

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.toSize
import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.descriptions
import io.ntole.kvizic.everyNode
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
 * A question drawn off screen in every skin: read, answered and locked in, watched by a late joiner, and the
 * bar over it. The lobby is [RoomLobbyDrawTest]'s, the reveal and the results [RoomRevealDrawTest]'s.
 */
class RoomScreenDrawTest : RoomStills() {
    @Test
    fun `a question read alone shows its text, its clock and where its answers will stand`() {
        eachSkin { skin ->
            val words = stringsOf(Language.DEFAULT).game
            draw(skin, "reading", inLobby(reading())) { scene ->
                val shown = scene.everyText()
                listOf(QUESTION.text, words.answersComing, "Географија").forEach {
                    assertTrue(it in shown, "${skin.id}: \"$it\" is not in $shown")
                }
                // The round stands in numbers alone, and is said in words.
                val round = words.questionOf.fill(3, 10)
                assertTrue(round in scene.descriptions(), "${skin.id}: \"$round\" is not said: ${scene.descriptions()}")
                assertFalse(shown.any { "Питање" in it }, "${skin.id}: the round is written in words: $shown")
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
    fun `the bar keeps its clock in the middle and four digits of points whole, and the topic stands on the card`() {
        eachSkin { skin ->
            val words = stringsOf(Language.DEFAULT).game
            val (before, after) = words.secondsToAnswer.split("{0}")
            val asked = inLobby(answering().copy(question = QUESTION.copy(topic = LONGEST_TOPIC.id)))
            val topics = TOPICS + LONGEST_TOPIC
            listOf("answering-long-topic" to asked, "reveal" to inLobby(revealing())).forEach { (name, state) ->
                // As wide as with room to spare: no flap cut off.
                var roomy = 0f
                draw(skin, "$name-wide", state, topics = topics, width = WIDE) { roomy = it.laidOut(POINTS).width }
                draw(skin, name, state, topics = topics) { scene ->
                    val points = scene.laidOut(POINTS)
                    assertTrue(points.left >= 0f && points.right <= WIDTH, "${skin.id} $name: the points at $points")
                    assertEquals(roomy, points.width, 0.5f, "${skin.id} $name: the points are cut off at $points")
                }
            }
            draw(skin, "answering-long-topic", asked, topics = topics) { scene ->
                val clock =
                    scene.laidOut { node ->
                        node.descriptions.any { it.startsWith(before) && it.endsWith(after) }
                    }
                assertEquals(WIDTH / 2f, clock.center.x, 1f, "${skin.id}: the clock at $clock is not in the middle")
                // The tab stands on the card's edge, clear of the bar and of the question.
                val tab = scene.laidOut { LONGEST_TOPIC.nameSr in it.texts }
                val leave = scene.laidOut { words.leave in it.descriptions }
                val question = scene.laidOut { QUESTION.text in it.texts }
                assertTrue(tab.left >= 0f && tab.right <= WIDTH, "${skin.id}: the topic at $tab")
                assertTrue(tab.top >= leave.bottom, "${skin.id}: the topic at $tab is on the bar at $leave")
                assertTrue(tab.bottom <= question.top, "${skin.id}: the topic at $tab is on the question at $question")
            }
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

    /** Where the first node that [matches] is laid out, cut by nothing. */
    private fun ImageComposeScene.laidOut(matches: (SemanticsNode) -> Boolean): Rect =
        everyNode().first(matches).let { Rect(it.positionInRoot, it.size.toSize()) }

    private companion object {
        /** A screen with room to spare across. */
        const val WIDE = 720

        /** The player's points on the bar, the first of them in the scene: 1 210, four digits. */
        val POINTS: (SemanticsNode) -> Boolean = { "1210" in it.descriptions }

        /** The longest of the server's topics' names, as its migrations write it. */
        val LONGEST_TOPIC = Topic("SCIENCE", "Наука и технологија", "Science & tech", 30, groupId = "KNOWLEDGE")
    }
}
