package io.ntole.kvizic.design

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.design.component.AnswerGrid
import io.ntole.kvizic.design.component.MIN_ANSWERS
import io.ntole.kvizic.design.skin.Skin
import io.ntole.kvizic.design.skin.Skins
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The longest question and answers the rules allow ([KvizicApi.Limits]: 120 characters, and four of 60),
 * on the smallest phones the game is drawn for, in every skin and every step of a question: every text is
 * set whole, smaller where it must be, never cut, and every answer stands on screen, apart from the rest.
 */
class LongTextFitTest {
    @Test
    fun `the texts are as long as the rules allow`() {
        assertEquals(KvizicApi.Limits.MAX_QUESTION_TEXT_LENGTH, LONGEST_QUESTION.length)
        assertEquals(KvizicApi.Limits.MAX_OPTIONS, LONGEST_ANSWERS.size)
        LONGEST_ANSWERS.forEach { assertEquals(KvizicApi.Limits.MAX_OPTION_LENGTH, it.length, it) }
        assertTrue(LONG_QUESTION.length <= KvizicApi.Limits.MAX_QUESTION_TEXT_LENGTH, "the mockups' question")
        LONG_ANSWERS.forEach { assertTrue(it.length <= KvizicApi.Limits.MAX_OPTION_LENGTH, it) }
    }

    @Test
    fun `the fewest answers a tile grid lays out are the wire's`() {
        assertEquals(KvizicApi.Limits.MIN_OPTIONS, MIN_ANSWERS)
    }

    @Test
    fun `the longest question over the longest answers fits a small phone whole, answered and locked in`() {
        eachSkinAndPhone { skin, width, height ->
            STEPS.forEach { (step, screen) ->
                val scene = stageScene(skin, width, height) { screen() }
                try {
                    writeDesign("longest-${skin.id}-$step-${width}x$height", scene.renderUpTo(SETTLED))
                    val where = "${skin.id} $step at $width by $height"
                    assertWhole(scene, listOf(LONGEST_QUESTION) + LONGEST_ANSWERS, where)
                    val tiles =
                        LONGEST_ANSWERS.map { answer ->
                            scene.nodes().single { node -> node.texts.any { it.equals(answer, ignoreCase = true) } }
                        }
                    tiles.forEach { tile ->
                        assertTrue(tile.boundsInRoot.bottom <= height, "$where: ${tile.texts} runs off the screen")
                    }
                    tiles.forEachIndexed { i, tile ->
                        tiles.drop(i + 1).forEach { other ->
                            assertFalse(tile.boundsInRoot.overlaps(other.boundsInRoot), "$where: two answers overlap")
                        }
                    }
                } finally {
                    scene.close()
                }
            }
        }
    }

    @Test
    fun `the longest question read alone fits whole`() {
        eachSkinAndPhone { skin, width, height ->
            val scene = stageScene(skin, width, height) { ReadingMock(LONGEST_QUESTION) }
            try {
                scene.renderUpTo(SETTLED)
                assertWhole(scene, listOf(LONGEST_QUESTION), "${skin.id} at $width by $height")
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun `every question stands alike, in a column on a phone and in a grid on a wide window`() {
        Skins.ALL.forEach { skin ->
            val short = listOf("Дунав", "Сава", "Тиса", "Морава")
            val oneLong = short.take(3) + LONGEST_ANSWERS.last()
            listOf(short, oneLong).forEach { options ->
                val which = if (options == short) "short answers" else "one long answer"
                val phone = layoutOfGrid(skin, options, SMALL_WIDTH, SMALL_HEIGHT)
                assertEquals(Layout.COLUMN, phone, "${skin.id}: $which on a phone")
                val wide = layoutOfGrid(skin, options, SMALL_HEIGHT, SMALL_WIDTH)
                assertEquals(Layout.GRID, wide, "${skin.id}: $which on a phone on its side")
            }
        }
    }

    private enum class Layout { GRID, COLUMN }

    private fun layoutOfGrid(
        skin: Skin,
        options: List<String>,
        width: Int,
        height: Int,
    ): Layout {
        val scene =
            stageScene(skin, width, height) {
                AnswerGrid(options, Modifier.fillMaxSize(), onPick = {})
            }
        try {
            scene.renderUpTo(SETTLED)
            val tiles =
                options.map { answer ->
                    scene.nodes().single { node -> node.texts.any { it.equals(answer, ignoreCase = true) } }
                }
            return if (tiles[0].boundsInRoot.top == tiles[1].boundsInRoot.top) Layout.GRID else Layout.COLUMN
        } finally {
            scene.close()
        }
    }

    /** Each of [texts] is on screen, laid out whole: every line of it shown, none past its box. */
    private fun assertWhole(
        scene: androidx.compose.ui.ImageComposeScene,
        texts: List<String>,
        where: String,
    ) {
        texts.forEach { text ->
            val node =
                scene.everyNode().singleOrNull { node -> node.texts.any { it.equals(text, ignoreCase = true) } }
                    ?: throw AssertionError("$where: no node holds \"$text\"")
            val laid = layoutOf(node) ?: throw AssertionError("$where: \"$text\" is laid out nowhere")
            assertFalse(laid.hasVisualOverflow, "$where: \"${text.take(30)}…\" is cut")
        }
    }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult? {
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return null
        val results = mutableListOf<TextLayoutResult>()
        action(results)
        return results.firstOrNull()
    }

    private fun eachSkinAndPhone(check: (Skin, Int, Int) -> Unit) {
        Skins.ALL.forEach { skin -> PHONES.forEach { (width, height) -> check(skin, width, height) } }
    }

    private companion object {
        const val SETTLED = 2_000L * MILLI
        const val SMALL_WIDTH = 360
        const val SMALL_HEIGHT = 640

        /** An iPhone SE, and the smallest Android phone in wide use, in dp at one pixel each. */
        val PHONES = listOf(375 to 667, SMALL_WIDTH to SMALL_HEIGHT)

        /**
         * A question's steps with its answers up and read against the clock: someone's pick on every answer
         * once locked in. Not yet the reveal, whose standings of up to eight players are laid out with its
         * screen, and which then joins them.
         */
        val STEPS: List<Pair<String, @Composable () -> Unit>> =
            listOf(
                "answering" to { AnsweringMock(LONGEST_QUESTION, LONGEST_ANSWERS) },
                "locked-in" to {
                    LockedInMock(
                        LONGEST_QUESTION,
                        LONGEST_ANSWERS,
                        picks =
                            mapOf(
                                0 to listOf(Nina, Marko),
                                1 to listOf(Sova),
                                2 to listOf(Bojan),
                                3 to listOf(Roda),
                            ),
                    )
                },
            )

        const val LONGEST_QUESTION =
            "Који српски научник, рођен у Смиљану у Лици 1856. године, има по себи названу јединицу мере за " +
                "густину магнетног флукса?"

        val LONGEST_ANSWERS =
            listOf(
                "Никола Тесла, проналазач који је живео у Сједињеним Државама",
                "Михајло Пупин, физичар из Идвора и изумитељ Пупиновог калема",
                "Милутин Миланковић, математичар који је објаснио ледена доба",
                "Јосиф Панчић, лекар и ботаничар, открио је Панчићеву оморику",
            )
    }
}
