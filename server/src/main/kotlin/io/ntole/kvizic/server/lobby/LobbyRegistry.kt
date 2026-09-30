package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.protocol.LeaveReason
import io.ntole.kvizic.server.admin.LiveCounts
import io.ntole.kvizic.server.plugins.ApiFailure
import io.ntole.kvizic.server.rules.ScoringRules
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/** A seat held, and the ticket its socket presents. */
data class Joined(
    val lobby: Lobby,
    val ticket: String,
)

/**
 * Every live lobby on this instance, by id and by code, and which lobby each player is in: one at a
 * time, so joining one leaves the one before. Everything that changes membership through REST, a
 * create, a join, Quick play or a solo run, goes one at a time under one lock; each is a message to a
 * lobby and back, so the lock is held for moments. The public list, Quick play and presence read the
 * lobbies' published summaries, never their state.
 */
class LobbyRegistry(
    private val scope: CoroutineScope,
    private val timeSource: TimeSource.WithComparableMarks,
    private val timings: GameTimings,
    private val limits: LobbyLimits,
    private val scoring: ScoringRules,
    private val questions: QuestionSource,
    private val results: ResultSink,
    private val topics: () -> Set<String>,
    private val random: Random = Random.Default,
) {
    val tickets = Tickets(timeSource, timings.ticketTtl)

    private val lobbies = ConcurrentHashMap<String, Lobby>()
    private val byCode = ConcurrentHashMap<String, String>()
    private val recentlyClosed = ConcurrentHashMap<String, ComparableTimeMark>()
    private val playerLobby = ConcurrentHashMap<String, String>()
    private val lock = Mutex()
    private val opened = AtomicLong()

    @Volatile
    var draining: Boolean = false
        private set

    private val events =
        object : LobbyEvents {
            override fun memberGone(
                lobby: Lobby,
                playerId: String,
            ) {
                playerLobby.remove(playerId, lobby.id)
            }

            override fun closed(lobby: Lobby) {
                lobbies.remove(lobby.id)
                byCode.remove(lobby.code, lobby.id)
                recentlyClosed[lobby.code] = timeSource.markNow()
                playerLobby.values.removeIf { it == lobby.id }
            }
        }

    /** Starts the registry's own loop: presence for waiting lobbies, and forgetting what expired. */
    fun start(): Job =
        scope.launch(CoroutineName("lobby-registry")) {
            while (isActive) {
                delay(timings.pingEvery)
                tickets.sweep()
                val now = timeSource.markNow()
                recentlyClosed.entries.removeIf { now - it.value >= CODE_COOLDOWN }
                val presence = presence()
                lobbies.values.forEach { it.send(LobbyCommand.Presence(presence.first, presence.second)) }
            }
        }

    fun lobby(id: String): Lobby? = lobbies[id]

    /** Opens a lobby with [seat] as its host. */
    suspend fun create(
        seat: Seat,
        settings: LobbySettingsDto,
        kind: LobbyKind,
    ): Joined =
        lock.withLock {
            refuseWhileDraining()
            if (lobbies.size >= limits.maxLobbies) throw ApiFailure.tooManyLobbies()
            val lobby = open(settings, kind)
            seatIn(lobby, seat)
        }

    /** A seat in the lobby with [code], or the one the player holds there already. */
    suspend fun join(
        seat: Seat,
        code: String,
    ): Joined =
        lock.withLock {
            refuseWhileDraining()
            val lobby = byCode[code]?.let { lobbies[it] } ?: throw ApiFailure.lobbyNotFound()
            // A solo run has a code only for its own player's rejoin: nobody else learns it is there.
            if (lobby.summary.value.kind == LobbyKind.SOLO && lobbyOf(seat.playerId) != lobby) {
                throw ApiFailure.lobbyNotFound()
            }
            seatIn(lobby, seat)
        }

    /** A seat for the player in the lobby they are in, for a rejoin: none when they are in none. */
    fun lobbyOf(playerId: String): Lobby? = playerLobby[playerId]?.let { lobbies[it] }

    /**
     * Quick play: the player's own public lobby if they wait in one; otherwise the fullest waiting public
     * lobby with room, the oldest first among equals, then one mid-game with room, whose next game they
     * play; otherwise a new public lobby of the default settings with them as its host.
     */
    suspend fun quickPlay(seat: Seat): Joined =
        lock.withLock {
            refuseWhileDraining()
            lobbyOf(seat.playerId)?.takeIf { it.kind == LobbyKind.PUBLIC }?.let { return@withLock seatIn(it, seat) }
            val candidates =
                lobbies.values
                    .map { it to it.summary.value }
                    .filter { (_, summary) ->
                        summary.kind == LobbyKind.PUBLIC && !summary.draining && !summary.closed &&
                            summary.hasRoom
                    }.sortedWith(
                        compareBy<Pair<Lobby, LobbySummary>> { it.second.inGame }
                            .thenByDescending { it.second.seatsTaken }
                            .thenBy { it.second.openedOrder },
                    ).take(QUICK_PLAY_TRIES)
            for ((lobby, _) in candidates) {
                val joined = trySeat(lobby, seat)
                if (joined != null) return@withLock joined
            }
            if (lobbies.size >= limits.maxLobbies) throw ApiFailure.tooManyLobbies()
            seatIn(open(LobbySettingsDto.QUICK_PLAY, LobbyKind.PUBLIC), seat)
        }

    /** A solo run: a one-seat lobby of the fixed format, never listed. */
    suspend fun solo(seat: Seat): Joined =
        lock.withLock {
            refuseWhileDraining()
            lobbyOf(seat.playerId)?.takeIf { it.kind == LobbyKind.SOLO }?.let { return@withLock seatIn(it, seat) }
            if (lobbies.size >= limits.maxLobbies) throw ApiFailure.tooManyLobbies()
            seatIn(open(LobbySettingsDto.SOLO, LobbyKind.SOLO), seat)
        }

    /** The open public lobbies for the list: waiting ones first, fullest first, at most [PUBLIC_LIST_SIZE]. */
    fun publicList(): List<LobbySummary> =
        lobbies.values
            .map { it.summary.value }
            .filter { it.kind == LobbyKind.PUBLIC && !it.closed && !it.draining && it.seatsTaken > 0 }
            .sortedWith(
                compareBy<LobbySummary> { it.inGame }.thenByDescending { it.seatsTaken }.thenBy { it.openedOrder },
            ).take(PUBLIC_LIST_SIZE)

    /** Players online, with a socket in any lobby, and searching, waiting in a public lobby. */
    fun presence(): Pair<Int, Int> {
        val summaries = lobbies.values.map { it.summary.value }
        val online = summaries.flatMap { it.connected }.toSet().size
        val searching = summaries.filter { it.kind == LobbyKind.PUBLIC && !it.inGame }.sumOf { it.connected.size }
        return online to searching
    }

    fun live(): LiveCounts {
        val summaries = lobbies.values.map { it.summary.value }
        return LiveCounts(
            lobbies = summaries.size,
            games = summaries.count { it.inGame },
            connectedPlayers = summaries.flatMap { it.connected }.toSet().size,
        )
    }

    /**
     * Takes [playerId] out of their lobby at once: their session [sessionId] ended, a logout, while it held
     * their seat; or, with none, their account is gone.
     */
    fun sessionEnded(
        playerId: String,
        sessionId: String?,
    ) {
        lobbyOf(playerId)?.send(LobbyCommand.Remove(playerId, LeaveReason.SESSION_ENDED, sessionId))
    }

    /**
     * Stops taking new seats and tells every lobby the server stops by [deadline]; returns once every
     * lobby has closed, or at the deadline.
     */
    suspend fun drain(deadline: ComparableTimeMark) {
        draining = true
        lobbies.values.forEach { it.send(LobbyCommand.Drain(deadline)) }
        val left = deadline - timeSource.markNow()
        if (left.isPositive()) {
            withTimeoutOrNull(left) {
                while (lobbies.isNotEmpty()) delay(DRAIN_POLL)
            }
        }
    }

    val lobbyCount: Int get() = lobbies.size

    private fun refuseWhileDraining() {
        if (draining) throw ApiFailure.draining()
    }

    private fun open(
        settings: LobbySettingsDto,
        kind: LobbyKind,
    ): Lobby {
        val now = timeSource.markNow()
        val code =
            LobbyCode.generate { candidate ->
                byCode.containsKey(candidate) || recentlyClosed[candidate]?.let { now - it < CODE_COOLDOWN } == true
            }
        val lobby =
            Lobby(
                id = UUID.randomUUID().toString(),
                code = code,
                kind = kind,
                settings = settings,
                openedOrder = opened.incrementAndGet(),
                env =
                    LobbyEnvironment(
                        scope = scope,
                        timeSource = timeSource,
                        timings = timings,
                        scoring = scoring,
                        questions = questions,
                        results = results,
                        knownTopics = topics,
                        random = random,
                        events = events,
                    ),
            )
        lobbies[lobby.id] = lobby
        byCode[code] = lobby.id
        lobby.start()
        return lobby
    }

    /** Seats [seat] in [lobby], leaving the one they were in, or throws what refused it. */
    private suspend fun seatIn(
        lobby: Lobby,
        seat: Seat,
    ): Joined =
        when (val result = seatOrRefusal(lobby, seat)) {
            is Seated -> result.joined
            is Refused -> throw result.failure
        }

    /** [seatIn], but null rather than a refusal, for Quick play to try the next lobby. */
    private suspend fun trySeat(
        lobby: Lobby,
        seat: Seat,
    ): Joined? = (seatOrRefusal(lobby, seat) as? Seated)?.joined

    private suspend fun seatOrRefusal(
        lobby: Lobby,
        seat: Seat,
    ): SeatOutcome {
        val result = lobby.reserve(seat)
        if (result != ReserveResult.Reserved) {
            return Refused(
                when (result) {
                    ReserveResult.Full -> ApiFailure.lobbyFull()
                    ReserveResult.Banned -> ApiFailure.lobbyBanned()
                    ReserveResult.Draining -> ApiFailure.draining()
                    else -> ApiFailure.lobbyNotFound()
                },
            )
        }
        val previous = playerLobby.put(seat.playerId, lobby.id)
        if (previous != null && previous != lobby.id) {
            lobbies[previous]?.send(LobbyCommand.Remove(seat.playerId, LeaveReason.JOINED_ANOTHER))
        }
        return Seated(Joined(lobby, tickets.issue(seat.playerId, seat.sessionId, lobby.id)))
    }

    private sealed interface SeatOutcome

    private class Seated(
        val joined: Joined,
    ) : SeatOutcome

    private class Refused(
        val failure: ApiFailure,
    ) : SeatOutcome

    private companion object {
        /** How long a closed lobby's code is not reused, so an old link never lands in a stranger's lobby. */
        val CODE_COOLDOWN: Duration = 30.minutes
        const val QUICK_PLAY_TRIES = 3
        const val PUBLIC_LIST_SIZE = 50
        val DRAIN_POLL: Duration = 100.milliseconds
    }
}
