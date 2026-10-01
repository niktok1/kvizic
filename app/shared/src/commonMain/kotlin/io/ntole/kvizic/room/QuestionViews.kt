package io.ntole.kvizic.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.style.TextAlign
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
import io.ntole.kvizic.design.component.Scoreboard
import io.ntole.kvizic.design.component.StageIconButton
import io.ntole.kvizic.design.component.TimerPhase
import io.ntole.kvizic.design.component.TimerSize
import io.ntole.kvizic.design.component.WaitingFor
import io.ntole.kvizic.design.component.rememberAnswersFit
import io.ntole.kvizic.design.component.signed
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.language.LocalLanguage
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.fill

/**
 * A question of the game under way, read alone ([GamePhase.Reading]) and then answered
 * ([GamePhase.Answering]): the round, its topic, the clock and the player's points over it, its answers under
 * it, and who it still waits for. A member who joined during the game watches it: their answers take no tap.
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
                    phase.question.topic,
                    topics,
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
                Panel(Modifier.fillMaxWidth(), kind = PanelKind.SCREEN, padding = space.xl) {
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
            Page {
                RoundBar(
                    phase.question.index,
                    phase.question.count,
                    phase.question.topic,
                    topics,
                    phase.standings,
                    state.you,
                    timer = timer,
                    timerDescription = words.secondsToAnswer.fill(lobby.settings.secondsPerQuestion),
                    onLeave = onLeave,
                )
                Spacer(Modifier.height(space.md))
                Panel(Modifier.fillMaxWidth(), kind = PanelKind.SCREEN, padding = space.lg) {
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
 * the first right ones in their order, what it gave the player, and the standings' head, until the next
 * question or the results.
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
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val reveal = phase.reveal
    val lobby = state.lobby
    val mine = reveal.results.firstOrNull { it.playerId == state.you }
    val seconds by secondsLeft(phase.next)
    RevealHaptic(reveal.questionId, right = mine?.option?.let { it == reveal.correct })
    Page {
        RoundBar(
            reveal.index,
            reveal.count,
            topic = null,
            topics,
            reveal.standings,
            state.you,
            timer = TimerPhase.Stopped(lobby.settings.secondsPerQuestion * MILLIS_PER_SECOND, 0),
            onLeave = onLeave,
        )
        Spacer(Modifier.height(space.md))
        RevealBody(
            fits = rememberAnswersFit(reveal.options.map { shown(it) }),
            gap = space.md,
            modifier = Modifier.weight(1f),
            panel = {
                Panel(Modifier.fillMaxWidth(), kind = PanelKind.SCREEN, padding = space.lg) {
                    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                        QuestionText(
                            shown(reveal.text),
                            Modifier.fillMaxWidth(),
                            recap = true,
                            textAlign = TextAlign.Center,
                        )
                        reveal.explanation?.let { explanation ->
                            KvizicText(
                                shown(explanation),
                                style = type.caption,
                                color = colors.onRaisedMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
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
                                i == reveal.correct -> AnswerTileState.CORRECT
                                i == mine?.option -> AnswerTileState.WRONG
                                else -> AnswerTileState.DIMMED
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
            standings = {
                Standings(lobby, reveal.standings, reveal.results.associate { it.playerId to it.points }, state.you)
            },
        )
        Spacer(Modifier.height(space.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val verdict =
                when {
                    mine?.option == null -> words.verdictNone
                    mine.option == reveal.correct -> words.verdictRight.fill(signed(mine.points))
                    else -> words.verdictWrong.fill(signed(mine.points))
                }
            Chip(verdict)
            Spacer(Modifier.weight(1f))
            KvizicText(
                (if (reveal.last) words.resultsIn else words.nextQuestionIn).fill(seconds),
                style = type.caption,
                color = colors.onPageMuted,
            )
            Spacer(Modifier.width(space.sm))
            StageIconButton(
                KvizicIcons.Flag,
                contentDescription = words.reportQuestion,
                onClick = tapped("reveal.report") { onReport(reveal.questionId) },
                kind = ButtonKind.QUIET,
                small = true,
            )
        }
    }
}

/** The standings' head, the first three, and the player's own line under them when it is not among them. */
@Composable
private fun Standings(
    lobby: Lobby,
    standings: List<Standing>,
    points: Map<String, Int>,
    you: String,
) {
    val ranked = standings.sortedBy { it.rank }
    val shownRows = ranked.take(STANDINGS_HEAD) + ranked.drop(STANDINGS_HEAD).filter { it.playerId == you }
    Scoreboard(
        shownRows.mapNotNull { standing ->
            val member = lobby.member(standing.playerId) ?: return@mapNotNull null
            ScoreRow(
                place = standing.rank,
                name = shown(member.name),
                avatarId = member.avatar,
                seat = member.seat,
                total = standing.score,
                delta = points[standing.playerId],
                host = member.playerId == lobby.host,
                own = member.playerId == you,
            )
        },
        Modifier.fillMaxWidth(),
    )
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

/** The bar over a question: the round and its topic, the clock in the middle, and the player's points on flaps. */
@Composable
private fun RoundBar(
    index: Int,
    count: Int,
    topic: String?,
    topics: List<Topic>,
    standings: List<Standing>,
    you: String,
    timer: TimerPhase? = null,
    timerDescription: String? = null,
    onLeave: () -> Unit = {},
) {
    val words = LocalStrings.current.game
    val language = LocalLanguage.current
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        StageIconButton(
            KvizicIcons.Leave,
            contentDescription = words.leave,
            onClick = tapped("question.leave", onClick = onLeave),
            kind = ButtonKind.QUIET,
            small = true,
        )
        Spacer(Modifier.width(space.xs))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KvizicText(words.question, style = type.label, color = colors.onPageMuted)
                Spacer(Modifier.width(space.xs))
                KvizicText("${index + 1} / $count", style = type.label, color = colors.onPageAccent)
            }
            if (topic != null) {
                Spacer(Modifier.height(space.xs))
                Chip(topicNameOf(topic, topics, language))
            }
        }
        if (timer != null) QuestionTimer(timer, size = TimerSize.SMALL, contentDescription = timerDescription)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            KvizicText(words.points, style = type.label, color = colors.onPageMuted)
            Spacer(Modifier.height(space.xs))
            FlipNumber(standings.firstOrNull { it.playerId == you }?.score ?: 0, size = FlapSize.MEDIUM)
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

