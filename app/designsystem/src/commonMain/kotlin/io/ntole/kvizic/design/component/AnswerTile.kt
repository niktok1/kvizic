package io.ntole.kvizic.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.LocalContentColor
import kotlinx.coroutines.launch

/**
 * One answer to a question, a buzzer to hit: its letter, named by its [index] in the script shown (А Б
 * В Г, or more for more answers), and its [text], on the skin's tile for [state]. It sinks under the
 * finger and, once [AnswerTileState.LOCKED_IN], stays down; it lights up as the answer is revealed. All
 * of it moves in the draw alone.
 *
 * [onClick] makes it a button, while the question takes answers; null leaves it to be read. [pickers]
 * stands on the tile beside its letter: the avatars of those who picked it. [stateDescription] says the
 * state to a screen reader, in the screen's own words.
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
                            maxFontSize = type.answer.style.fontSize,
                        ),
                )
            }
            // As tall as the tile is given, through its propagated minimum, and no taller: a tile wraps
            // its answer unless its grid gives it a height.
            when (arrangement) {
                TileArrangement.ROW -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(space.tile.padding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(space.md),
                    ) {
                        mark()
                        answer(Modifier.weight(ANSWER_SHARE))
                        // Those who picked it take what the answer leaves, never the answer's own room.
                        if (pickers != null) Box(Modifier.weight(1f, fill = false)) { pickers() }
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
        )
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(space.tile.gap)) {
        when (options.size) {
            TRUE_OR_FALSE -> {
                options.indices.forEach { i ->
                    tile(
                        i,
                        TileArrangement.ROW,
                        Modifier.fillMaxWidth().weight(1f).heightIn(min = space.tile.tallMinHeight),
                    )
                }
            }

            IN_A_COLUMN -> {
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

/** How much more of a row tile's width its answer may take than those who picked it. */
private const val ANSWER_SHARE = 2f
