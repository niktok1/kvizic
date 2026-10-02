package io.ntole.kvizic.core.data.lobby

import io.ntole.kvizic.core.data.mapper.runApi
import io.ntole.kvizic.core.data.session.DefaultSessionRepository
import io.ntole.kvizic.core.data.session.withSessionRecovery
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.GameError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.LobbyCommandKind
import io.ntole.kvizic.core.domain.lobby.LobbyEvent
import io.ntole.kvizic.core.domain.lobby.LobbyExit
import io.ntole.kvizic.core.domain.lobby.LobbySession
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.lobby.TicketDto
import io.ntole.kvizic.core.network.UpgradeSignal
import io.ntole.kvizic.core.network.api.LobbyApi
import io.ntole.kvizic.core.network.realtime.PlayConnection
import io.ntole.kvizic.core.network.realtime.PlayTransport
import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.CloseReason
import io.ntole.kvizic.core.protocol.ServerMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** How often, and how patiently, a lost connection is made again. */
public data class ReconnectPolicy(
    val firstWait: Duration = 500.milliseconds,
    val longestWait: Duration = 10.seconds,
    /** A wait is this share longer or shorter at random, so a server's players do not come back in step. */
    val jitter: Double = 0.2,
    /** Tries in a row with no connection before the player is told it is lost. */
    val attempts: Int = 15,
    /** A socket that has heard nothing this many pings long is dead, and made again. */
    val silentPings: Int = 3,
) {
    public companion object {
        public val DEFAULT: ReconnectPolicy = ReconnectPolicy()
    }
}

/**
 * The lobby this device is in, as one coroutine per membership that owns the socket: it takes the seat
 * over REST, opens the socket with the ticket, keeps the state from the snapshot and every change after
 * it ([LobbyReducer]), answers the server's pings, and when the connection drops takes a new ticket by
 * the same code and connects again, backing off with jitter, until the lobby lets the player go.
 *
 * An answer shows at once. Should the connection drop before the server has it, it is sent again once
 * the snapshot shows the same question still open and no answer of this player's in it.
 *
 * Everything it keeps is touched on [confined] alone, one thread at a time: the socket's loop runs
 * there, and every call from the UI is posted there, in the order it was made.
 */
