package io.ntole.kvizic.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.LocalContentColor
import io.ntole.kvizic.design.skin.Skin
import io.ntole.kvizic.design.skin.SkinType
import io.ntole.kvizic.design.skin.reserve
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * One answer to a question, a buzzer to hit: its letter, named by its [index] in the script shown (А Б
 * В Г, or more for more answers), and its [text], on the skin's tile for [state]. It sinks under the
 * finger and, once [AnswerTileState.LOCKED_IN], stays down; it lights up as the answer is revealed. All
 * of it moves in the draw alone.
 *
 * [onClick] makes it a button, while the question takes answers; null leaves it to be read. [pickers]
 * stands on the tile beside its letter: the avatars of those who picked it. [stateDescription] says the
 * state to a screen reader, in the screen's own words. [textSize] is the largest its answer is set at,
 * the skin's answer size when unspecified: [AnswerGrid] gives all its tiles one, so they are set alike.
 */
@Composable
fun AnswerTile(
    index: Int,
    text: String,
    state: AnswerTileState,
    modifier: Modifier = Modifier,
    arrangement: TileArrangement = TileArrangement.ROW,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    stateDescription: String? = null,
    pickers: (@Composable () -> Unit)? = null,
    textSize: TextUnit = TextUnit.Unspecified,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val type = KvizicTheme.type
    val part = skin.parts.tile
    val source = interactionSource ?: remember { MutableInteractionSource() }
    // How lit the letter is and how far the result's stamp has come in: read in the draw alone.
    val lit = remember { Animatable(state.lit) }
    val stamp = remember { Animatable(state.stamped) }
    LaunchedEffect(state) {
        launch { lit.animateTo(state.lit, tween(skin.motion.reveal, easing = FastOutSlowInEasing)) }
        stamp.animateTo(state.stamped, tween(skin.motion.reveal, easing = FastOutSlowInEasing))
    }
    val tap =
        if (onClick != null) {
            Modifier.clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
        } else {
            Modifier
        }
    val minHeight = if (arrangement == TileArrangement.ROW) space.tile.rowMinHeight else space.tile.gridMinHeight
    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = minHeight)
                .semantics(mergeDescendants = true) {
                    selected = state == AnswerTileState.LOCKED_IN
                    if (stateDescription != null) this.stateDescription = stateDescription
                }.then(tap)
                .raised(
                    look = part.surface(state, index),
                    depth = skin.depth,
                    motion = skin.motion,
                    interactionSource = source,
                    focus = skin.colors.focus,
                    focusWidth = space.strokeThin,
                ),
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(LocalContentColor provides part.content(state)) {
            val letter = KvizicTheme.script.letter(index)
            val mark: @Composable () -> Unit = {
                Box(
                    modifier =
                        Modifier
                            .size(space.tile.letterMark)
                            .drawWithContent {
                                with(part) { drawLetterMark(state, index, lit.value) }
                                drawContent()
                                // The result's stamp, over the letter, and moving with the face.
                                with(part) { drawStamp(state, stamp.value) }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    KvizicText(letter, style = type.letter, color = part.letterColor(state, index), maxLines = 1)
                }
            }
            val answer: @Composable (Modifier) -> Unit = { answerModifier ->
                KvizicText(
                    text = text,
                    modifier = answerModifier,
                    style = type.answer,
                    maxLines = ANSWER_LINES,
                    autoSize =
                        TextAutoSize.StepBased(
                            minFontSize = type.answerMin.style.fontSize,
                            maxFontSize =
                                if (textSize.isSpecified) {
                                    textSize.value
                                        .coerceIn(type.answerMin.style.fontSize.value, type.answer.style.fontSize.value)
                                        .sp
                                } else {
                                    type.answer.style.fontSize
                                },
                        ),
                )
            }
            // As tall as the tile is given, through its propagated minimum, and no taller: a tile wraps
            // its answer unless its grid gives it a height.
            when (arrangement) {
                TileArrangement.ROW -> {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = space.tile.padding, vertical = space.tile.rowPaddingVertical),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(space.md),
                    ) {
                        mark()
                        answer(Modifier.weight(1f))
                        // Those who picked it keep their own room, there or not: showing moves nothing.
                        Box(Modifier.width(space.tile.rowPickers), contentAlignment = Alignment.CenterEnd) {
                            pickers?.invoke()
                        }
                    }
                }

                TileArrangement.STACK -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(space.tile.padding),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            mark()
                            Spacer(Modifier.weight(1f))
                            pickers?.invoke()
                        }
                        Spacer(Modifier.height(space.sm))
                        answer(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/**
 * A question's answers, as many as it has, laid out by how many: two stacked tall across the width,
 * three in a column, four in a grid of two by two, and more in rows of two. Each tile takes an equal
 * share of the room the grid is given. [states] says where each answer stands; [onPick] makes each a
 * button; [pickers] stands on a tile what the screen puts there, by the answer's index.
 *
 * Four answers stand in a grid of two by two unless a column, each across the whole width, sets them
 * larger: one long answer takes them all to a column, where it has the width. And all are set at one
 * size, the largest the answer that needs the most room is whole at ([answerLayout]).
 */
@Composable
fun AnswerGrid(
    options: List<String>,
    modifier: Modifier = Modifier,
    states: List<AnswerTileState> = options.map { AnswerTileState.IDLE },
    onPick: ((index: Int) -> Unit)? = null,
    stateDescriptions: List<String?> = options.map { null },
    pickers: (@Composable (index: Int) -> Unit)? = null,
) {
    require(options.size >= MIN_ANSWERS) { "a question has at least $MIN_ANSWERS answers, not ${options.size}" }
    require(states.size == options.size) { "a state for each of the ${options.size} answers" }
    require(stateDescriptions.size == options.size) { "a description for each of the ${options.size} answers" }
    val space = KvizicTheme.space
    BoxWithConstraints(modifier) {
        val layout = answerLayout(options, constraints)
        val tile: @Composable (Int, TileArrangement, Modifier) -> Unit = { i, arrangement, tileModifier ->
            AnswerTile(
                index = i,
                text = options[i],
                state = states[i],
                modifier = tileModifier,
                arrangement = arrangement,
                onClick = onPick?.let { pick -> { pick(i) } },
                stateDescription = stateDescriptions[i],
                pickers = pickers?.let { slot -> { slot(i) } },
                textSize = layout.textSize,
            )
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(space.tile.gap)) {
            when {
                options.size == TRUE_OR_FALSE -> {
                    options.indices.forEach { i ->
                        tile(
                            i,
                            TileArrangement.ROW,
                            Modifier.fillMaxWidth().weight(1f).heightIn(min = space.tile.tallMinHeight),
                        )
                    }
                }

                !layout.inGrid -> {
                    options.indices.forEach { i ->
                        tile(i, TileArrangement.ROW, Modifier.fillMaxWidth().weight(1f))
                    }
                }

                else -> {
                    options.indices.chunked(COLUMNS).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(space.tile.gap),
                        ) {
                            row.forEach { i -> tile(i, TileArrangement.STACK, Modifier.weight(1f).fillMaxHeight()) }
                            // A last row of one keeps its tile the width of the others.
                            repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * How [options] stand inside [constraints]: four or more in a grid of two by two rather than in a column
 * across the width while there the answer that needs the most room is set at least as large as in the
 * column, each whole in at most [ANSWER_LINES] lines, and every answer at that one size, so a question's
 * answers are set alike. Short answers take the skin's full size either way, and keep the grid.
 *
 * Measured in the room each tile leaves its answer, the height too when it is bounded, in the skin's own
 * type and in the auto size's own steps.
 */
@Composable
private fun answerLayout(
    options: List<String>,
    constraints: Constraints,
): AnswerLayout {
    val skin = KvizicTheme.skin
    val type = KvizicTheme.type
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val boxes =
        remember(skin, constraints, options.size, density) {
            AnswerBoxes.of(skin, constraints, options.size, density)
        }
    return remember(options, boxes, type, measurer) {
        val inColumn = options.minOf { largestFit(measurer, type, it, boxes.column) }
        val inGrid =
            if (options.size >
                IN_A_COLUMN
            ) {
                options.minOf { largestFit(measurer, type, it, boxes.grid) }
            } else {
                -1f
            }
        val grid = inGrid >= inColumn
        AnswerLayout(grid, maxOf(if (grid) inGrid else inColumn, type.answerMin.style.fontSize.value).sp)
    }
}

/** Whether a question's answers stand in a grid, and the one size they are all set at. */
private data class AnswerLayout(
    val inGrid: Boolean,
    val textSize: TextUnit,
)

/** The room, in pixels, an answer has on a tile of the grid and on one of the column; no height is unbounded. */
private data class AnswerBoxes(
    val grid: AnswerBox,
    val column: AnswerBox,
) {
    companion object {
        fun of(
            skin: Skin,
            constraints: Constraints,
            count: Int,
            density: Density,
        ): AnswerBoxes =
            with(density) {
                val tile = skin.space.tile
                val (depthRight, depthBottom) = skin.depth.reserve(skin.parts.tile.surface(AnswerTileState.IDLE, 0))
                val width = constraints.maxWidth.toDp()
                val height = if (constraints.hasBoundedHeight) constraints.maxHeight.toDp() else null
                val rows = (count + COLUMNS - 1) / COLUMNS
                val gridTileWidth = (width - tile.gap * (COLUMNS - 1)) / COLUMNS - depthRight
                val rowAnswerWidth =
                    width - depthRight - tile.padding * 2 - tile.letterMark - skin.space.md * 2 - tile.rowPickers
                AnswerBoxes(
                    grid =
                        AnswerBox(
                            width = (gridTileWidth - tile.padding * 2).roundToPx(),
                            height =
                                height?.let {
                                    (
                                        (it - tile.gap * (rows - 1)) / rows - depthBottom - tile.padding * 2 -
                                            tile.letterMark - skin.space.sm
                                    ).roundToPx()
                                },
                        ),
                    column =
                        AnswerBox(
                            width = rowAnswerWidth.roundToPx(),
                            height =
                                height?.let {
                                    ((it - tile.gap * (count - 1)) / count - depthBottom - tile.rowPaddingVertical * 2)
                                        .roundToPx()
                                },
                        ),
                )
            }
    }
}

private data class AnswerBox(
    val width: Int,
    val height: Int?,
)

/**
 * The largest size, in sp and in the auto size's steps, at which [text] is set whole in [box], at most
 * [ANSWER_LINES] lines: from the skin's answer size down to its least, and 0 when not even that holds it.
 */
private fun largestFit(
    measurer: TextMeasurer,
    type: SkinType,
    text: String,
    box: AnswerBox,
): Float {
    val spec = type.answer
    val largest = spec.style.fontSize.value
    val least = type.answerMin.style.fontSize.value
    val set = spec.apply(text)

    fun fits(size: Float): Boolean {
        val laid =
            measurer.measure(
                text = set,
                style = spec.style.copy(fontSize = size.sp),
                constraints = Constraints(maxWidth = box.width.coerceAtLeast(1)),
            )
        return laid.lineCount <= ANSWER_LINES && (box.height == null || laid.size.height <= box.height)
    }

    fun sizeAt(steps: Int): Float = maxOf(largest - steps * AUTO_SIZE_STEP, least)
    if (fits(largest)) return largest
    if (!fits(least)) return 0f
    // The fewest steps down from the largest that fit, between one that does not and one that does.
    var tooFew = 0
    var enough = ceil((largest - least) / AUTO_SIZE_STEP).toInt()
    while (enough - tooFew > 1) {
        val mid = (tooFew + enough) / 2
        if (fits(sizeAt(mid))) enough = mid else tooFew = mid
    }
    return sizeAt(enough)
}

/** How lit a tile's letter stands in a state. */
private val AnswerTileState.lit: Float
    get() = if (this == AnswerTileState.LOCKED_IN || this == AnswerTileState.CORRECT) 1f else 0f

/** How far a tile's result's stamp is in, in a state. */
private val AnswerTileState.stamped: Float
    get() = if (this == AnswerTileState.CORRECT || this == AnswerTileState.WRONG) 1f else 0f

/** The fewest answers a question has, as the wire's `Limits.MIN_OPTIONS`. */
const val MIN_ANSWERS: Int = 2

private const val TRUE_OR_FALSE = 2
private const val IN_A_COLUMN = 3
private const val COLUMNS = 2
private const val ANSWER_LINES = 3

/** The step, in sp, [TextAutoSize.StepBased] sets a text smaller by, which is its own default. */
private const val AUTO_SIZE_STEP = 0.25f
