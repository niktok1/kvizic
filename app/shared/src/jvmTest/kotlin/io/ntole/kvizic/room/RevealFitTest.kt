package io.ntole.kvizic.room

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.domain.lobby.AnswerResult
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.Reveal
import io.ntole.kvizic.core.domain.lobby.Standing
import io.ntole.kvizic.design.component.Stage
import io.ntole.kvizic.design.skin.Skins
import io.ntole.kvizic.everyNode
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.renderSettled
import io.ntole.kvizic.texts
import io.ntole.kvizic.theme.GameTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The reveal at its fullest on the smallest phones, in every skin: eight players, the longest question,
 * four of the longest answers and the longest explanation the rules allow. Every one of those texts is
 * set whole, every answer stands on screen apart from the rest, and the player's verdict and the next
 * question's countdown are on screen.
 */
class RevealFitTest {
    @Test
    fun `the texts are as long as the rules allow`() {
        assertEquals(KvizicApi.Limits.MAX_QUESTION_TEXT_LENGTH, QUESTION_TEXT.length)
        ANSWERS.forEach { assertEquals(KvizicApi.Limits.MAX_OPTION_LENGTH, it.length, it) }
        assertEquals(KvizicApi.Limits.MAX_OPTIONS, ANSWERS.size)
        assertEquals(KvizicApi.Limits.MAX_EXPLANATION_LENGTH, EXPLANATION.length)
        assertEquals(KvizicApi.Limits.MAX_PLAYERS, EIGHT.size)
    }

    @Test
    fun `a full reveal fits a small phone whole`() {
        val failures = mutableListOf<String>()
        Skins.ALL.forEach { skin ->
            PHONES.forEach { (width, height) ->
                val where = "${skin.id} at $width by $height"
                val scene =
                    ImageComposeScene(width = width, height = height, density = Density(1f)) {
                        GameTheme(Language.DEFAULT, skin) {
                            Stage(Modifier.fillMaxSize()) {
                                RoomScreen(FULL, TOPICS, note = null, bursts = emptyMap(), actions = RoomActions())
                            }
                        }
                    }
                try {
                    write("reveal-full-${skin.id}-${width}x$height", scene.renderSettled())
                    (listOf(QUESTION_TEXT, EXPLANATION) + ANSWERS).forEach { text ->
                        val node = scene.everyNode().firstOrNull { node -> node.texts.any { it.equals(text, true) } }
                        when {
                            node == null -> {
                                failures += "$where: \"${text.take(24)}…\" is not shown"
                            }

                            layoutOf(node)?.hasVisualOverflow == true -> {
                                failures +=
                                    "$where: \"${text.take(24)}…\" is cut"
                            }

                            node.boundsInRoot.bottom > height -> {
                                failures += "$where: \"${text.take(24)}…\" runs off"
                            }
                        }
                    }
                    val tiles = ANSWERS.mapNotNull { answer -> scene.everyNode().firstOrNull { answer in it.texts } }
                    tiles.forEachIndexed { i, tile ->
                        tiles.drop(i + 1).forEach { other ->
                            if (tile.boundsInRoot.overlaps(other.boundsInRoot)) {
                                failures +=
                                    "$where: two answers overlap"
                            }
                        }
                    }
                    val texts = scene.everyNode().filter { it.boundsInRoot.bottom <= height }.flatMap { it.texts }
                    if (texts.none { "−" in it || "-" in it }) failures += "$where: no verdict on screen in $texts"
                } finally {
                    scene.close()
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun layoutOf(node: androidx.compose.ui.semantics.SemanticsNode): TextLayoutResult? {
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return null
        val results = mutableListOf<TextLayoutResult>()
        action(results)
        return results.firstOrNull()
    }

    private fun write(
        name: String,
        image: org.jetbrains.skia.Image,
    ) {
        val directory = System.getenv("KVIZIC_DESIGN_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        directory.mkdirs()
        File(directory, "$name.png").writeBytes(checkNotNull(image.encodeToData()).bytes)
    }

    private companion object {
        /** An iPhone SE, and the smallest Android phone in wide use, in dp at one pixel each. */
        val PHONES = listOf(375 to 667, 360 to 640)

        /** [words] repeated, cut to exactly [length] characters, never ending on a space. */
        fun textOf(
            words: String,
            length: Int,
        ): String {
            val long = generateSequence { words }.take(length / words.length + 2).joinToString(" ")
            val cut = long.take(length)
            return if (cut.last() == ' ') cut.dropLast(1) + "а" else cut
        }

        val QUESTION_TEXT = textOf("Који град на обали Јадранског мора има најстарију очувану градску апотеку", 120)
        val ANSWERS =
            listOf(
                textOf("Дубровник у Хрватској, са апотеком из четрнаестог века", 60),
                textOf("Котор у Црној Гори, са зидинама и тврђавом изнад града", 60),
                textOf("Сплит у Хрватској, са Диоклецијановом палатом у центру", 60),
                textOf("Пирин у Словенији, са старим венецијанским трговима", 60),
            )
        val EXPLANATION =
            textOf(
                "Апотека Мале браће у Дубровнику ради без прекида од 1317. године и једна је од најстаријих у Европи",
                KvizicApi.Limits.MAX_EXPLANATION_LENGTH,
            )

        val EIGHT =
            MEMBERS +
                listOf(
                    member("vuk", "Сиви Вук", "wolf", 5),
                    member("ris", "Брзи Рис", "lynx", 6),
                    member("jelen", "Јелен Јова", "deer", 7),
                )

        val FULL =
            inLobby(
                GamePhase.Revealing(
                    "game-1",
                    EIGHT.map { it.playerId },
                    Reveal(
                        index = 9,
                        count = 10,
                        questionId = "q1",
                        text = QUESTION_TEXT,
                        options = ANSWERS,
                        correct = 0,
                        results =
                            listOf(
                                AnswerResult("nina", 0, 98, 1_100, 1),
                                AnswerResult(YOU, 1, -31, 1_400, null),
                                AnswerResult("bojan", 0, 81, 3_000, 2),
                                AnswerResult("sova", 2, -12, 6_000, null),
                                AnswerResult("roda", null, 0, null, null),
                                AnswerResult("vuk", 0, 64, 7_200, 3),
                                AnswerResult("ris", 3, -8, 9_000, null),
                                AnswerResult("jelen", 1, -5, 13_000, null),
                            ),
                        standings =
                            listOf(
                                Standing("nina", 1328, 1, 8),
                                Standing("bojan", 1210, 2, 7),
                                Standing("vuk", 1045, 3, 7),
                                Standing("sova", 980, 4, 6),
                                Standing(YOU, 911, 5, 6),
                                Standing("ris", 870, 6, 5),
                                Standing("jelen", 655, 7, 4),
                                Standing("roda", 402, 8, 3),
                            ),
                        explanation = EXPLANATION,
                        last = false,
                    ),
                    deadline(6.seconds, 7.seconds),
                ),
                lobby = lobby(members = EIGHT),
            )
    }
}