public class DefaultLobbySession(
    private val api: LobbyApi,
    private val transport: PlayTransport,
    private val session: DefaultSessionRepository,
    private val scope: CoroutineScope,
    private val upgrade: UpgradeSignal? = null,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    private val random: Random = Random.Default,
    private val policy: ReconnectPolicy = ReconnectPolicy.DEFAULT,
    private val confined: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) : LobbySession {
    private val mutableState = MutableStateFlow<LobbySessionState>(LobbySessionState.Idle)
    override val state: StateFlow<LobbySessionState> = mutableState.asStateFlow()

    private val mutableEvents =
        MutableSharedFlow<LobbyEvent>(extraBufferCapacity = EVENT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: Flow<LobbyEvent> = mutableEvents.asSharedFlow()

    private val seats = Mutex()
    private var membership: Membership? = null

    /** One stay in one lobby: the coroutine that runs it and whatever socket it has open now. */
    private inner class Membership(
        val code: String,
    ) {
        lateinit var job: Job
        var live: Live? = null
        var model: LobbyModel? = null
        var pending: PendingAnswer? = null
        var leaving = false
        val wake = Channel<Unit>(Channel.CONFLATED)
    }

    /** An open socket, and the commands sent on it still waiting for the lobby's word. */
    private class Live(
        val connection: PlayConnection,
    ) {
        private var ids = 0
        val sent = mutableMapOf<Int, LobbyCommandKind>()
        val outbox = Channel<ClientMessage>(Channel.UNLIMITED)

        fun nextId(): Int = ++ids
    }

    private data class PendingAnswer(
        val index: Int,
        val option: Int,
    )

    override suspend fun create(settings: LobbySettings): Unit = enter(code = null) { api.create(settings.toDto()) }

    override suspend fun join(code: String): Unit = enter(code) { api.join(code) }

    override suspend fun quickPlay(): Unit = enter(code = null) { api.quickPlay() }

    override suspend fun solo(): Unit = enter(code = null) { api.solo() }

    private suspend fun enter(
        code: String?,
        seat: suspend () -> TicketDto,
    ): Unit = seats.withLock { withContext(confined) { take(code, seat) } }

    private suspend fun take(
        code: String?,
        seat: suspend () -> TicketDto,
    ) {
        // The server moves a player's seat when they take another: this device lets the old one go first.
        membership?.let { old ->
            old.leaving = true
            old.job.cancelAndJoin()
        }
        membership = null
        mutableState.value = LobbySessionState.Joining(code)
        val ticket =
            try {
                session.ensure()
                session.withSessionRecovery { seat() }
            } catch (failure: Throwable) {
                if (mutableState.value is LobbySessionState.Joining) mutableState.value = LobbySessionState.Idle
                throw failure
            }
        val stay = Membership(ticket.code)
        membership = stay
        stay.job = scope.launch(confined) { run(stay, ticket) }
    }

    override fun answer(option: Int): Unit =
        onConfined {
            val stay = membership ?: return@onConfined
            val shown = mutableState.value as? LobbySessionState.InLobby ?: return@onConfined
            val answering = shown.phase as? GamePhase.Answering ?: return@onConfined
            if (answering.myPick != null || option !in answering.options.indices) return@onConfined
            stay.pending = PendingAnswer(answering.question.index, option)
            publish(stay)
            stay.live?.let { live ->
                send(live, LobbyCommandKind.ANSWER) { id -> ClientMessage.Answer(id, answering.question.index, option) }
            }
        }

    override fun start(): Unit = command(LobbyCommandKind.START) { ClientMessage.Start(it) }

    override fun updateSettings(settings: LobbySettings): Unit =
        command(LobbyCommandKind.SETTINGS) { ClientMessage.UpdateSettings(it, settings.toDto()) }

    override fun kick(playerId: String): Unit = command(LobbyCommandKind.KICK) { ClientMessage.Kick(it, playerId) }

    override fun voteKick(playerId: String?): Unit =
        command(LobbyCommandKind.VOTE_KICK) {
            ClientMessage.VoteKick(it, playerId)
        }

    override fun transferHost(playerId: String): Unit =
        command(LobbyCommandKind.TRANSFER_HOST) {
            ClientMessage.TransferHost(it, playerId)
        }

    override fun backToLobby(): Unit = command(LobbyCommandKind.BACK_TO_LOBBY) { ClientMessage.BackToLobby(it) }

    override fun react(reaction: String): Unit =
        onConfined {
            membership?.live?.outbox?.trySend(ClientMessage.React(reaction))
        }

    override fun stay(): Unit =
        onConfined {
            membership?.live?.outbox?.trySend(ClientMessage.Stay)
        }

    override fun leave(): Unit =
        onConfined {
            val stay = membership
            membership = null
            mutableState.value = LobbySessionState.Idle
            stay ?: return@onConfined
            stay.leaving = true
            // Said once, if there is a socket to say it on; the server keeps the seat through its grace otherwise.
            stay.live?.outbox?.trySend(ClientMessage.Leave)
            withTimeoutOrNull(LEAVE_WAIT) { stay.job.join() }
            stay.job.cancel()
        }

    override fun wake(): Unit = onConfined { membership?.wake?.trySend(Unit) }

    private fun command(
        kind: LobbyCommandKind,
        build: (Int) -> ClientMessage,
    ): Unit = onConfined { membership?.live?.let { live -> send(live, kind, build) } }

    /** Runs [block] on [confined], after whatever was posted there before it. */
    private fun onConfined(block: suspend () -> Unit) {
        scope.launch(confined) { block() }
    }

    private fun send(
        live: Live,
        kind: LobbyCommandKind,
        build: (Int) -> ClientMessage,
    ) {
        val id = live.nextId()
        live.sent[id] = kind
        live.outbox.trySend(build(id))
    }

    // --- The membership's coroutine ---

    private suspend fun run(
        stay: Membership,
        first: TicketDto,
    ) {
        var ticket: TicketDto? = first
        var failures = 0
        while (true) {
            val current =
                ticket ?: when (val rejoined = rejoin(stay)) {
                    is Rejoin.Seated -> rejoined.ticket
                    is Rejoin.Exit -> return end(stay, rejoined.exit)
                    Rejoin.Retry -> null
                }
            ticket = null
            if (current != null) {
                val connection =
                    try {
                        transport.open(current.ticket)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failed: Exception) {
                        null
                    }
                if (connection != null) {
                    val outcome =
                        try {
                            connect(stay, connection)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failed: Exception) {
                            // Whatever a transport throws mid-socket is a connection lost, never the app's
                            // end: this coroutine has no handler above it.
                            Outcome(exit = null, welcomed = false)
                        }
                    if (outcome.exit != null) return end(stay, outcome.exit)
                    if (outcome.welcomed) failures = 0
                }
            }
            if (stay.leaving) return
            failures++
            if (failures > policy.attempts) return end(stay, LobbyExit.CONNECTION_LOST)
            publish(stay, reconnecting = true)
            withTimeoutOrNull(waitBefore(failures)) { stay.wake.receive() }
        }
    }

    private sealed interface Rejoin {
        data class Seated(
            val ticket: TicketDto,
        ) : Rejoin

        data class Exit(
            val exit: LobbyExit,
        ) : Rejoin

        data object Retry : Rejoin
    }

    /** A new ticket for the same seat, by the lobby's code, or why there is none. Never a fresh guest. */
    private suspend fun rejoin(stay: Membership): Rejoin =
        try {
            Rejoin.Seated(runApi { api.join(stay.code) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: KvizicException) {
            when (failure.error) {
                GameError.LOBBY_NOT_FOUND, GameError.LOBBY_FULL -> {
                    Rejoin.Exit(LobbyExit.LOBBY_GONE)
                }

                GameError.LOBBY_BANNED -> {
                    Rejoin.Exit(LobbyExit.KICKED)
                }

                GameError.SERVER_DRAINING -> {
                    Rejoin.Exit(LobbyExit.SERVER_RESTARTING)
                }

                CoreError.UPGRADE_REQUIRED -> {
                    Rejoin.Exit(LobbyExit.UPGRADE_REQUIRED)
                }

                CoreError.UNAUTHORIZED -> {
                    Rejoin.Exit(LobbyExit.SESSION_ENDED)
                }

                CoreError.RATE_LIMITED -> {
                    delay(failure.retryAfter ?: policy.longestWait)
                    Rejoin.Retry
                }

                else -> {
                    Rejoin.Retry
                }
            }
        }

    private class Outcome(
        val exit: LobbyExit?,
        val welcomed: Boolean,
    )

    /** One socket, from its hello to its close: what it ended with, and whether it ever got in. */
    private suspend fun connect(
        stay: Membership,
        connection: PlayConnection,
    ): Outcome {
        val live = Live(connection)
        stay.live = live
        var said: CloseReason? = null
        var welcomed = false
        var pingEvery = DEFAULT_PING_EVERY
        var lastHeard = timeSource.markNow()
        try {
            coroutineScope {
                val sender =
                    launch {
                        for (message in live.outbox) {
                            if (!connection.send(message)) break
                        }
                    }
                val watchdog =
                    launch {
                        while (isActive) {
                            val left = pingEvery * policy.silentPings - lastHeard.elapsedNow()
                            if (left <= Duration.ZERO) {
                                connection.close()
                                break
                            }
                            delay(left)
                        }
                    }
                for (message in connection.incoming) {
                    val at = timeSource.markNow()
                    lastHeard = at
                    when (message) {
                        is ServerMessage.Welcome -> {
                            welcomed = true
                            pingEvery = message.pingEveryMs.milliseconds.coerceAtLeast(MIN_PING_EVERY)
                        }

                        is ServerMessage.Snapshot -> {
                            stay.model = LobbyReducer.snapshot(message, at)
                            settlePending(stay, live)
                            publish(stay)
                        }

                        is ServerMessage.StateChange -> {
                            val model = stay.model
                            if (model == null) {
                                live.outbox.trySend(ClientMessage.Resync)
                            } else {
                                when (val step = LobbyReducer.apply(model, message, at)) {
                                    is Step.Changed -> {
                                        stay.model = step.model
                                        settlePending(stay, live = null)
                                        publish(stay)
                                    }

                                    Step.OutOfOrder -> {
                                        live.outbox.trySend(ClientMessage.Resync)
                                    }

                                    Step.Stale -> {
                                        Unit
                                    }
                                }
                            }
                        }

                        is ServerMessage.Ping -> {
                            live.outbox.trySend(ClientMessage.Pong(message.seq))
                        }

                        is ServerMessage.Ack -> {
                            live.sent.remove(message.id)
                        }

                        is ServerMessage.Rejected -> {
                            val kind = message.id?.let { live.sent.remove(it) }
                            if (kind == LobbyCommandKind.ANSWER) {
                                stay.pending = null
                                publish(stay)
                            }
                            if (kind != null) mutableEvents.tryEmit(LobbyEvent.Refused(kind, message.code.toDomain()))
                        }

                        is ServerMessage.Reacted -> {
                            mutableEvents.tryEmit(LobbyEvent.Reacted(message.player, message.reaction))
                        }

                        is ServerMessage.Presence -> {
                            mutableEvents.tryEmit(LobbyEvent.Presence(message.online, message.searching))
                        }

                        is ServerMessage.Notice -> {
                            mutableEvents.tryEmit(LobbyEvent.Notice(message.kind.toDomain(), message.remainingMs))
                        }

                        is ServerMessage.Closing -> {
                            said = message.reason
                        }

                        ServerMessage.Unknown -> {
                            Unit
                        }
                    }
                }
                sender.cancel()
                watchdog.cancel()
            }
            val code = connection.closed()
            val exit = if (stay.leaving) LobbyExit.LEFT else exitFor(code, said)
            return Outcome(exit, welcomed)
        } finally {
            stay.live = null
            live.outbox.close()
            withContext(NonCancellable) { connection.close() }
        }
    }

    /**
     * Keeps this player's answer only while the question it was for is open with no answer of theirs on
     * the server's side, and sends it on [live], a socket just come, when it is.
     */
    private fun settlePending(
        stay: Membership,
        live: Live?,
    ) {
        val pending = stay.pending ?: return
        val answering = stay.model?.phase as? GamePhase.Answering
        when {
            answering == null || answering.question.index != pending.index -> {
                stay.pending = null
            }

            answering.myPick != null -> {
                stay.pending = null
            }

            live != null -> {
                send(live, LobbyCommandKind.ANSWER) { id -> ClientMessage.Answer(id, pending.index, pending.option) }
            }
        }
    }

    private fun publish(
        stay: Membership,
        reconnecting: Boolean = stay.live == null,
    ) {
        if (membership !== stay) return
        val model = stay.model ?: return
        val pending = stay.pending
        val phase =
            when (val phase = model.phase) {
                is GamePhase.Answering -> {
                    if (pending != null && phase.myPick == null &&
                        phase.question.index == pending.index
                    ) {
                        phase.copy(myPick = pending.option)
                    } else {
                        phase
                    }
                }

                else -> {
                    phase
                }
            }
        mutableState.value = LobbySessionState.InLobby(model.you, model.lobby, phase, reconnecting)
    }

    private fun end(
        stay: Membership,
        exit: LobbyExit,
    ) {
        if (exit == LobbyExit.UPGRADE_REQUIRED) upgrade?.raise()
        if (membership !== stay || stay.leaving) return
        membership = null
        mutableState.value = LobbySessionState.Ended(exit, stay.code)
    }

    /** How long before try [failures], from the first wait doubling up to the longest, give or take the jitter. */
    private fun waitBefore(failures: Int): Duration {
        val doubled = policy.firstWait * (1 shl (failures - 1).coerceIn(0, MAX_DOUBLINGS))
        val base = if (doubled > policy.longestWait) policy.longestWait else doubled
        val spread = 1.0 + policy.jitter * (random.nextDouble() * 2 - 1)
        return base * spread
    }

    private companion object {
        const val EVENT_BUFFER = 64
        const val MAX_DOUBLINGS = 10
        val DEFAULT_PING_EVERY = 5.seconds
        val MIN_PING_EVERY = 100.milliseconds
        val LEAVE_WAIT = 3.seconds
    }
}
