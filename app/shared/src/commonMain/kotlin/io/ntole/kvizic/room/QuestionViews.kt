package io.ntole.kvizic.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.analytics.tappedAt
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.Lobby
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.core.domain.lobby.Standing
import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.design.component.AnswerGrid
import io.ntole.kvizic.design.component.AnswerTileState
import io.ntole.kvizic.design.component.AvatarChip
import io.ntole.kvizic.design.component.AvatarStack
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.Chip
import io.ntole.kvizic.design.component.FlapSize
import io.ntole.kvizic.design.component.FlipNumber
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.Panel
import io.ntole.kvizic.design.component.PanelKind
import io.ntole.kvizic.design.component.QuestionText
import io.ntole.kvizic.design.component.QuestionTimer
import io.ntole.kvizic.design.component.ScoreRow
import io.ntole.kvizic.design.component.StageIconButton
import io.ntole.kvizic.design.component.StandingsBoard
import io.ntole.kvizic.design.component.TimerPhase
import io.ntole.kvizic.design.component.TimerSize
import io.ntole.kvizic.design.component.WaitingFor
import io.ntole.kvizic.design.component.rememberAnswersFit
import io.ntole.kvizic.design.component.signed
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.LocalLanguage
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.fill
import kotlin.math.roundToInt

/**
 * A question of the game under way, read alone ([GamePhase.Reading]) and then answered
 * ([GamePhase.Answering]): the round, the clock and the player's points over it, its topic on its card's edge,
 * its answers under it, and who it still waits for. A member who joined during the game watches it: their
 * answers take no tap.
 */
