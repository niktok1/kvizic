package io.ntole.kvizic.room

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.text.style.TextAlign
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.analytics.tappedAt
import io.ntole.kvizic.core.domain.lobby.Deadline
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.GameResults
import io.ntole.kvizic.core.domain.lobby.Lobby
import io.ntole.kvizic.core.domain.lobby.LobbyDifficulty
import io.ntole.kvizic.core.domain.lobby.LobbyKind
import io.ntole.kvizic.core.domain.lobby.LobbyMember
import io.ntole.kvizic.core.domain.lobby.LobbyRules
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.core.domain.report.QuestionReportReason
import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.Chip
import io.ntole.kvizic.design.component.ChipTone
import io.ntole.kvizic.design.component.CodeDisplay
import io.ntole.kvizic.design.component.FlapSize
import io.ntole.kvizic.design.component.FlipNumber
import io.ntole.kvizic.design.component.KvizicIcon
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.Podium
import io.ntole.kvizic.design.component.PodiumPlace
import io.ntole.kvizic.design.component.ReactionBurst
import io.ntole.kvizic.design.component.ScoreRow
import io.ntole.kvizic.design.component.Scoreboard
import io.ntole.kvizic.design.component.Seat
import io.ntole.kvizic.design.component.SeatOccupant
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.component.StageDialog
import io.ntole.kvizic.design.component.StageIconButton
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.SkinMotion
import io.ntole.kvizic.language.LocalLanguage
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.navigation.BackTopBar
import io.ntole.kvizic.navigation.SystemBack
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds

/** What a member can do in the room, each a command to it, or a screen of the app's. */
@Immutable
class RoomActions(
    val answer: (Int) -> Unit = {},
    val start: () -> Unit = {},
    val openSettings: () -> Unit = {},
    val chooseDifficulty: (LobbyDifficulty) -> Unit = {},
    val kick: (String) -> Unit = {},
    /** A member's vote to put a player out, or, for none, their vote taken back. */
    val voteKick: (String?) -> Unit = {},
    val transferHost: (String) -> Unit = {},
    val backToLobby: () -> Unit = {},
    val react: (String) -> Unit = {},
    val leave: () -> Unit = {},
    val share: (String) -> Unit = {},
    /** Copies the room's code, given, to the clipboard. */
    val copyCode: (String) -> Unit = {},
    val report: (questionId: String, reason: QuestionReportReason) -> Unit = { _, _ -> },
)

/**
 * The room the player is in, whatever it does: the lobby while it waits, its countdown, a question read,
 * answered and revealed, and the results, which the player leaves for the lobby by hand. [topics] name the
 * questions' topics; [note] is a line the room shows a moment; [bursts] each member's last reaction.
 */
