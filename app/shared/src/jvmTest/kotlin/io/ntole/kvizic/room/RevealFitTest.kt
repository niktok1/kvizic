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
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.core.domain.lobby.Reveal
import io.ntole.kvizic.core.domain.lobby.Standing
import io.ntole.kvizic.design.component.Stage
import io.ntole.kvizic.design.component.signed
import io.ntole.kvizic.design.skin.Skins
import io.ntole.kvizic.everyNode
import io.ntole.kvizic.isCut
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.renderSettled
import io.ntole.kvizic.texts
import io.ntole.kvizic.theme.GameTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The reveal at its fullest on the smallest phones, in every skin: eight players, the longest question,
 * four of the longest answers and the longest explanation the rules allow. The explanation and every
 * answer are set whole, every answer stands on screen apart from the rest, the question is recalled, and
 * the player's own line, with their points for the question, is on the board; at a phone's large font
 * (130%) as well, where the board, when there is no room for its line, gives way whole rather than falling
 * off the screen, and a usual question keeps it.
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

    /** At the wire's own limits the answers keep the room they need, and the board may give way. */
    @Test
    fun `a full reveal at the wire's limits keeps its answers whole`() {
        assertFits(full(ANSWERS), ANSWERS, "wire", Board.MAY_GIVE_WAY)
    }

    /** At the content style's limits, answers of 40, the player's own line stands on the board as well. */
    @Test
    fun `a full reveal at the content style's limits shows the player's line on the board too`() {
        assertFits(full(HOUSE_ANSWERS), HOUSE_ANSWERS, "house", Board.SHOWN)
    }

    /**
     * At a large font the content style's longest leave a small phone no room for the board's line: it gives
     * way whole, the player's points still in the bar, and everything else is set whole.
     */
    @Test
    fun `a full reveal at a large font keeps its words whole and its board whole or gone`() {
        assertFits(full(HOUSE_ANSWERS), HOUSE_ANSWERS, "house", Board.WHOLE_OR_GONE, LARGE_FONT)
    }

    /**
     * A usual question, answers and explanation keep the board at a large font, the recap in its two lines, on an
     * iPhone SE and on a phone 360 wide of today's height; on one 640 high too the board gives way whole.
     */
    @Test
    fun `a usual reveal at a large font shows the player's line on the board`() {
        assertFits(usual(), USUAL_ANSWERS, "usual", Board.SHOWN, LARGE_FONT, USUAL_QUESTION, listOf(SE, TALL))
        assertFits(usual(), USUAL_ANSWERS, "usual", Board.WHOLE_OR_GONE, LARGE_FONT, USUAL_QUESTION, listOf(SMALL))
    }

    private enum class Board { SHOWN, WHOLE_OR_GONE, MAY_GIVE_WAY }

    private fun assertFits(
        state: LobbySessionState.InLobby,
        answers: List<String>,
        name: String,
        board: Board,
        fontScale: Float = 1f,
        question: String = QUESTION_TEXT,
        phones: List<Pair<Int, Int>> = PHONES,
    ) {
        val failures = mutableListOf<String>()
        val explanation = checkNotNull((state.phase as GamePhase.Revealing).reveal.explanation)
        Skins.ALL.forEach { skin ->
            phones.forEach { (width, height) ->
                val where = "${skin.id} at $width by $height, font $fontScale"
                val scene =
                    ImageComposeScene(width = width, height = height, density = Density(1f, fontScale)) {
                        GameTheme(Language.DEFAULT, skin) {
                            Stage(Modifier.fillMaxSize()) {
                                RoomScreen(state, TOPICS, note = null, bursts = emptyMap(), actions = RoomActions())
                            }
                        }
                    }
                try {
                    write("reveal-$name-${skin.id}-${width}x$height-$fontScale", scene.renderSettled())
                    // Beside its explanation the question is recalled in two lines: shown, not whole.
                    val recap = scene.everyNode().firstOrNull { node -> node.texts.any { it.equals(question, true) } }
                    val recapLines = recap?.let { layoutOf(it)?.lineCount } ?: 0
                    if (recap == null) {
                        failures += "$where: the question is not recalled"
                    } else if (recapLines < RECAP_LINES) {
                        failures += "$where: the question is recalled in $recapLines lines"
                    }
                    (listOf(explanation) + answers).forEach { text ->
                        val node = scene.everyNode().firstOrNull { node -> node.texts.any { it.equals(text, true) } }
                        when {
                            node == null -> failures += "$where: \"${text.take(24)}…\" is not shown"
                            layoutOf(node)?.isCut == true -> failures += "$where: \"${text.take(24)}…\" is cut"
                            node.boundsInRoot.bottom > height -> failures += "$where: \"${text.take(24)}…\" runs off"
                        }
                    }
                    val tiles = answers.mapNotNull { answer -> scene.everyNode().firstOrNull { answer in it.texts } }
                    tiles.forEachIndexed { i, tile ->
                        tiles.drop(i + 1).forEach { other ->
                            if (tile.boundsInRoot.overlaps(other.boundsInRoot)) {
                                failures +=
                                    "$where: two answers overlap"
                            }
                        }
                    }
                    // Laid out on the screen: a board the reveal has no room for is composed to be asked its least,
                    // and never placed.
                    val points = scene.everyNode().filter { it.layoutInfo.isPlaced && signed(-31) in it.texts }
                    val whole = points.filter { it.boundsInRoot.height >= it.size.height - HALF_PIXEL }
                    when (board) {
                        Board.SHOWN -> {
                            if (whole.none { it.boundsInRoot.bottom <= height }) {
                                failures += "$where: the player's points are not on the board"
                            }
                            // The player's place, 5., stands whole beside them.
                            val place = scene.everyNode().filter { it.layoutInfo.isPlaced && "5." in it.texts }
                            if (place.isEmpty() || place.any { layoutOf(it)?.isCut != false }) {
                                failures += "$where: the player's place is cut"
                            }
                        }

                        Board.WHOLE_OR_GONE -> {
                            if (points.size != whole.size) failures += "$where: the player's line is cut"
                        }

                        Board.MAY_GIVE_WAY -> {
                            Unit
                        }
                    }
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
        val SE = 375 to 667
        val SMALL = 360 to 640
        val PHONES = listOf(SE, SMALL)

        /** A phone 360 wide of today's height, as most sold are. */
        val TALL = 360 to 760

        /** A phone's large font, 130%, a common setting. */
        const val LARGE_FONT = 1.3f

        /** The lines the question is recalled in beside its explanation. */
        const val RECAP_LINES = 2
        const val HALF_PIXEL = 0.5f
        const val HOUSE_ANSWER_LENGTH = 40

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

        /** The content style's longest answers, 40 characters each. */
        val HOUSE_ANSWERS = ANSWERS.map { textOf(it, HOUSE_ANSWER_LENGTH) }

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

        /** A question as long as most, answers of a few words and an explanation of a sentence or two. */
        const val USUAL_QUESTION = "Која је прва модерна олимпијада одржана у Атини, и које године?"
        val USUAL_ANSWERS = listOf("1896. године", "1900. године", "1912. године", "1924. године")
        const val USUAL_EXPLANATION =
            "Прве модерне олимпијске игре одржане су у Атини 1896. године, на иницијативу Пјера де Кубертена."

        fun usual() = full(USUAL_ANSWERS, USUAL_QUESTION, USUAL_EXPLANATION)

        fun full(
            options: List<String>,
            text: String = QUESTION_TEXT,
            explanation: String = EXPLANATION,
        ) = inLobby(
            GamePhase.Revealing(
                "game-1",
                EIGHT.map { it.playerId },
                Reveal(
                    index = 9,
                    count = 10,
                    questionId = "q1",
                    text = text,
                    options = options,
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
                    explanation = explanation,
                    last = false,
                ),
                deadline(6.seconds, 7.seconds),
            ),
            lobby = lobby(members = EIGHT),
        )
    }
}