@Composable
internal fun QuestionScreen(
    state: LobbySessionState.InLobby,
    phase: GamePhase,
    topics: List<Topic>,
    actions: RoomActions,
    onLeave: () -> Unit = {},
) {
    val words = LocalStrings.current.game
    val language = LocalLanguage.current
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val lobby = state.lobby
    val totalMillis = lobby.settings.secondsPerQuestion * MILLIS_PER_SECOND
    when (phase) {
        is GamePhase.Reading -> {
            val timer =
                remember(phase.deadline) {
                    TimerPhase.Reading(
                        totalMillis = totalMillis,
                        readMillis =
                            phase.deadline.total.inWholeMilliseconds
                                .toInt(),
                        readLeftMillis =
                            phase.deadline
                                .remaining()
                                .inWholeMilliseconds
                                .toInt(),
                    )
                }
            Page {
                RoundBar(
                    phase.question.index,
                    phase.question.count,
                    phase.standings,
                    state.you,
                    onLeave = onLeave,
                )
                Spacer(Modifier.height(space.xl))
                QuestionTimer(
                    timer,
                    Modifier.align(Alignment.CenterHorizontally),
                    contentDescription = words.secondsToAnswer.fill(lobby.settings.secondsPerQuestion),
                )
                Spacer(Modifier.height(space.xl))
                QuestionCard(phase.question.topic, topics, padding = space.xl) {
                    QuestionText(shown(phase.question.text), Modifier.fillMaxWidth(), reading = true)
                }
                Spacer(Modifier.height(space.xl))
                // Where the answers will stand, so nothing moves when they come.
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.tile.rowGap)) {
                    (0 until phase.question.optionCount).chunked(2).forEach { row ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(space.tile.gap)) {
                            row.forEach { _ -> Panel(Modifier.weight(1f).fillMaxSize(), kind = PanelKind.EMPTY) {} }
                        }
                    }
                }
                Spacer(Modifier.height(space.md))
                KvizicText(
                    words.answersComing,
                    Modifier.fillMaxWidth(),
                    style = type.label,
                    color = colors.onPageMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }

        is GamePhase.Answering -> {
            val timer =
                remember(phase.deadline) {
                    TimerPhase.Running(
                        totalMillis =
                            phase.deadline.total.inWholeMilliseconds
                                .toInt(),
                        leftMillis =
                            phase.deadline
                                .remaining()
                                .inWholeMilliseconds
                                .toInt(),
                    )
                }
            val playing = state.you in phase.players
            val myPick = phase.myPick
            val picks: Map<String, Int> = if (myPick != null) phase.picks + (state.you to myPick) else phase.picks
            val answer = tappedAt("question.answer", withLockInHaptic(actions.answer))
            // The tiles come up one after another as the answers open, once a question: not again after a rotation.
            val appearing = rememberSaveable(phase.gameId, phase.question.index) { mutableStateOf(true) }
            LaunchedEffect(phase.gameId, phase.question.index) { appearing.value = false }
            Page {
                RoundBar(
                    phase.question.index,
                    phase.question.count,
                    phase.standings,
                    state.you,
                    timer = timer,
                    timerDescription = words.secondsToAnswer.fill(lobby.settings.secondsPerQuestion),
                    onLeave = onLeave,
                )
                Spacer(Modifier.height(space.md))
                QuestionCard(phase.question.topic, topics, padding = space.lg) {
                    QuestionText(shown(phase.question.text), Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(space.lg))
                AnswerGrid(
                    options = phase.options.map { shown(it) },
                    modifier = Modifier.weight(1f),
                    states =
                        phase.options.indices.map { i ->
                            when (myPick) {
                                null -> AnswerTileState.IDLE
                                i -> AnswerTileState.LOCKED_IN
                                else -> AnswerTileState.DIMMED
                            }
                        },
                    onPick = if (playing && myPick == null) answer else null,
                    stateDescriptions = phase.options.indices.map { i -> if (i == myPick) words.yourAnswer else null },
                    pickers =
                        if (picks.isEmpty()) {
                            null
                        } else {
                            { option -> Pickers(lobby, picks.filterValues { it == option }.keys.toList()) }
                        },
                    appearing = appearing.value,
                )
                Spacer(Modifier.height(space.md))
                Strip {
                    if (playing) {
                        val waiting =
                            phase.players.filter {
                                it !in phase.answered &&
                                    it != state.you.takeIf { myPick != null }
                            }
                        val members = waiting.mapNotNull { lobby.member(it) }.sortedBy { it.seat }
                        WaitingFor(
                            members.map { AvatarChip(it.avatar, it.seat) },
                            Modifier.fillMaxWidth(),
                            contentDescription =
                                words.waitingFor.fill(
                                    members.joinToString { shownIn(it.name, language) },
                                ),
                        )
                    } else {
                        KvizicText(
                            words.spectating,
                            style = type.caption,
                            color = colors.onRaisedMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        else -> {
            Unit
        }
    }
}

/**
 * A question revealed: the right answer lit, the player's own stamped wrong where it was, who picked each,
 * the first right ones in their order, and every player's standing on one board, the lines sliding from
 * where they stood to where the question put them, a line draining along its foot to the next question.
 * The flag to report the question stands in the question's corner, out of the way of the tiles.
 */
@Composable
internal fun RevealScreen(
    state: LobbySessionState.InLobby,
    phase: GamePhase.Revealing,
    topics: List<Topic>,
    onReport: (questionId: String) -> Unit = {},
    onLeave: () -> Unit = {},
) {
    val words = LocalStrings.current.game
    val language = LocalLanguage.current
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val reveal = phase.reveal
    val lobby = state.lobby
    val mine = reveal.results.firstOrNull { it.playerId == state.you }
    val seconds by secondsLeft(phase.next)
    RevealHaptic(reveal.questionId, right = mine?.option?.let { it == reveal.correct })
    // The tiles stand a frame as they were answered, then light up and stamp: the reveal's moment, once a
    // question, and not again after a rotation.
    var revealed by rememberSaveable(reveal.questionId) { mutableStateOf(false) }
    LaunchedEffect(reveal.questionId) {
        if (!revealed) {
            withFrameNanos {}
            revealed = true
        }
    }
    Page {
        RoundBar(
            reveal.index,
            reveal.count,
            reveal.standings,
            state.you,
            timer = TimerPhase.Stopped(lobby.settings.secondsPerQuestion * MILLIS_PER_SECOND, 0),
            onLeave = onLeave,
        )
        Spacer(Modifier.height(space.md))
        RevealBody(
            fits = rememberAnswersFit(reveal.options.map { shown(it) }),
            gap = space.md,
            boardLeast = space.avatar.xs * BOARD_LEAST_LINES + space.md * 2 + space.sm + space.xs,
            modifier = Modifier.weight(1f),
            panel = {
                Panel(Modifier.fillMaxWidth(), kind = PanelKind.SCREEN, padding = space.lg) {
                    Box {
                        Column(
                            Modifier.padding(horizontal = space.lg),
                            verticalArrangement = Arrangement.spacedBy(space.sm),
                        ) {
                            val explanation = reveal.explanation
                            if (explanation == null) {
                                QuestionText(
                                    shown(reveal.text),
                                    Modifier.fillMaxWidth(),
                                    recap = true,
                                    textAlign = TextAlign.Center,
                                )
                            } else {
                                // With an explanation to read, the question it explains, read whole a moment
                                // ago, is recalled in a line or two, and a screen reader still hears it whole.
                                KvizicText(
                                    shown(reveal.text),
                                    Modifier.fillMaxWidth(),
                                    style = type.bodyStrong,
                                    textAlign = TextAlign.Center,
                                    maxLines = RECAP_LINES,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            reveal.explanation?.let { explanation ->
                                KvizicText(
                                    shown(explanation),
                                    style = type.caption,
                                    color = colors.onRaisedMuted,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        StageIconButton(
                            KvizicIcons.Flag,
                            contentDescription = words.reportQuestion,
                            onClick = tapped("reveal.report") { onReport(reveal.questionId) },
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = space.md, y = -space.md),
                            kind = ButtonKind.QUIET,
                            small = true,
                        )
                    }
                }
            },
            grid = { gridModifier ->
                AnswerGrid(
                    options = reveal.options.map { shown(it) },
                    modifier = gridModifier,
                    states =
                        reveal.options.indices.map { i ->
                            when {
                                !revealed && mine?.option == null -> {
                                    AnswerTileState.IDLE
                                }

                                !revealed -> {
                                    if (i ==
                                        mine?.option
                                    ) {
                                        AnswerTileState.LOCKED_IN
                                    } else {
                                        AnswerTileState.DIMMED
                                    }
                                }

                                i == reveal.correct -> {
                                    AnswerTileState.CORRECT
                                }

                                i == mine?.option -> {
                                    AnswerTileState.WRONG
                                }

                                else -> {
                                    AnswerTileState.DIMMED
                                }
                            }
                        },
                    stateDescriptions =
                        reveal.options.indices.map { i ->
                            when {
                                i == reveal.correct -> words.rightAnswer
                                i == mine?.option -> words.wrongAnswer
                                else -> null
                            }
                        },
                    pickers = { option ->
                        val picked =
                            reveal.results.filter { it.option == option }.sortedWith(
                                compareBy(nullsLast()) { it.order },
                            )
                        if (picked.isNotEmpty()) {
                            AvatarStack(
                                picked.mapNotNull { result ->
                                    lobby
                                        .member(
                                            result.playerId,
                                        )?.let { AvatarChip(it.avatar, it.seat, order = result.order) }
                                },
                            )
                        }
                    },
                )
            },
            board = {
                StandingsBoard(
                    rows =
                        boardRows(
                            lobby,
                            reveal.standings,
                            reveal.results.associate { it.playerId to it.points },
                            state.you,
                            language,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                    reorderKey = reveal.questionId,
                    timeLeft = phase.next::fractionLeft,
                    timeDescription = (if (reveal.last) words.resultsIn else words.nextQuestionIn).fill(seconds),
                )
            },
        )
    }
}

/**
 * Every player's line on the reveal's board, in their new order, each saying how many places the question
 * moved it: where it stood is its order by the score before the question, the points it gave taken back,
 * ties kept in the new order.
 */
internal fun boardRows(
    lobby: Lobby,
    standings: List<Standing>,
    points: Map<String, Int>,
    you: String,
    language: Language,
): List<ScoreRow> {
    val ranked = standings.sortedBy { it.rank }
    val before =
        ranked
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<Standing>> {
                    it.value.score - (points[it.value.playerId] ?: 0)
                }.thenBy { it.index },
            ).map { it.value.playerId }
    return ranked.mapIndexedNotNull { index, standing ->
        val member = lobby.member(standing.playerId) ?: return@mapIndexedNotNull null
        ScoreRow(
            place = standing.rank,
            name = shownIn(member.name, language),
            avatarId = member.avatar,
            seat = member.seat,
            total = standing.score,
            delta = points[standing.playerId],
            host = member.playerId == lobby.host,
            own = member.playerId == you,
            moved = before.indexOf(standing.playerId) - index,
            id = member.playerId,
        )
    }
}

/** Those who picked an answer, in their seats' order, behind its tile. */
@Composable
private fun Pickers(
    lobby: Lobby,
    playerIds: List<String>,
) {
    val members = playerIds.mapNotNull { lobby.member(it) }.sortedBy { it.seat }
    if (members.isNotEmpty()) AvatarStack(members.map { AvatarChip(it.avatar, it.seat) })
}

/**
 * The bar over a question: the way out and the round in numbers alone („3 / 10“, said in words), the clock in
 * the middle of the screen, and the player's points on flaps. The two sides share the width alike, so the
 * clock stands in the middle and the points have half of what it leaves, four digits whole.
 */
@Composable
private fun RoundBar(
    index: Int,
    count: Int,
    standings: List<Standing>,
    you: String,
    timer: TimerPhase? = null,
    timerDescription: String? = null,
    onLeave: () -> Unit = {},
) {
    val words = LocalStrings.current.game
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            StageIconButton(
                KvizicIcons.Leave,
                contentDescription = words.leave,
                onClick = tapped("question.leave", onClick = onLeave),
                kind = ButtonKind.QUIET,
                small = true,
            )
            Spacer(Modifier.width(space.xs))
            KvizicText(
                "${index + 1} / $count",
                Modifier.clearAndSetSemantics { contentDescription = words.questionOf.fill(index + 1, count) },
                style = type.label,
                color = colors.onPageAccent,
                maxLines = 1,
            )
        }
        if (timer != null) QuestionTimer(timer, size = TimerSize.SMALL, contentDescription = timerDescription)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            KvizicText(words.points, style = type.label, color = colors.onPageMuted)
            Spacer(Modifier.height(space.xs))
            FlipNumber(standings.firstOrNull { it.playerId == you }?.score ?: 0, size = FlapSize.MEDIUM)
        }
    }
}

/**
 * The question's card, its topic, when it has one, a tab on its top edge at the start, half over the
 * border: the card's width holds the longest topic, and the tab takes none of the room the question and its
 * answers share. It stands in by the same space whatever the card's [padding], so it stays put as the read
 * question rises into its answers.
 */
@Composable
private fun QuestionCard(
    topic: String?,
    topics: List<Topic>,
    padding: Dp,
    content: @Composable () -> Unit,
) {
    val space = KvizicTheme.space
    Box(Modifier.fillMaxWidth()) {
        Panel(Modifier.fillMaxWidth(), kind = PanelKind.SCREEN, padding = padding) { content() }
        if (topic != null) {
            Chip(
                topicNameOf(topic, topics, LocalLanguage.current),
                Modifier.offset(x = space.lg, y = -space.chip.height / 2),
            )
        }
    }
}

/** The strip under the answers, as tall whatever it holds. */
@Composable
private fun Strip(content: @Composable () -> Unit) {
    val space = KvizicTheme.space
    Panel(Modifier.fillMaxWidth().heightIn(min = space.avatar.xs), padding = space.md) { content() }
}

private const val MILLIS_PER_SECOND = 1_000

/** The most lines the question is recalled in on the reveal, beside its explanation. */
private const val RECAP_LINES = 2

/**
 * The fewest of the board's lines a reveal keeps in sight, when it has room for a board at all: the
 * player's own, which the board scrolls to, and the rest a scroll away.
 */
private const val BOARD_LEAST_LINES = 1

/** The least share of the room the answers keep beside the board, however long the board. */
private const val GRID_SHARE = 0.45f

/**
 * The reveal under its bar: the question over the answers, and the standings' board under them. The answers
 * come first: they keep the least room that sets them whole ([fits]) and at least [GRID_SHARE] of what is
 * left, and the board takes the rest up to its own height, scrolling past it. Where not even [boardLeast]
 * is left, on a phone too small for both, the board gives way and the player's points stay in the bar.
 * [gap] stands between the three.
 */
@Composable
private fun RevealBody(
    fits: (Constraints) -> Boolean,
    gap: Dp,
    boardLeast: Dp,
    modifier: Modifier = Modifier,
    panel: @Composable () -> Unit,
    grid: @Composable (Modifier) -> Unit,
    board: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val space = gap.roundToPx()
        val top = subcompose(RevealSlot.PANEL, panel).map { it.measure(Constraints(maxWidth = width)) }
        val topHeight = top.maxOfOrNull { it.height } ?: 0
        val room = (constraints.maxHeight - topHeight - space).coerceAtLeast(0)
        // A margin past the least, which the fit is measured at to the pixel, for the tiles' own rounding.
        val gridLeast = leastFitting(room) { height -> fits(Constraints.fixed(width, height)) } + space
        val gridKept = maxOf(gridLeast, (room * GRID_SHARE).roundToInt()).coerceAtMost(room)
        val boardRoom = room - gridKept - space
        val below =
            if (boardRoom >= boardLeast.roundToPx()) {
                subcompose(
                    RevealSlot.STANDINGS,
                    board,
                ).map { it.measure(Constraints(maxWidth = width, maxHeight = boardRoom)) }
            } else {
                emptyList()
            }
        val boardHeight = below.maxOfOrNull { it.height } ?: 0
        val gridHeight = (room - if (below.isEmpty()) 0 else boardHeight + space).coerceAtLeast(0)
        val answers =
            subcompose(RevealSlot.GRID) { grid(Modifier.fillMaxSize()) }.map {
                it.measure(Constraints.fixed(width, gridHeight))
            }
        layout(width, constraints.maxHeight) {
            top.forEach { it.place(0, 0) }
            answers.forEach { it.place(0, topHeight + space) }
            below.forEach { it.place(0, topHeight + space + gridHeight + space) }
        }
    }
}

/** The least height up to [most] at which [fits] holds, or [most] when not even that does. */
private inline fun leastFitting(
    most: Int,
    fits: (Int) -> Boolean,
): Int {
    if (most <= 0 || !fits(most)) return most.coerceAtLeast(0)
    var low = 0
    var high = most
    while (high - low > 1) {
        val mid = (low + high) / 2
        if (fits(mid)) high = mid else low = mid
    }
    return high
}

private enum class RevealSlot { PANEL, GRID, STANDINGS }