@Composable
fun RoomScreen(
    state: LobbySessionState.InLobby,
    topics: List<Topic>,
    note: RoomNote?,
    bursts: Map<String, Burst>,
    actions: RoomActions,
    modifier: Modifier = Modifier,
) {
    var leaving by rememberSaveable { mutableStateOf(false) }
    // Held here, not on the reveal, so the dialog stays as the next question comes.
    var reporting by rememberSaveable { mutableStateOf<String?>(null) }
    // The system's back asks before it leaves the room, wherever in it the player is.
    SystemBack(enabled = true) { leaving = true }
    val motion = KvizicTheme.skin.motion
    Column(modifier.fillMaxSize()) {
        RoomBanner(reconnecting = state.reconnecting, note = note)
        // Each step of the game gives way to the next, never a cut: the stage changes by its key, and within
        // one step the screen only takes the new state.
        AnimatedContent(
            targetState = state,
            modifier = Modifier.weight(1f),
            contentKey = ::stageOf,
            transitionSpec = { stageChange(stageOf(initialState), stageOf(targetState), motion) },
            label = "stage",
        ) { shown ->
            val shownMe = shown.lobby.member(shown.you)
            // The host changes the room's settings during a game too, for the next one; a solo run has none.
            val openSettings =
                actions.openSettings.takeIf { shown.lobby.host == shown.you && shown.lobby.kind != LobbyKind.SOLO }
            when (val phase = shown.phase) {
                is GamePhase.Waiting -> {
                    val results = phase.lastResults
                    if (shownMe?.onResults == true && results != null) {
                        Results(results, shown.lobby, shown.you, newGameStarting = false, actions, openSettings)
                    } else {
                        Waiting(shown, countdown = null, bursts, actions, onLeave = { leaving = true }, topics = topics)
                    }
                }

                is GamePhase.Countdown -> {
                    val results = phase.lastResults
                    if (shownMe?.onResults == true && results != null) {
                        Results(results, shown.lobby, shown.you, newGameStarting = true, actions, openSettings)
                    } else {
                        Waiting(
                            shown,
                            countdown = phase.deadline,
                            bursts,
                            actions,
                            onLeave = { leaving = true },
                            topics = topics,
                        )
                    }
                }

                is GamePhase.Reading -> {
                    QuestionScreen(
                        shown,
                        phase,
                        topics,
                        actions,
                        onLeave = { leaving = true },
                        onSettings = openSettings,
                    )
                }

                is GamePhase.Answering -> {
                    QuestionScreen(
                        shown,
                        phase,
                        topics,
                        actions,
                        onLeave = { leaving = true },
                        onSettings = openSettings,
                    )
                }

                is GamePhase.Revealing -> {
                    RevealScreen(
                        shown,
                        phase,
                        topics,
                        onReport = { reporting = it },
                        onLeave = { leaving = true },
                        onSettings = openSettings,
                    )
                }
            }
        }
    }
    if (leaving) LeaveDialog(onStay = { leaving = false }, onLeave = actions.leave)
    reporting?.let { questionId ->
        ReportDialog(
            onDismiss = { reporting = null },
            onReport = { reason ->
                reporting = null
                actions.report(questionId, reason)
            },
        )
    }
}

/** Why the question is reported: each reason one tap, which sends it. */
@Composable
private fun ReportDialog(
    onDismiss: () -> Unit,
    onReport: (QuestionReportReason) -> Unit,
) {
    val strings = LocalStrings.current
    val words = strings.game
    val pick = tappedAt("report.reason") { index -> onReport(QuestionReportReason.entries[index]) }
    StageDialog(onDismiss = onDismiss, title = words.reportWhy) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KvizicTheme.space.xs)) {
            QuestionReportReason.entries.forEachIndexed { index, reason ->
                StageButton(
                    words.reasonText(reason),
                    onClick = { pick(index) },
                    modifier = Modifier.fillMaxWidth(),
                    kind = ButtonKind.SECONDARY,
                    size = ButtonSize.SMALL,
                )
            }
            StageButton(
                strings.cancel,
                onClick = tapped("report.cancel", onClick = onDismiss),
                modifier = Modifier.align(Alignment.End),
                kind = ButtonKind.QUIET,
                size = ButtonSize.SMALL,
            )
        }
    }
}

/** Over the room, while it is so: that the connection is being made again, and a note the room shows. */
@Composable
private fun RoomBanner(
    reconnecting: Boolean,
    note: RoomNote?,
) {
    val words = LocalStrings.current.game
    val space = KvizicTheme.space
    val text = if (reconnecting) words.reconnecting else note?.let { words.noteText(it) }
    if (text != null) {
        Box(Modifier.fillMaxWidth().padding(horizontal = space.screen, vertical = space.xs), Alignment.Center) {
            Chip(text, tone = if (reconnecting) ChipTone.NEUTRAL else ChipTone.ACCENT)
        }
    }
}

