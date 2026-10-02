package io.ntole.kvizic.room

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.GameResults
import io.ntole.kvizic.core.domain.lobby.LobbyExit
import io.ntole.kvizic.core.domain.lobby.LobbyRules
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.Cues
import io.ntole.kvizic.design.sound.LocalCues
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** A [cue] the room calls for, [afterMillis] after the change that called for it, at [volume]. */
internal data class Heard(
    val cue: Cue,
    val afterMillis: Long = 0,
    val volume: Float = 1f,
)

/**
 * The cues the room's changes call for: who comes and goes, who is host, the votes, the connection, and the
 * game's steps, as the room's state changes from one to the next. Pure, so what the room sounds like is
 * tested without a scene or a speaker.
 *
 * The first state it sees is only taken as how things stand, with no cue: a room joined, rotated or
 * reconnected into does not replay what happened before. [tileStaggerMillis] is the time between one
 * answer's tile coming on and the next, which each tile's sound follows.
 */
internal class RoomCueTracker(
    private val tileStaggerMillis: Int,
) {
    private var before: LobbySessionState.InLobby? = null

    /** What the room changing to [state], from what it was when last asked, calls for; none the first time. */
    fun heard(state: LobbySessionState.InLobby): List<Heard> {
        val was = before
        before = state
        if (was == null || was.lobby.id != state.lobby.id) return emptyList()
        return buildList {
            people(was, state)
            connection(was, state)
            phase(was, state)
        }
    }

    private fun MutableList<Heard>.people(
        was: LobbySessionState.InLobby,
        now: LobbySessionState.InLobby,
    ) {
        val inBefore =
            was.lobby.members
                .map { it.playerId }
                .toSet() - now.you
        val inNow =
            now.lobby.members
                .map { it.playerId }
                .toSet() - now.you
        if ((inNow - inBefore).isNotEmpty()) add(Heard(Cue.JOINED, volume = OTHERS))
        if ((inBefore - inNow).isNotEmpty()) add(Heard(Cue.LEFT, volume = OTHERS))
        if (was.lobby.host != now.you && now.lobby.host == now.you) add(Heard(Cue.HOST))
        if (now.lobby.members.sumOf { it.kickVotes } > was.lobby.members.sumOf { it.kickVotes }) add(Heard(Cue.VOTE))
    }

    private fun MutableList<Heard>.connection(
        was: LobbySessionState.InLobby,
        now: LobbySessionState.InLobby,
    ) {
        if (!was.reconnecting && now.reconnecting) add(Heard(Cue.LINK_LOST))
        if (was.reconnecting && !now.reconnecting) add(Heard(Cue.LINK_BACK))
    }

    private fun MutableList<Heard>.phase(
        was: LobbySessionState.InLobby,
        now: LobbySessionState.InLobby,
    ) {
        val then = was.phase
        when (val phase = now.phase) {
            // A question coming on: the first of a game with a go, the last with a stinger.
            is GamePhase.Reading -> {
                val same =
                    then is GamePhase.Reading && then.gameId == phase.gameId &&
                        then.question.index == phase.question.index
                if (!same) {
                    val question = phase.question
                    add(
                        Heard(
                            when {
                                question.index == 0 -> Cue.GO
                                question.index == question.count - 1 -> Cue.LAST
                                else -> Cue.QUESTION
                            },
                        ),
                    )
                }
            }

            // The answers opening: each tile's sound as its tile comes on.
            is GamePhase.Answering -> {
                val opening =
                    then is GamePhase.Reading && then.gameId == phase.gameId &&
                        then.question.index == phase.question.index
                if (opening) {
                    phase.options.indices.forEach {
                        add(
                            Heard(
                                Cue.TILE,
                                afterMillis =
                                    it * tileStaggerMillis.toLong(),
                            ),
                        )
                    }
                }
            }

            // A game's end, as its results come up for this player.
            is GamePhase.Waiting -> {
                val results = phase.lastResults
                val fresh = then !is GamePhase.Waiting && then !is GamePhase.Countdown
                if (results != null && fresh && now.lobby.member(now.you)?.onResults == true) {
                    add(Heard(resultsCue(results, now.you)))
                }
            }

            is GamePhase.Countdown, is GamePhase.Revealing -> {
                Unit
            }
        }
    }

    private companion object {
        /** Others coming and going are heard under what the player does themselves. */
        const val OTHERS = 0.6f
    }
}

/**
 * How a game ended for [you], in a sound: a win's fanfare, the podium's, a solo run's best, or the plain end
 * of the rest.
 */
internal fun resultsCue(
    results: GameResults,
    you: String,
): Cue {
    val own = results.standings.firstOrNull { it.playerId == you } ?: return Cue.END
    results.personalBest?.let { return if (it.isNew) Cue.BEST else Cue.END }
    return when {
        own.rank == 1 -> Cue.WIN
        own.rank <= PODIUM_PLACES -> Cue.PODIUM
        else -> Cue.END
    }
}

private const val PODIUM_PLACES = 3

