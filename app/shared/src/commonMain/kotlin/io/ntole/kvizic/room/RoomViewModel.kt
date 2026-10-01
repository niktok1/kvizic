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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
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

/** A line the room shows for a moment: a notice of the server's, or a command it refused. */
enum class RoomNote {
    ONLY_HOST,
    REFUSED,
    TOPICS_TOPPED_UP,
    GAME_SHORTENED,
    SERVER_RESTARTING,
    REPORTED,
    REPORT_FAILED,
    VOTE_TOO_SOON,
    CODE_COPIED,
}

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
            session.state.collect { state ->
                when (state) {
                    is LobbySessionState.InLobby -> {
                        inRoom = true
                        reportFinished(state)
                    }

                    is LobbySessionState.Ended -> {
                        if (inRoom) {
                            analytics.track(
                                AnalyticsEvent.ROOM_EXITED,
                                mapOf(AnalyticsProperty.EXIT to state.exit.name),
                            )
                        }
                        inRoom = false
                        mutableBursts.value = emptyMap()
                    }

                    LobbySessionState.Idle -> {
                        inRoom = false
                        mutableBursts.value = emptyMap()
                    }

                    is LobbySessionState.Joining -> {
                        Unit
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

    /** Leaves the room for good, or takes down how the last one ended. */
    fun leave() {
        taking?.cancel()
        mutableEntry.value = Entry.None
        session.leave()
    }

    /** The room's code was copied: the room says so a moment. */
    fun codeCopied() = show(RoomNote.CODE_COPIED)

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
                show(RoomNote.REPORTED)
            } catch (failure: KvizicException) {
                analytics.track(
                    AnalyticsEvent.ERROR_SHOWN,
                    mapOf(AnalyticsProperty.CODE to failure.error.name, AnalyticsProperty.ACTION to "report"),
                )
                show(RoomNote.REPORT_FAILED)
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
                    mutableEntry.value = Entry.None
                    analytics.track(AnalyticsEvent.ROOM_ENTERED, mapOf(AnalyticsProperty.WAY to way.key))
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
                noteOf(event.kind)?.let(::show)
            }

            is LobbyEvent.Refused -> {
                show(
                    when (event.reason) {
                        RefusalReason.NOT_HOST -> RoomNote.ONLY_HOST
                        RefusalReason.TOO_SOON -> RoomNote.VOTE_TOO_SOON
                        else -> RoomNote.REFUSED
                    },
                )
            }
        }
    }

    private fun noteOf(kind: NoticeKind): RoomNote? =
        when (kind) {
            NoticeKind.SERVER_RESTARTING -> RoomNote.SERVER_RESTARTING
            NoticeKind.TOPICS_TOPPED_UP -> RoomNote.TOPICS_TOPPED_UP
            NoticeKind.GAME_SHORTENED -> RoomNote.GAME_SHORTENED
            NoticeKind.UNKNOWN -> null
        }

    private fun show(note: RoomNote) {
        mutableNote.value = note
        noteShown?.cancel()
        noteShown =
            viewModelScope.launch {
                delay(NOTE_SHOWN)
                mutableNote.value = null
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
    }
}
