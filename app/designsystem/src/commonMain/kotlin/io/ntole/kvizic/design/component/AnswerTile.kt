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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
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
 * [onClick] makes it a button, while the question takes answers; null leaves it to be read. [pickers] are the
 * avatars of those who picked it: given [pickersInside], they stand on the tile's face at its end, in that
 * room beside the answer, and move with it; otherwise on its top edge, whole, in front of it.
 * [stateDescription] says the state to a screen reader, in the screen's own words. [textSize] is the largest
 * its answer is set at, the skin's answer size when unspecified: [AnswerGrid] gives all its tiles one, so they
 * are set alike.
 *
 * [appearing] has the tile come up as it is first composed, after [appearDelay] milliseconds: from a little
 * small and clear to its place, in its layer alone, as the answers open. Read once, when the tile is first
 * composed.
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
    pickersInside: PickersRoom? = null,
    textSize: TextUnit = TextUnit.Unspecified,
    appearing: Boolean = false,
    appearDelay: Int = 0,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val type = KvizicTheme.type
    val part = skin.parts.tile
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val shown = remember { Animatable(if (appearing) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (shown.value < 1f) {
            shown.animateTo(
                1f,
                tween(skin.motion.tileAppear.coerceAtLeast(1), delayMillis = appearDelay, easing = FastOutSlowInEasing),
            )
        }
    }
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
    val card: @Composable (Modifier) -> Unit = { cardModifier ->
        Card(
            index = index,
            text = text,
            state = state,
            modifier = cardModifier.defaultMinSize(minHeight = minHeight),
            arrangement = arrangement,
            tap = tap,
            source = source,
            stateDescription = stateDescription,
            textSize = textSize,
            pickers = pickers.takeIf { pickersInside != null },
            pickersRoom = pickersInside,
            lit = { lit.value },
            stamp = { stamp.value },
        )
    }
    PickersOver(
        pickers.takeIf { pickersInside == null },
        modifier.graphicsLayer {
            val p = shown.value
            alpha = p
            val scale = APPEAR_SCALE + (1f - APPEAR_SCALE) * p
            scaleX = scale
            scaleY = scale
        },
    ) { card(Modifier) }
}

/** How small a tile starts as it comes up. */
private const val APPEAR_SCALE = 0.86f

/**
 * The room those who picked an answer have on its tile's face: as wide as [width], in as many as [rows] rows,
 * which only a tile of the grid gives more than one of, under its letter.
 */
@Immutable
data class PickersRoom(
    val width: Dp,
    val rows: Int = 1,
)

/**
 * An answer tile's card: its surface, its letter and its answer, and [pickers] on its face where it has
 * [pickersRoom] for them: beside its letter on a tile of the grid, after its answer on one across the width.
 */