/** How many of the standings a reveal shows, the player's own line besides. */
private const val STANDINGS_HEAD = 3

/**
 * The reveal between its bar and its verdict: the question over the answers, and the standings under them
 * while that leaves the answers room to be set whole ([fits]). On a small phone with eight players and
 * long answers it does not, and the standings give way: the player's own points stay in the bar and the
 * verdict, and the answers, which the reveal is for, keep their room. [gap] stands between the three.
 */
@Composable
private fun RevealBody(
    fits: (Constraints) -> Boolean,
    gap: Dp,
    modifier: Modifier = Modifier,
    panel: @Composable () -> Unit,
    grid: @Composable (Modifier) -> Unit,
    standings: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val space = gap.roundToPx()
        val top = subcompose(RevealSlot.PANEL, panel).map { it.measure(loose) }
        val topHeight = top.maxOfOrNull { it.height } ?: 0
        val below = subcompose(RevealSlot.STANDINGS, standings).map { it.measure(loose) }
        val belowHeight = below.maxOfOrNull { it.height } ?: 0
        val withStandings = constraints.maxHeight - topHeight - belowHeight - space * 2
        val keepStandings = withStandings > 0 && fits(Constraints.fixed(width, withStandings))
        val gridHeight =
            (if (keepStandings) withStandings else constraints.maxHeight - topHeight - space).coerceAtLeast(0)
        val answers =
            subcompose(RevealSlot.GRID) { grid(Modifier.fillMaxSize()) }.map {
                it.measure(Constraints.fixed(width, gridHeight))
            }
        layout(width, constraints.maxHeight) {
            top.forEach { it.place(0, 0) }
            answers.forEach { it.place(0, topHeight + space) }
            if (keepStandings) below.forEach { it.place(0, topHeight + space + gridHeight + space) }
        }
    }
}

private enum class RevealSlot { PANEL, GRID, STANDINGS }
