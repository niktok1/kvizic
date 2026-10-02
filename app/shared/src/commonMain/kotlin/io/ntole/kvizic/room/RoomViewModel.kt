package io.ntole.kvizic.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ntole.kvizic.core.domain.analytics.Analytics
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.core.domain.error.DomainError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.GameResults
import io.ntole.kvizic.core.domain.lobby.LobbyEvent
import io.ntole.kvizic.core.domain.lobby.LobbySession
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.domain.lobby.NoticeKind
import io.ntole.kvizic.core.domain.lobby.RefusalReason
import io.ntole.kvizic.core.domain.report.QuestionReportReason
import io.ntole.kvizic.core.domain.report.ReportQuestion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** How a seat is taken: the first open public room, a solo run, a room of the player's own, or a code. */
enum class EntryWay(
    internal val key: String,
) {
    QUICK_PLAY("quick_play"),
    SOLO("solo"),
    CREATE("create"),
    JOIN("join"),
}

/** How taking a seat stands: none asked, one under way, or why the last one could not. */
sealed interface Entry {
    data object None : Entry

    data class Taking(
        val way: EntryWay,
    ) : Entry

    data class Failed(
        val way: EntryWay,
        val error: DomainError,
        val retryAfter: Duration? = null,
    ) : Entry
}

/**
 * A line the room shows: a notice of the server's, a command it refused, or a change in the room. Most show a
 * moment; a note that [asks] stays up until the player answers it, or its time is over.
 */
sealed interface RoomNote {
    data object OnlyHost : RoomNote

    data object Refused : RoomNote

    data object TopicsToppedUp : RoomNote

    data object GameShortened : RoomNote

    data object ServerRestarting : RoomNote

    data object Reported : RoomNote

    data object ReportFailed : RoomNote

    data object VoteTooSoon : RoomNote

    data object CodeCopied : RoomNote

    /** [name] is the host now: the player themselves when it is [you]. */
    data class HostChanged(
        val name: String,
        val you: Boolean,
    ) : RoomNote

    /** The host changed the room's settings, from [before] to [after]. */
    data class SettingsChanged(
        val before: LobbySettings,
        val after: LobbySettings,
    ) : RoomNote

    /** The player's hosting passes to another soon, for lack of anything done, unless they say they still wait. */
    data object HostIdle : RoomNote

    /** The room closes soon, for lack of anything done, unless a member says they stay. */
    data object RoomIdle : RoomNote
}

/** Whether the note asks the player something, and so stays up until they answer or its time is over. */
val RoomNote.asks: Boolean get() = this is RoomNote.HostIdle || this is RoomNote.RoomIdle

/**
 * A reaction one member sent, a key that tells it from the one before, so the same reaction bursts again,
 * and when it arrived ([at]), so a screen composed anew goes on with its burst rather than playing it again.
 */
data class Burst(
    val reaction: String,
    val key: Int,
    val at: TimeMark = TimeSource.Monotonic.markNow(),
)

/** How many play now, and how many look for a game, as the server last said. */
data class Presence(
    val online: Int,
    val searching: Int,
)

/**
 * The room this device is in, for the app's life, as one [LobbySession] keeps it: taking a seat, which
 * may fail ([entry]), everything said in the room meanwhile, and the commands a member sends. [state] is the
 * session's own. A notice or a refusal shows a moment as [note]; each member's last reaction is in
 * [bursts]; how many play and look for a game, once the server says, is [presence].
 *
 * Reports to [analytics] each room entered and left and each game played to its end, by none of its words.
 */