@Composable
private fun Card(
    index: Int,
    text: String,
    state: AnswerTileState,
    modifier: Modifier,
    arrangement: TileArrangement,
    tap: Modifier,
    source: MutableInteractionSource,
    stateDescription: String?,
    textSize: TextUnit,
    pickers: (@Composable () -> Unit)?,
    pickersRoom: PickersRoom?,
    lit: () -> Float,
    stamp: () -> Float,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val type = KvizicTheme.type
    val part = skin.parts.tile
    // What stands on the face turns with it as it settles into the state, never ahead of it.
    val content = rememberSettlingColor(part.content(state))
    val letterColor = rememberSettlingColor(part.letterColor(state, index))
    val markColor = rememberSettlingColor(part.markColor(state, index))
    Box(
        modifier =
            modifier
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
                                with(part) { drawLetterMark(state, index, lit(), markColor()) }
                                drawContent()
                                // The result's stamp, over the letter, and moving with the face.
                                with(part) { drawStamp(state, stamp()) }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    KvizicText(letter, style = type.letter, maxLines = 1, colorInDraw = letterColor)
                }
            }
            val answer: @Composable (Modifier) -> Unit = { answerModifier ->
                KvizicText(
                    text = text,
                    modifier = answerModifier,
                    style = type.answer,
                    maxLines = ANSWER_LINES,
                    colorInDraw = content,
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
                    }
                    // Over the answer's end, in the room its longest line leaves: the answer is laid out
                    // as if they were not there.
                    if (pickers != null && pickersRoom != null) {
                        Box(
                            Modifier.matchParentSize().padding(horizontal = space.tile.padding),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Box(Modifier.widthIn(max = pickersRoom.width)) { pickers() }
                        }
                    }
                }

                TileArrangement.STACK -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(space.tile.padding),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (pickers != null && pickersRoom != null) {
                            // Beside the letter, their first row level with it, and a second under it in the
                            // room the answer leaves above itself: the answer stands at the foot either way.
                            Box(Modifier.fillMaxWidth()) {
                                mark()
                                Box(
                                    Modifier.fillMaxWidth().padding(
                                        start = space.tile.letterMark + space.sm,
                                        top = (space.tile.letterMark - space.avatar.xs) / 2,
                                    ),
                                    contentAlignment = Alignment.TopEnd,
                                ) {
                                    CompositionLocalProvider(LocalAvatarRows provides pickersRoom.rows) { pickers() }
                                }
                            }
                        } else {
                            mark()
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
 * [card], and on its top edge those who picked its answer, at its end, in front of it: risen over the edge
 * by the skin's `pickersPeek`, about half of them, into the row gap above, the rest on the card.
 * They take none of its answer's room, so its answer keeps its size whether or not anyone has picked it. A
 * crowd of a whole room has the card's width. The card sinks under them, without them.
 */
@Composable
private fun PickersOver(
    pickers: (@Composable () -> Unit)?,
    modifier: Modifier,
    card: @Composable () -> Unit,
) {
    val skin = KvizicTheme.skin
    val tile = skin.space.tile
    val (depthRight, _) = skin.depth.reserve(skin.parts.tile.surface(AnswerTileState.IDLE, 0))
    Layout(
        content = {
            Box { pickers?.invoke() }
            card()
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val face = measurables[1].measure(constraints)
        val end = (depthRight + tile.padding).roundToPx()
        val picks = measurables[0].measure(Constraints(maxWidth = (face.width - end * 2).coerceAtLeast(0)))
        layout(face.width, face.height) {
            // Placed last, so drawn last: they stand in front of the card.
            face.place(0, 0)
            picks.place(face.width - end - picks.width, -tile.pickersPeek.roundToPx())
        }
    }
}

/**
 * A question's answers, as many as it has, laid out by how many: two stacked tall across the width,
 * three in a column, four in a grid of two by two, and more in rows of two. Each tile takes an equal
 * share of the room the grid is given. [states] says where each answer stands; [onPick] makes each a
 * button; [pickers] is what the screen puts with a tile, by the answer's index: those who picked it.
 *
 * Those who picked an answer stand on its tile's face where a [crowd] of them, the most who may pick one
 * answer, fits on every tile beside its answer, clear of it ([answerLayout]); where it does not fit on one
 * tile, they stand on every tile's top edge, in front of it, rising into the room over the grid
 * that a screen leaves it, [TileSizes.rowGap]. It is the question's, never the picks': no one moves as more
 * pick, and every tile shows them alike. A [crowd] of none keeps them on the edge.
 *
 * Four answers stand in a grid of two by two unless a column, each across the whole width, sets them
 * larger: one long answer takes them all to a column, where it has the width. And all are set at one
 * size, the largest the answer that needs the most room is whole at ([answerLayout]).
 *
 * [appearing] has the tiles come up one after another as they are first composed, as the answers open.
 * [places], kept above the screens a question's grids stand on, has a grid of the same [placesKey] that
 * comes after another take its tiles over, gliding from where they stood ([TilePlaces]).
 */
@Composable
fun AnswerGrid(
    options: List<String>,
    modifier: Modifier = Modifier,
    states: List<AnswerTileState> = options.map { AnswerTileState.IDLE },
    onPick: ((index: Int) -> Unit)? = null,
    stateDescriptions: List<String?> = options.map { null },
    pickers: (@Composable (index: Int) -> Unit)? = null,
    crowd: Int = 0,
    appearing: Boolean = false,
    places: TilePlaces? = null,
    placesKey: Any? = null,
) {
    require(options.size >= MIN_ANSWERS) { "a question has at least $MIN_ANSWERS answers, not ${options.size}" }
    require(states.size == options.size) { "a state for each of the ${options.size} answers" }
    require(stateDescriptions.size == options.size) { "a description for each of the ${options.size} answers" }
    val skin = KvizicTheme.skin
    val space = skin.space
    val grid =
        if (places != null && placesKey != null) {
            remember(places, placesKey) { GridOfTiles(places, placesKey) }
        } else {
            null
        }
    BoxWithConstraints(modifier) {
        val layout = answerLayout(options, constraints, crowd)
        val tile: @Composable (Int, TileArrangement, Modifier) -> Unit = { i, arrangement, tileModifier ->
            AnswerTile(
                index = i,
                text = options[i],
                state = states[i],
                modifier = if (grid != null) tileModifier.glidingTile(grid, i, skin.motion.stage) else tileModifier,
                arrangement = arrangement,
                onClick = onPick?.let { pick -> { pick(i) } },
                stateDescription = stateDescriptions[i],
                pickers = pickers?.let { slot -> { slot(i) } },
                pickersInside = layout.pickersRoom?.get(i),
                textSize = layout.textSize,
                appearing = appearing,
                appearDelay = i * skin.motion.tileStagger,
            )
        }
        // Between rows of tiles, room for those who picked one to stand on its top edge ([PickersOver]).
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(space.tile.rowGap)) {
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
 * Where a question's [count] answers will stand, while it is read and they are yet to come, so they come
 * where it showed: a tile's place for each of two or three, which always stand in a column ([AnswerGrid]), and
 * one place for four or more, which stand in a grid or in a column as their texts, not known yet, decide.
 */
@Composable
fun AnswerPlaces(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val space = KvizicTheme.space
    Column(modifier, verticalArrangement = Arrangement.spacedBy(space.tile.rowGap)) {
        repeat(if (count <= IN_A_COLUMN) count else 1) {
            Panel(Modifier.fillMaxWidth().weight(1f), kind = PanelKind.EMPTY) {}
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
 *
 * Those who picked an answer have the room on a tile's face that its answer never takes: beside the letter
 * on a tile of the grid, past the answer's longest line, at the size it is set at, on one across the width.
 * They stand there when a [crowd] of them, closed up as far as the skin lets a stack close, fits it on every
 * tile. A tile of the grid gives them a second row under the first where every answer, at its size, still
 * fits under both, so a crowd stands in two rows rather than closed up in one.
 */
@Composable
private fun answerLayout(
    options: List<String>,
    constraints: Constraints,
    crowd: Int,
): AnswerLayout {
    val skin = KvizicTheme.skin
    val type = KvizicTheme.type
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val boxes =
        remember(skin, constraints, options.size, density) {
            AnswerBoxes.of(skin, constraints, options.size, density)
        }
    val measures = remember(type, measurer) { AnswerMeasures(measurer, type) }
    return remember(options, boxes, measures, crowd, skin, density) {
        val inColumn = options.minOf { measures.largestFit(it, boxes.column) }
        val inGrid =
            if (options.size >
                IN_A_COLUMN
            ) {
                options.minOf { measures.largestFit(it, boxes.grid) }
            } else {
                -1f
            }
        val grid = inGrid >= inColumn
        val size = maxOf(if (grid) inGrid else inColumn, type.answerMin.style.fontSize.value)
        val crowdIn = { count: Int -> with(density) { crowdWidth(skin.space, count) } }
        val pickersRoom =
            when {
                crowd <= 0 -> {
                    null
                }

                grid -> {
                    val underAnswers = boxes.grid.height?.let { it - boxes.secondRow }
                    val twoRows =
                        crowd > 1 && crowdIn((crowd + 1) / 2) <= boxes.besideLetter && underAnswers != null &&
                            options.all { measures.height(it, size, boxes.grid) <= underAnswers }
                    val width = with(density) { boxes.besideLetter.toDp() }
                    when {
                        twoRows -> List(options.size) { PickersRoom(width, rows = 2) }
                        crowdIn(crowd) <= boxes.besideLetter -> List(options.size) { PickersRoom(width) }
                        else -> null
                    }
                }

                else -> {
                    val rooms =
                        options.map { boxes.column.width - measures.widest(it, size, boxes.column) - boxes.afterAnswer }
                    val fits = rooms.all { it >= crowdIn(crowd) }
                    if (fits) rooms.map { with(density) { PickersRoom(it.toDp()) } } else null
                }
            }
        AnswerLayout(grid, size.sp, pickersRoom)
    }
}

/**
 * Whether [options] can each be set whole in an [AnswerGrid] given a box, in a column or a grid, at no less
 * than the skin's least answer size: for a screen that must choose what else to show beside the answers, as
 * the reveal does, before it hands the grid its room.
 */
@Composable
fun rememberAnswersFit(options: List<String>): (Constraints) -> Boolean {
    val skin = KvizicTheme.skin
    val type = KvizicTheme.type
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(options, skin, type, measurer, density) {
        // Asked at many heights, as the reveal finds the least room its answers take: each answer is measured
        // once a width, and every height after that is a comparison.
        val measures = AnswerMeasures(measurer, type)
        val fits: (Constraints) -> Boolean = { constraints ->
            val boxes = AnswerBoxes.of(skin, constraints, options.size, density)
            options.all { measures.whole(it, boxes.column) } ||
                (options.size > IN_A_COLUMN && options.all { measures.whole(it, boxes.grid) })
        }
        fits
    }
}

/**
 * Whether a question's answers stand in a grid, the one size they are all set at, and the room those who
 * picked one have on each tile's face, by index; null where they stand behind the tiles.
 */
private data class AnswerLayout(
    val inGrid: Boolean,
    val textSize: TextUnit,
    val pickersRoom: List<PickersRoom>?,
)

/**
 * The room, in pixels, an answer has on a tile of the grid and on one of the column, no height is unbounded;
 * and on a tile's face, the room beside a grid tile's letter, how much more than the letter's row two rows of
 * those who picked it take, and the gap a column tile keeps after its answer.
 */
private data class AnswerBoxes(
    val grid: AnswerBox,
    val column: AnswerBox,
    val besideLetter: Int,
    val secondRow: Int,
    val afterAnswer: Int,
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
                val rowAnswerWidth = width - depthRight - tile.padding * 2 - tile.letterMark - skin.space.md
                AnswerBoxes(
                    grid =
                        AnswerBox(
                            width = (gridTileWidth - tile.padding * 2).roundToPx(),
                            height =
                                height?.let {
                                    (
                                        (it - tile.rowGap * (rows - 1)) / rows - depthBottom - tile.padding * 2 -
                                            tile.letterMark - skin.space.sm
                                    ).roundToPx()
                                },
                        ),
                    column =
                        AnswerBox(
                            width = rowAnswerWidth.roundToPx(),
                            height =
                                height?.let {
                                    (
                                        (it - tile.rowGap * (count - 1)) / count - depthBottom -
                                            tile.rowPaddingVertical * 2
                                    ).roundToPx()
                                },
                        ),
                    besideLetter = (gridTileWidth - tile.padding * 2 - tile.letterMark - skin.space.sm).roundToPx(),
                    secondRow =
                        (
                            (tile.letterMark - skin.space.avatar.xs) / 2 + skin.space.avatar.xs * 2 + skin.space.xxs -
                                tile.letterMark
                        ).roundToPx(),
                    afterAnswer = skin.space.md.roundToPx(),
                )
            }
    }
}

private data class AnswerBox(
    val width: Int,
    val height: Int?,
)

/**
 * The answers' measures in the skin's [type]: each answer laid out once at a size and a width, its lines, its
 * height and its longest line kept, since a box's height only says whether those fit.
 */
private class AnswerMeasures(
    private val measurer: TextMeasurer,
    private val type: SkinType,
) {
    private val laid = HashMap<Laying, Laid>()

    /** Whether [text] is set whole in [box] at [size], in sp: in at most [ANSWER_LINES] lines, and its height. */
    fun fits(
        text: String,
        size: Float,
        box: AnswerBox,
    ): Boolean {
        val (lines, height) = laidIn(text, size, box)
        return lines <= ANSWER_LINES && (box.height == null || height <= box.height)
    }

    /** How tall, in pixels, [text] is set in [box]'s width at [size], in sp. */
    fun height(
        text: String,
        size: Float,
        box: AnswerBox,
    ): Int = laidIn(text, size, box).height

    /** How far, in pixels, [text]'s longest line reaches across [box] at [size], in sp. */
    fun widest(
        text: String,
        size: Float,
        box: AnswerBox,
    ): Int = laidIn(text, size, box).widest

    private fun laidIn(
        text: String,
        size: Float,
        box: AnswerBox,
    ): Laid {
        val width = box.width.coerceAtLeast(1)
        return laid.getOrPut(Laying(text, size, width)) {
            val spec = type.answer
            val result =
                measurer.measure(
                    text = spec.apply(text),
                    style = spec.style.copy(fontSize = size.sp),
                    constraints = Constraints(maxWidth = width),
                )
            Laid(
                result.lineCount,
                result.size.height,
                (0 until result.lineCount).maxOf { ceil(result.getLineRight(it)).toInt() },
            )
        }
    }

    /** Whether [text] is set whole in [box] at the skin's least answer size. */
    fun whole(
        text: String,
        box: AnswerBox,
    ): Boolean = fits(text, type.answerMin.style.fontSize.value, box)

    /**
     * The largest size, in sp and in the auto size's steps, at which [text] is set whole in [box]: from the
     * skin's answer size down to its least, and 0 when not even that holds it.
     */
    fun largestFit(
        text: String,
        box: AnswerBox,
    ): Float {
        val largest = type.answer.style.fontSize.value
        val least = type.answerMin.style.fontSize.value

        fun sizeAt(steps: Int): Float = maxOf(largest - steps * AUTO_SIZE_STEP, least)
        if (fits(text, largest, box)) return largest
        if (!fits(text, least, box)) return 0f
        // The fewest steps down from the largest that fit, between one that does not and one that does.
        var tooFew = 0
        var enough = ceil((largest - least) / AUTO_SIZE_STEP).toInt()
        while (enough - tooFew > 1) {
            val mid = (tooFew + enough) / 2
            if (fits(text, sizeAt(mid), box)) enough = mid else tooFew = mid
        }
        return sizeAt(enough)
    }

    private data class Laying(
        val text: String,
        val size: Float,
        val width: Int,
    )

    private data class Laid(
        val lines: Int,
        val height: Int,
        val widest: Int,
    )
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