/** The sound of the reaction [id], or none for one this build does not know. */
internal fun reactionCue(id: String): Cue? =
    when (id) {
        "bravo" -> Cue.REACT_BRAVO
        "clap" -> Cue.REACT_CLAP
        "fire" -> Cue.REACT_FIRE
        "wow" -> Cue.REACT_WOW
        "laugh" -> Cue.REACT_LAUGH
        "oops" -> Cue.REACT_OOPS
        LobbyRules.NUDGE -> Cue.NUDGE
        else -> null
    }

/**
 * The sound of the line the room says: a refusal is an error, the rest a soft ping; a code copied is sounded
 * where it is copied, at once, and the host's room handed to the player by the tracker's own.
 */
internal fun noteCue(note: RoomNote): Cue? =
    when (note) {
        RoomNote.OnlyHost, RoomNote.Refused, RoomNote.VoteTooSoon, RoomNote.ReportFailed -> Cue.ERROR

        RoomNote.ServerRestarting,
        RoomNote.TopicsToppedUp,
        RoomNote.GameShortened,
        RoomNote.Reported,
        RoomNote.HostIdle,
        RoomNote.RoomIdle,
        is RoomNote.SettingsChanged,
        -> Cue.NOTICE

        is RoomNote.HostChanged -> if (note.you) null else Cue.NOTICE

        RoomNote.CodeCopied -> null
    }

/**
 * The sound of the room: the changes in [state] ([RoomCueTracker]), the line it says ([note]), the others'
 * reactions ([bursts]), and, while the player has not answered, the seconds running out under them. It draws
 * nothing and composes nothing of its own; a rotation, which makes it anew, starts from how things stand and
 * replays nothing.
 */
@Composable
internal fun RoomSounds(
    state: LobbySessionState.InLobby,
    note: RoomNote?,
    bursts: Map<String, Burst>,
) {
    val cues = LocalCues.current
    val motion = KvizicTheme.skin.motion
    val scope = rememberCoroutineScope()
    val tracker = remember(motion.tileStagger) { RoomCueTracker(motion.tileStagger) }
    // A scope of its own, not the effect's: the next state must not cut the tiles still to sound.
    LaunchedEffect(state) {
        tracker.heard(state).forEach { heard ->
            scope.launch {
                if (heard.afterMillis > 0) delay(heard.afterMillis)
                cues.play(heard.cue, heard.volume)
            }
        }
    }
    LaunchedEffect(note) { note?.let(::noteCue)?.let { cues.play(it) } }
    val you by rememberUpdatedState(state.you)
    val seen = remember { bursts.mapValuesTo(mutableMapOf()) { it.value.key } }
    LaunchedEffect(bursts) {
        bursts.forEach { (player, burst) ->
            if (seen[player] == burst.key) return@forEach
            seen[player] = burst.key
            // The player's own are sounded by their button, as they are pressed.
            if (player != you) reactionCue(burst.reaction)?.let { cues.play(it, volume = 0.7f) }
        }
    }
    val phase = state.phase
    if (phase is GamePhase.Answering) {
        val answered by rememberUpdatedState(phase.myPick != null)
        LaunchedEffect(phase.gameId, phase.question.index) {
            sounding(phase.deadline::remaining, motion.warnSeconds, { answered }, cues)
        }
    }
}

/**
 * The last [warnSeconds] of a question, a tick on each second, and the buzzer when the time is up, for a
 * player who has not [answered]: it ends when the question's time [remaining] does, or when the question
 * ends sooner, which cancels it.
 */
internal suspend fun sounding(
    remaining: () -> Duration,
    warnSeconds: Int,
    answered: () -> Boolean,
    cues: Cues,
) {
    val warn = warnSeconds.seconds
    var last = Int.MAX_VALUE
    while (true) {
        val left = remaining()
        val seconds = ceil(left / 1.seconds).toInt()
        if (seconds <= 0) {
            if (!answered()) cues.play(Cue.TIME_UP)
            return
        }
        if (left > warn) {
            // Nothing to hear yet: sleep to the first second that is.
            delay(left - warn + 1.milliseconds)
            continue
        }
        if (seconds < last) {
            last = seconds
            if (!answered()) cues.play(Cue.TICK)
        }
        // To the next whole second.
        val toNext = left - (seconds - 1).seconds
        delay(toNext + 1.milliseconds)
    }
}

/**
 * The sounds of coming into the game and going out of it, whichever screen the player is on: the welcome on
 * taking a seat, the boot's thud on being put out, an error when a seat could not be taken. What is so when
 * it first draws, after a rotation, is how things stand: nothing is welcomed or thudded twice.
 */
@Composable
internal fun SessionSounds(
    state: LobbySessionState,
    entry: Entry,
) {
    val cues = LocalCues.current
    var seenState by remember { mutableStateOf(state) }
    var seenEntry by remember { mutableStateOf(entry) }
    LaunchedEffect(state) {
        val was = seenState
        seenState = state
        if (state is LobbySessionState.InLobby && was !is LobbySessionState.InLobby) cues.play(Cue.WELCOME)
        val exit = (state as? LobbySessionState.Ended)?.exit
        if (was !is LobbySessionState.Ended && (exit == LobbyExit.KICKED || exit == LobbyExit.VOTED_OUT)) {
            cues.play(Cue.KICKED)
        }
    }
    LaunchedEffect(entry) {
        if (entry != seenEntry && entry is Entry.Failed) cues.play(Cue.ERROR)
        seenEntry = entry
    }
}