class RoomViewModel(
    private val session: LobbySession,
    private val reportQuestion: ReportQuestion,
    private val analytics: Analytics,
) : ViewModel() {
    val state: StateFlow<LobbySessionState> = session.state

    private val mutableEntry = MutableStateFlow<Entry>(Entry.None)
    val entry: StateFlow<Entry> = mutableEntry.asStateFlow()

    private val mutableNote = MutableStateFlow<RoomNote?>(null)
    val note: StateFlow<RoomNote?> = mutableNote.asStateFlow()

    private val mutableBursts = MutableStateFlow<Map<String, Burst>>(emptyMap())
    val bursts: StateFlow<Map<String, Burst>> = mutableBursts.asStateFlow()

    private val mutablePresence = MutableStateFlow<Presence?>(null)
    val presence: StateFlow<Presence?> = mutablePresence.asStateFlow()

    private var taking: Job? = null
    private var noteShown: Job? = null
    private var burstKey = 0

    /** The last game whose end is reported, so a lobby's results seen again report nothing. */
    private var reportedGame: String? = null

    init {
        viewModelScope.launch { session.events.collect(::heard) }
        viewModelScope.launch {
            var inRoom = false
            var before: LobbySessionState.InLobby? = null
            session.state.collect { state ->
                when (state) {
                    is LobbySessionState.InLobby -> {
                        inRoom = true
                        reportFinished(state)
                        announce(before, state)
                        before = state
                    }

                    is LobbySessionState.Ended -> {
                        if (inRoom) {
                            analytics.track(
                                AnalyticsEvent.ROOM_EXITED,
                                mapOf(AnalyticsProperty.EXIT to state.exit.name),
                            )
                        }
                        inRoom = false
                        before = null
                        mutableBursts.value = emptyMap()
                    }

                    LobbySessionState.Idle -> {
                        inRoom = false
                        before = null
                        mutableBursts.value = emptyMap()
                    }

                    is LobbySessionState.Joining -> {
                        before = null
                    }
                }
            }
        }
    }

    fun quickPlay() = take(EntryWay.QUICK_PLAY) { session.quickPlay() }

    fun solo() = take(EntryWay.SOLO) { session.solo() }

    fun create(settings: LobbySettings) = take(EntryWay.CREATE) { session.create(settings) }

    fun join(code: String) = take(EntryWay.JOIN) { session.join(code) }

    /** Takes the last failure to take a seat down, as a screen does once it has shown it. */
    fun dismissEntryFailure() {
        mutableEntry.update { if (it is Entry.Failed) Entry.None else it }
    }

    fun answer(option: Int) = session.answer(option)

    fun start() = session.start()

    fun updateSettings(settings: LobbySettings) = session.updateSettings(settings)

    fun kick(playerId: String) = session.kick(playerId)

    /** Votes [playerId] out of the room, or, for none, takes the vote back. */
    fun voteKick(playerId: String?) = session.voteKick(playerId)

    fun transferHost(playerId: String) = session.transferHost(playerId)

    fun backToLobby() = session.backToLobby()

    fun react(reaction: String) = session.react(reaction)

    /** The player says they still wait, to the note that asked: the host's time and the room's start over. */
    fun stay() {
        session.stay()
        if (mutableNote.value?.asks == true) clearNote()
    }

    /** Leaves the room for good, or takes down how the last one ended. */
    fun leave() {
        taking?.cancel()
        mutableEntry.value = Entry.None
        session.leave()
    }

    /** The room's code was copied: the room says so a moment. */
    fun codeCopied() = show(RoomNote.CodeCopied)

    /** The app came back to the foreground: a connection being made again is tried now. */
    fun wake() = session.wake()

    /** Reports question [questionId] for [reason]; the room says a moment whether it went. */
    fun report(
        questionId: String,
        reason: QuestionReportReason,
    ) {
        viewModelScope.launch {
            try {
                reportQuestion(questionId, reason)
                show(RoomNote.Reported)
            } catch (failure: KvizicException) {
                analytics.track(
                    AnalyticsEvent.ERROR_SHOWN,
                    mapOf(AnalyticsProperty.CODE to failure.error.name, AnalyticsProperty.ACTION to "report"),
                )
                show(RoomNote.ReportFailed)
            }
        }
    }

    /** Takes a seat [way], unless one is being taken already, and says why when it could not. */
    private fun take(
        way: EntryWay,
        block: suspend () -> Unit,
    ) {
        if (taking?.isActive == true) return
        mutableEntry.value = Entry.Taking(way)
        taking =
            viewModelScope.launch {
                try {
                    block()
                    analytics.track(AnalyticsEvent.ROOM_ENTERED, mapOf(AnalyticsProperty.WAY to way.key))
                    // The seat is taken, but the room opens once its socket says what is in it: until then the
                    // seat is still being taken, so the screen it was asked from does not light up again in
                    // between. A socket that does not come gives that screen back after a while.
                    withTimeoutOrNull(OPENING_WAIT) { session.state.first { it !is LobbySessionState.Joining } }
                    mutableEntry.value = Entry.None
                } catch (failure: KvizicException) {
                    analytics.track(
                        AnalyticsEvent.ERROR_SHOWN,
                        mapOf(AnalyticsProperty.CODE to failure.error.name, AnalyticsProperty.ACTION to way.key),
                    )
                    mutableEntry.value = Entry.Failed(way, failure.error, failure.retryAfter)
                }
            }
    }

    private fun heard(event: LobbyEvent) {
        when (event) {
            is LobbyEvent.Reacted -> {
                burstKey++
                mutableBursts.update { it + (event.playerId to Burst(event.reaction, burstKey)) }
            }

            is LobbyEvent.Presence -> {
                mutablePresence.value = Presence(event.online, event.searching)
            }

            is LobbyEvent.Notice -> {
                noteOf(event.kind)?.let { note ->
                    // What is about to happen is shown for as long as it takes to, as the server counted it.
                    show(note, if (note.asks) event.remainingMs?.milliseconds ?: ASKED_SHOWN else NOTE_SHOWN)
                }
            }

            is LobbyEvent.Refused -> {
                show(
                    when (event.reason) {
                        RefusalReason.NOT_HOST -> RoomNote.OnlyHost
                        RefusalReason.TOO_SOON -> RoomNote.VoteTooSoon
                        else -> RoomNote.Refused
                    },
                )
            }
        }
    }

    private fun noteOf(kind: NoticeKind): RoomNote? =
        when (kind) {
            NoticeKind.SERVER_RESTARTING -> RoomNote.ServerRestarting
            NoticeKind.TOPICS_TOPPED_UP -> RoomNote.TopicsToppedUp
            NoticeKind.GAME_SHORTENED -> RoomNote.GameShortened
            NoticeKind.HOST_IDLE -> RoomNote.HostIdle
            NoticeKind.ROOM_IDLE -> RoomNote.RoomIdle
            NoticeKind.UNKNOWN -> null
        }

    private fun show(
        note: RoomNote,
        shownFor: Duration = NOTE_SHOWN,
    ) {
        // A question put to the player stays up for its time: a line that matters less does not take it down.
        if (mutableNote.value?.asks == true && !note.asks) return
        mutableNote.value = note
        noteShown?.cancel()
        noteShown =
            viewModelScope.launch {
                delay(shownFor)
                mutableNote.value = null
            }
    }

    private fun clearNote() {
        noteShown?.cancel()
        mutableNote.value = null
    }

    /**
     * Says what changed in the room since [before], for the others in it: who the host is now, or what the
     * host did to the settings, and takes down a question to the player that no longer holds.
     */
    private fun announce(
        before: LobbySessionState.InLobby?,
        after: LobbySessionState.InLobby,
    ) {
        val asking = mutableNote.value
        if (asking != null && asking.asks) {
            val waiting = after.phase is GamePhase.Waiting
            val hosting = after.lobby.host == after.you
            if (!waiting || (asking is RoomNote.HostIdle && !hosting)) clearNote()
        }
        if (before == null || before.lobby.id != after.lobby.id) return
        val host = after.lobby.host
        if (host != null && host != before.lobby.host) {
            after.lobby.member(host)?.let { show(RoomNote.HostChanged(it.name, you = host == after.you)) }
        } else if (after.lobby.settings != before.lobby.settings && host != after.you) {
            show(RoomNote.SettingsChanged(before.lobby.settings, after.lobby.settings))
        }
    }

    /** Reports the end of a game this player played, once, as its results come. */
    private fun reportFinished(state: LobbySessionState.InLobby) {
        val results = resultsOf(state.phase) ?: return
        if (results.gameId == reportedGame) return
        reportedGame = results.gameId
        val own = results.standings.firstOrNull { it.playerId == state.you } ?: return
        analytics.track(
            AnalyticsEvent.GAME_FINISHED,
            mapOf(
                AnalyticsProperty.RANK to own.rank,
                AnalyticsProperty.PLAYERS to results.standings.size,
                AnalyticsProperty.QUESTIONS to results.questionCount,
                AnalyticsProperty.SOLO to (results.personalBest != null),
            ),
        )
    }

    private fun resultsOf(phase: GamePhase): GameResults? =
        when (phase) {
            is GamePhase.Waiting -> phase.lastResults
            is GamePhase.Countdown -> phase.lastResults
            else -> null
        }

    private companion object {
        /** How long a note shows. */
        val NOTE_SHOWN = 4.seconds

        /** How long a question to the player stays up, when the server does not say how long it has. */
        val ASKED_SHOWN = 30.seconds

        /** The longest a seat taken waits for its room to open before its screen takes taps again. */
        val OPENING_WAIT = 10.seconds
    }
}
