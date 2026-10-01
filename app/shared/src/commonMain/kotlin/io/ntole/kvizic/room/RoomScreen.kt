package io.ntole.kvizic.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.core.domain.lobby.Deadline
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.GameResults
import io.ntole.kvizic.core.domain.lobby.Lobby
import io.ntole.kvizic.core.domain.lobby.LobbyKind
import io.ntole.kvizic.core.domain.lobby.LobbyMember
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.Chip
import io.ntole.kvizic.design.component.ChipTone
import io.ntole.kvizic.design.component.CodeDisplay
import io.ntole.kvizic.design.component.FlapSize
import io.ntole.kvizic.design.component.FlipNumber
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
import io.ntole.kvizic.language.LocalLanguage
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.navigation.BackTopBar
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds

/** What a member can do in the room, each a command to it, or a screen of the app's. */
@Immutable
class RoomActions(
    val answer: (Int) -> Unit = {},
    val start: () -> Unit = {},
    val openSettings: () -> Unit = {},
    val kick: (String) -> Unit = {},
    val transferHost: (String) -> Unit = {},
    val backToLobby: () -> Unit = {},
    val react: (String) -> Unit = {},
    val leave: () -> Unit = {},
    val share: (String) -> Unit = {},
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
    val me = state.lobby.member(state.you)
    var leaving by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        RoomBanner(reconnecting = state.reconnecting, note = note)
        when (val phase = state.phase) {
            is GamePhase.Waiting -> {
                val results = phase.lastResults
                if (me?.onResults == true && results != null) {
                    Results(results, state.lobby, state.you, newGameStarting = false, actions)
                } else {
                    Waiting(state, countdown = null, bursts, actions, onLeave = { leaving = true })
                }
            }

            is GamePhase.Countdown -> {
                val results = phase.lastResults
                if (me?.onResults == true && results != null) {
                    Results(results, state.lobby, state.you, newGameStarting = true, actions)
                } else {
                    Waiting(state, countdown = phase.deadline, bursts, actions, onLeave = { leaving = true })
                }
            }

            is GamePhase.Reading -> {
                QuestionScreen(state, phase, topics, actions)
            }

            is GamePhase.Answering -> {
                QuestionScreen(state, phase, topics, actions)
            }

            is GamePhase.Revealing -> {
                RevealScreen(state, phase, topics)
            }
        }
    }
    if (leaving) LeaveDialog(onStay = { leaving = false }, onLeave = actions.leave)
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
 * The lobby waiting: its code on flaps, the seats, the settings, the reactions, and the host's start; or,
 * once the host started, the [countdown] in the start's place.
 */
@Composable
private fun Waiting(
    state: LobbySessionState.InLobby,
    countdown: Deadline?,
    bursts: Map<String, Burst>,
    actions: RoomActions,
    onLeave: () -> Unit,
) {
    val words = LocalStrings.current.game
    val language = LocalLanguage.current
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val lobby = state.lobby
    val hosting = lobby.host == state.you
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }

    BackTopBar(
        onBack = onLeave,
        title =
            when (lobby.kind) {
                LobbyKind.PRIVATE -> words.privateRoom
                LobbyKind.PUBLIC -> words.publicRoom
                LobbyKind.SOLO -> words.soloRun
            },
        actions =
            if (lobby.kind == LobbyKind.SOLO) {
                null
            } else {
                {
                    StageIconButton(
                        KvizicIcons.Share,
                        contentDescription = words.shareRoom,
                        onClick = tapped("room.share") { actions.share(words.shareText.fill(lobby.code)) },
                        small = true,
                    )
                }
            },
    )
    Page {
        if (lobby.kind != LobbyKind.SOLO) {
            KvizicText(
                words.roomCode,
                Modifier.fillMaxWidth(),
                style = type.label,
                color = colors.onPageMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(space.sm))
            CodeDisplay(
                lobby.code,
                Modifier.align(Alignment.CenterHorizontally),
                contentDescription = words.roomCode + ": " + lobby.code.toList().joinToString(" "),
            )
            Spacer(Modifier.height(space.lg))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            KvizicText(words.players, style = type.label, color = colors.onPageMuted)
            Spacer(Modifier.width(space.sm))
            KvizicText(
                "${lobby.members.size} / ${lobby.settings.maxPlayers}",
                style = type.label,
                color = colors.onPageAccent,
            )
        }
        Spacer(Modifier.height(space.sm))
        Seats(lobby, state.you, bursts, onChoose = if (hosting) ({ chosen = it }) else null)
        Spacer(Modifier.height(space.sm))
        val status =
            when {
                lobby.kind == LobbyKind.SOLO -> null
                lobby.members.size < 2 -> words.waitingForPlayers
                !hosting -> words.waitingForHost
                else -> null
            }
        if (status != null) {
            KvizicText(
                status,
                Modifier.fillMaxWidth(),
                style = type.caption,
                color = colors.onPageMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(space.md))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
            settingsChips(lobby.settings).forEach { chip ->
                Chip(
                    chip,
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
        Spacer(Modifier.weight(1f))
        if (lobby.kind != LobbyKind.SOLO) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                REACTIONS.forEach { reaction ->
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
                    supportingText =
                        words.playerCount.of(lobby.members.size, language) + " · " +
                            words.questionCount.of(lobby.settings.questionCount, language),
                )
            }

            else -> {
                Unit
            }
        }
    }
    val target = chosen?.let { lobby.member(it) }
    if (target != null) {
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
                            emptyLabel = words.freeSeat,
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
    return SeatOccupant(
        name = shown(member.name),
        avatarId = member.avatar,
        seat = member.seat,
        badge =
            when {
                member.onResults -> words.onResults
                host -> words.host
                member.playerId == you -> words.you
                else -> null
            },
        host = host,
        away = !member.connected || member.onResults,
    )
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
    StageDialog(onDismiss = onDismiss, title = shown(member.name)) {
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
 * back to the lobby or out; or, once the host started the next game, the way into it.
 */
@Composable
private fun Results(
    results: GameResults,
    lobby: Lobby,
    you: String,
    newGameStarting: Boolean,
    actions: RoomActions,
) {
    val words = LocalStrings.current.game
    val language = LocalLanguage.current
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val ranked = results.standings.sortedBy { it.rank }
    val seatOf = { playerId: String, fallback: Int -> lobby.member(playerId)?.seat ?: fallback }
    Page {
        Spacer(Modifier.height(space.md))
        KvizicText(
            words.gameOver.fill(words.questionCount.of(results.questionCount, language)),
            Modifier.fillMaxWidth(),
            style = type.label,
            color = colors.onPageMuted,
            textAlign = TextAlign.Center,
        )
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