@Composable
private fun LeaveDialog(
    onStay: () -> Unit,
    onLeave: () -> Unit,
) {
    val strings = LocalStrings.current
    StageDialog(onDismiss = onStay, title = strings.game.leaveRoom) {
        StageButton(
            strings.cancel,
            onClick = tapped("room.leave_cancel", onClick = onStay),
            kind = ButtonKind.QUIET,
            size = ButtonSize.SMALL,
        )
        StageButton(
            strings.game.leave,
            onClick = tapped("room.leave_confirm", onClick = onLeave),
            kind = ButtonKind.DARK,
            size = ButtonSize.SMALL,
        )
    }
}

/**
 * The lobby waiting: its code, small in the top bar, the seats, the settings, the reactions, and the host's
 * start; or, once the host started, the [countdown] in the start's place. The seats say who is in and how
 * many more fit, so no words count them.
 */
@Composable
private fun Waiting(
    state: LobbySessionState.InLobby,
    countdown: Deadline?,
    bursts: Map<String, Burst>,
    actions: RoomActions,
    onLeave: () -> Unit,
    topics: List<Topic> = emptyList(),
) {
    val words = LocalStrings.current.game
    val space = KvizicTheme.space
    val lobby = state.lobby
    val hosting = lobby.host == state.you
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }

    // One column: the stage lays its content out as a box, where the bar would sit over the page.
    Column(Modifier.fillMaxSize()) {
        if (lobby.kind == LobbyKind.SOLO) {
            BackTopBar(onBack = onLeave, title = words.soloRun)
        } else {
            BackTopBar(
                onBack = onLeave,
                titleContent = { RoomCode(lobby, onCopy = { actions.copyCode(lobby.code) }) },
                actions = {
                    StageIconButton(
                        KvizicIcons.Share,
                        contentDescription = words.shareRoom,
                        onClick = tapped("room.share") { actions.share(words.shareText.fill(lobby.code)) },
                        small = true,
                    )
                },
            )
        }
        Page {
            // The host chooses what to do with a member at any time; the rest may vote one out while the room waits.
            val choosing = hosting || (lobby.kind != LobbyKind.SOLO && countdown == null)
            Seats(lobby, state.you, bursts, onChoose = if (choosing) ({ chosen = it }) else null)
            Spacer(Modifier.height(space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
                if (lobby.kind == LobbyKind.SOLO) {
                    // A solo run's one setting is its level, picked here; each level keeps its own best.
                    LobbyDifficulty.entries.forEach { level ->
                        Chip(
                            words.levelName(level),
                            selected = lobby.settings.difficulty == level,
                            onClick =
                                if (countdown == null) {
                                    tapped("room.difficulty", mapOf("difficulty" to level.name.lowercase())) {
                                        actions.chooseDifficulty(level)
                                    }
                                } else {
                                    null
                                },
                        )
                    }
                } else {
                    settingsChips(lobby.settings, topics).forEach { chip ->
                        Chip(
                            chip.text,
                            icon = chip.icon,
                            onClick =
                                if (hosting &&
                                    countdown == null
                                ) {
                                    tapped("room.settings", onClick = actions.openSettings)
                                } else {
                                    null
                                },
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (lobby.kind != LobbyKind.SOLO) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    EMOTES.forEach { reaction ->
                        StageIconButton(
                            reaction.icon,
                            contentDescription = reaction.name(words),
                            onClick =
                                tapped(
                                    "room.reaction",
                                    mapOf("reaction" to reaction.id),
                                ) { actions.react(reaction.id) },
                            small = true,
                        )
                    }
                }
                Spacer(Modifier.height(space.md))
            }
            when {
                countdown != null -> {
                    Countdown(countdown)
                }

                hosting -> {
                    StageButton(
                        words.start,
                        onClick = tapped("room.start", onClick = actions.start),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                lobby.kind != LobbyKind.SOLO -> {
                    // A member's way to tell the host the room is ready: the nudge bursts as a bell over their seat.
                    StageButton(
                        words.reactionNudge,
                        onClick =
                            tapped("room.reaction", mapOf("reaction" to LobbyRules.NUDGE)) {
                                actions.react(LobbyRules.NUDGE)
                            },
                        modifier = Modifier.fillMaxWidth(),
                        kind = ButtonKind.SECONDARY,
                        icon = KvizicIcons.Bell,
                    )
                }

                else -> {
                    Unit
                }
            }
        }
    }
    val target = chosen?.let { lobby.member(it) }
    when {
        target == null -> {
            Unit
        }

        hosting -> {
            MemberDialog(
                target,
                onDismiss = { chosen = null },
                onKick = {
                    chosen = null
                    actions.kick(target.playerId)
                },
                onMakeHost = {
                    chosen = null
                    actions.transferHost(target.playerId)
                },
            )
        }

        countdown == null -> {
            VoteDialog(
                target,
                onDismiss = { chosen = null },
                onVote = {
                    chosen = null
                    actions.voteKick(target.playerId)
                },
                onWithdraw = {
                    chosen = null
                    actions.voteKick(null)
                },
            )
        }
    }
}

/**
 * The room's code in the top bar, small: a lock or a globe for a private room or a public one, the word, and
 * the code on small flaps. A long press copies it, as [onCopy] does, with the phone's tick.
 */
@Composable
private fun RoomCode(
    lobby: Lobby,
    onCopy: () -> Unit,
) {
    val words = LocalStrings.current.game
    val space = KvizicTheme.space
    val colors = KvizicTheme.colors
    val haptics = LocalHapticFeedback.current
    val copy = tapped("room.copy_code", onClick = onCopy)
    val public = lobby.kind == LobbyKind.PUBLIC
    val said =
        (if (public) words.publicRoom else words.privateRoom) + ", " + words.roomCode + ": " +
            lobby.code.toList().joinToString(" ")
    Row(
        Modifier
            .clearAndSetSemantics {
                contentDescription = said
                onLongClick(label = words.copyCode) {
                    copy()
                    true
                }
            }.pointerInput(copy) {
                detectTapGestures(
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        copy()
                    },
                )
            },
        horizontalArrangement = Arrangement.spacedBy(space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KvizicIcon(
            if (public) KvizicIcons.Globe else KvizicIcons.Lock,
            contentDescription = null,
            tint = colors.onPageMuted,
            size = space.icon.small,
        )
        KvizicText(words.code, style = KvizicTheme.type.label, color = colors.onPageMuted, maxLines = 1)
        CodeDisplay(lobby.code, size = FlapSize.SMALL, framed = false)
    }
}

/** A room's seats, as many as it holds, the members in their seats' order, each one's last reaction bursting over it. */
@Composable
private fun Seats(
    lobby: Lobby,
    you: String,
    bursts: Map<String, Burst>,
    onChoose: ((String) -> Unit)?,
) {
    val words = LocalStrings.current.game
    val space = KvizicTheme.space
    val columns = space.seat.columns
    val members = lobby.members.sortedBy { it.seat }
    val seats: List<LobbyMember?> = members + List((lobby.settings.maxPlayers - members.size).coerceAtLeast(0)) { null }
    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
        seats.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                row.forEach { member ->
                    Box(Modifier.weight(1f)) {
                        Seat(
                            member?.let { occupantOf(it, lobby, you) },
                            emptyDescription = words.freeSeat,
                            onClick =
                                if (member != null && member.playerId != you && onChoose != null) {
                                    tapped("room.seat") { onChoose(member.playerId) }
                                } else {
                                    null
                                },
                        )
                        val burst = member?.let { bursts[it.playerId] }
                        val icon = burst?.let { reactionIcon(it.reaction) }
                        if (icon != null) {
                            ReactionBurst(
                                icon,
                                burstKey = burst.key,
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = space.sm, y = -space.xl),
                                startedAt = burst.at,
                            )
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun occupantOf(
    member: LobbyMember,
    lobby: Lobby,
    you: String,
): SeatOccupant {
    val words = LocalStrings.current.game
    val host = member.playerId == lobby.host
    val own = member.playerId == you
    return SeatOccupant(
        name = shown(member.name),
        avatarId = member.avatar,
        seat = member.seat,
        // Shown otherwise, so said only: the host's microphone, the player's own seat lit, an hourglass.
        badge =
            listOfNotNull(
                words.host.takeIf { host },
                words.you.takeIf { own },
                words.onResults.takeIf { member.onResults },
            ).joinToString(", ").ifEmpty { null },
        host = host,
        own = own,
        status = KvizicIcons.Hourglass.takeIf { member.onResults },
        away = !member.connected || member.onResults,
        // A count is the same in every language, so it is no string of theirs.
        votes = if (member.kickVotes > 0) "${member.kickVotes}/${member.kickVotesNeeded}" else null,
        votesDescription = votesLine(member),
    )
}

/** How many vote [member] out, of how many it takes, in words; none while nobody does. */
@Composable
private fun votesLine(member: LobbyMember): String? =
    if (member.kickVotes > 0) {
        LocalStrings.current.game.kickVotes
            .fill(member.kickVotes, member.kickVotesNeeded)
    } else {
        null
    }

/** The host's choice about another member: hand them the hosting, or remove them for good. */
@Composable
private fun MemberDialog(
    member: LobbyMember,
    onDismiss: () -> Unit,
    onKick: () -> Unit,
    onMakeHost: () -> Unit,
) {
    val strings = LocalStrings.current
    StageDialog(onDismiss = onDismiss, title = shown(member.name), text = votesLine(member)) {
        StageButton(
            strings.cancel,
            onClick = tapped("room.member_cancel", onClick = onDismiss),
            kind = ButtonKind.QUIET,
            size = ButtonSize.SMALL,
        )
        StageButton(
            strings.game.makeHost,
            onClick = tapped("room.make_host", onClick = onMakeHost),
            kind = ButtonKind.SECONDARY,
            size = ButtonSize.SMALL,
        )
        StageButton(
            strings.game.removePlayer,
            onClick = tapped("room.kick", onClick = onKick),
            kind = ButtonKind.DARK,
            size = ButtonSize.SMALL,
        )
    }
}

/**
 * A member's choice about another: vote them out of the room, which takes more than half of it, or take the
 * vote back; how many vote them out already, and how many it takes, when anyone does.
 */
@Composable
private fun VoteDialog(
    member: LobbyMember,
    onDismiss: () -> Unit,
    onVote: () -> Unit,
    onWithdraw: () -> Unit,
) {
    val strings = LocalStrings.current
    StageDialog(onDismiss = onDismiss, title = shown(member.name), text = votesLine(member)) {
        StageButton(
            strings.cancel,
            onClick = tapped("room.vote_cancel", onClick = onDismiss),
            kind = ButtonKind.QUIET,
            size = ButtonSize.SMALL,
        )
        if (member.kickVotedByYou) {
            StageButton(
                strings.game.withdrawVote,
                onClick = tapped("room.withdraw_vote", onClick = onWithdraw),
                kind = ButtonKind.SECONDARY,
                size = ButtonSize.SMALL,
            )
        } else {
            StageButton(
                strings.game.voteKick,
                onClick = tapped("room.vote_kick", onClick = onVote),
                kind = ButtonKind.DARK,
                size = ButtonSize.SMALL,
                icon = KvizicIcons.Leave,
            )
        }
    }
}

/** The seconds to the first question, on flaps. */
@Composable
private fun Countdown(deadline: Deadline) {
    val words = LocalStrings.current.game
    val space = KvizicTheme.space
    val seconds by secondsLeft(deadline)
    Row(
        Modifier.fillMaxWidth().height(space.button.regular),
        horizontalArrangement = Arrangement.spacedBy(space.md, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KvizicText(words.gameStarting, style = KvizicTheme.type.label)
        FlipNumber(seconds, size = FlapSize.MEDIUM)
    }
}

/** The whole seconds left to [deadline], counted down a second at a time. */
@Composable
internal fun secondsLeft(deadline: Deadline) =
    produceState(initialValue = secondsOf(deadline), deadline) {
        while (value > 0) {
            delay(TICK)
            value = secondsOf(deadline)
        }
    }

private fun secondsOf(deadline: Deadline): Int =
    ceil(deadline.remaining().inWholeMilliseconds / MILLIS_PER_SECOND).toInt()

private const val MILLIS_PER_SECOND = 1_000.0
private val TICK = 200.milliseconds

/**
 * A game's end: who won and the podium, the rest of the standings, how the player did, and their own way
 * back to the lobby or out; or, once the host started the next game, the way into it. [onSettings], the
 * host's, opens the room's settings for the next game.
 */
@Composable
private fun Results(
    results: GameResults,
    lobby: Lobby,
    you: String,
    newGameStarting: Boolean,
    actions: RoomActions,
    onSettings: (() -> Unit)? = null,
) {
    val words = LocalStrings.current.game
    val language = LocalLanguage.current
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val ranked = results.standings.sortedBy { it.rank }
    val seatOf = { playerId: String, fallback: Int -> lobby.member(playerId)?.seat ?: fallback }
    Page {
        Box(Modifier.fillMaxWidth().heightIn(min = space.md * 2), contentAlignment = Alignment.Center) {
            KvizicText(
                words.gameOver.fill(words.questionCount.of(results.questionCount, language)),
                Modifier.fillMaxWidth(),
                style = type.label,
                color = colors.onPageMuted,
                textAlign = TextAlign.Center,
            )
            if (onSettings != null) {
                StageIconButton(
                    KvizicIcons.Sliders,
                    contentDescription = LocalStrings.current.game.settings,
                    onClick = tapped("results.settings", onClick = onSettings),
                    modifier = Modifier.align(Alignment.CenterEnd),
                    kind = ButtonKind.QUIET,
                    small = true,
                )
            }
        }
        ranked.firstOrNull()?.let { winner ->
            Spacer(Modifier.height(space.xs))
            KvizicText(
                words.winner.fill(shown(winner.name)),
                Modifier.fillMaxWidth(),
                style = type.headline,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(1f))
        Podium(
            ranked.take(PODIUM).mapIndexed { i, standing ->
                PodiumPlace(shown(standing.name), standing.avatar, seatOf(standing.playerId, i), standing.score)
            },
            Modifier.fillMaxWidth(),
        )
        val rest = ranked.drop(PODIUM)
        if (rest.isNotEmpty()) {
            Spacer(Modifier.height(space.lg))
            Scoreboard(
                rest.mapIndexed { i, standing ->
                    ScoreRow(
                        place = standing.rank,
                        name = shown(standing.name),
                        avatarId = standing.avatar,
                        seat = seatOf(standing.playerId, PODIUM + i),
                        total = standing.score,
                        own = standing.playerId == you,
                    )
                },
                Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(space.md))
        Row(
            horizontalArrangement = Arrangement.spacedBy(space.xs),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            results.standings.firstOrNull { it.playerId == you }?.let { own ->
                Chip(words.rightOf.fill(own.correct, results.questionCount), tone = ChipTone.GAIN)
            }
            results.personalBest?.let { best ->
                if (best.isNew) {
                    Chip(words.newBest, icon = KvizicIcons.Crown, tone = ChipTone.ACCENT)
                } else {
                    Chip(words.best.fill(best.previous ?: best.score), icon = KvizicIcons.Crown)
                }
            }
        }
        if (results.endedEarly) {
            Spacer(Modifier.height(space.sm))
            KvizicText(
                words.endedEarly,
                Modifier.fillMaxWidth(),
                style = type.caption,
                color = colors.onPageMuted,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(space.md)) {
            StageButton(
                if (newGameStarting) words.joinNewGame else words.backToRoom,
                onClick = tapped("results.back_to_room", onClick = actions.backToLobby),
                modifier = Modifier.weight(1f),
            )
            StageButton(
                words.leave,
                onClick = tapped("results.leave", onClick = actions.leave),
                kind = ButtonKind.DARK,
                icon = KvizicIcons.Leave,
            )
        }
    }
}

/** A room's page: the screen's padding, and its content held to a readable column on a wide window. */
@Composable
internal fun Page(content: @Composable ColumnScope.() -> Unit) {
    val space = KvizicTheme.space
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = space.contentWidth)
                    .fillMaxSize()
                    .padding(horizontal = space.screen, vertical = space.md),
            content = content,
        )
    }
}

/** How many of a game's best stand on its podium. */
private const val PODIUM = 3

/** Which step of the room a state shows: a change of it is a change of the stage, and the screen moves. */
private data class Stage(
    val kind: StageKind,
    val game: String? = null,
    val question: Int = -1,
)

private enum class StageKind { LOBBY, RESULTS, READ, ANSWER, REVEAL }

private fun stageOf(state: LobbySessionState.InLobby): Stage {
    val onResults = state.lobby.member(state.you)?.onResults == true
    return when (val phase = state.phase) {
        is GamePhase.Waiting -> {
            Stage(
                if (onResults &&
                    phase.lastResults != null
                ) {
                    StageKind.RESULTS
                } else {
                    StageKind.LOBBY
                },
            )
        }

        is GamePhase.Countdown -> {
            Stage(
                if (onResults &&
                    phase.lastResults != null
                ) {
                    StageKind.RESULTS
                } else {
                    StageKind.LOBBY
                },
            )
        }

        is GamePhase.Reading -> {
            Stage(StageKind.READ, phase.gameId, phase.question.index)
        }

        is GamePhase.Answering -> {
            Stage(StageKind.ANSWER, phase.gameId, phase.question.index)
        }

        is GamePhase.Revealing -> {
            Stage(StageKind.REVEAL, phase.gameId, phase.reveal.index)
        }
    }
}

/**
 * How one stage gives way to the next, over the skin's [SkinMotion.stage]: a question read rises into its
 * answers, its answers fade into their reveal, and every new question comes in from the side like the next
 * card, the last one going out the other way; the rest fade.
 */
private fun stageChange(
    from: Stage,
    to: Stage,
    motion: SkinMotion,
): ContentTransform {
    val time = motion.stage.coerceAtLeast(1)
    val sameQuestion = from.game == to.game && from.question == to.question
    val transform =
        when {
            sameQuestion && from.kind == StageKind.READ && to.kind == StageKind.ANSWER -> {
                (
                    fadeIn(
                        tween(time),
                    ) + slideInVertically(tween(time, easing = FastOutSlowInEasing)) { it / RISE }
                ) togetherWith
                    (
                        fadeOut(tween(time / 2)) +
                            slideOutVertically(tween(time, easing = FastOutSlowInEasing)) { -it / RISE }
                    )
            }

            sameQuestion && from.kind == StageKind.ANSWER && to.kind == StageKind.REVEAL -> {
                fadeIn(tween(time / 2)) togetherWith fadeOut(tween(time / 2))
            }

            to.kind == StageKind.READ -> {
                (
                    slideInHorizontally(
                        tween(time, easing = FastOutSlowInEasing),
                    ) { it } + fadeIn(tween(time))
                ) togetherWith
                    (
                        slideOutHorizontally(tween(time, easing = FastOutSlowInEasing)) { -it / AWAY } +
                            fadeOut(tween(time / 2))
                    )
            }

            else -> {
                fadeIn(tween(time)) togetherWith fadeOut(tween(time))
            }
        }
    // No size to animate: the stage fills the room either way.
    return ContentTransform(transform.targetContentEnter, transform.initialContentExit, sizeTransform = null)
}

/** How far a question read rises as its answers come, and how far the last question goes as the next comes: shares. */
private const val RISE = 10
private const val AWAY = 3
