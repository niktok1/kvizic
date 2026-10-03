package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.protocol.AbortReason
import io.ntole.kvizic.core.protocol.AnswerResultView
import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.CloseCodes
import io.ntole.kvizic.core.protocol.CloseReason
import io.ntole.kvizic.core.protocol.FinalStandingView
import io.ntole.kvizic.core.protocol.LeaveReason
import io.ntole.kvizic.core.protocol.LobbyView
import io.ntole.kvizic.core.protocol.MemberView
import io.ntole.kvizic.core.protocol.NoticeKind
import io.ntole.kvizic.core.protocol.PersonalBestView
import io.ntole.kvizic.core.protocol.PhaseView
import io.ntole.kvizic.core.protocol.PickView
import io.ntole.kvizic.core.protocol.Protocol
import io.ntole.kvizic.core.protocol.QuestionView
import io.ntole.kvizic.core.protocol.Reactions
import io.ntole.kvizic.core.protocol.RejectCode
import io.ntole.kvizic.core.protocol.ResultsView
import io.ntole.kvizic.core.protocol.RevealView
import io.ntole.kvizic.core.protocol.ServerMessage
import io.ntole.kvizic.core.protocol.StandingView
import io.ntole.kvizic.server.player.Levels
import io.ntole.kvizic.server.rules.ScoringRules
import io.ntole.kvizic.server.rules.Tally
import io.ntole.kvizic.server.rules.answerTime
import io.ntole.kvizic.server.rules.fractionLeft
import io.ntole.kvizic.server.rules.rank
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.random.Random
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** What a lobby needs from outside: time, randomness, questions, where results go, and who to tell. */
class LobbyEnvironment(
    val scope: CoroutineScope,
    val timeSource: TimeSource.WithComparableMarks,
    val timings: GameTimings,
    val scoring: ScoringRules,
    val questions: QuestionSource,
    val results: ResultSink,
    val knownTopics: () -> Set<String>,
    val random: Random = Random.Default,
    val wallClock: () -> Long = System::currentTimeMillis,
    val newId: () -> String = { UUID.randomUUID().toString() },
    val events: LobbyEvents = LobbyEvents.NONE,
)

/** What a lobby tells its registry. Called on the lobby's loop, so it must return at once. */
interface LobbyEvents {
    /** [playerId] is no longer a member. */
    fun memberGone(
        lobby: Lobby,
        playerId: String,
    ) {}

    /** The lobby closed for good. */
    fun closed(lobby: Lobby) {}

    companion object {
        val NONE: LobbyEvents = object : LobbyEvents {}
    }
}

/**
 * One lobby, as one coroutine that owns all of its state: members, host, settings and the game under
 * way. Everything reaches it as a [LobbyCommand] through its inbox, handled one at a time, so nothing
 * of it is ever shared or locked. It never reads the database: questions load in a child coroutine
 * that posts back, and results go to a [ResultSink] that nothing waits for.
 *
 * Every state change moves [version] on by one and reaches every connected member in order, fitted to
 * who reads it: a member who locked in an answer sees everyone's picks, one who has not sees only who
 * answered. A reconnecting member gets a full snapshot. Nothing on the wire says which answer is right,
 * nor what anyone scored, until the reveal.
 */
class Lobby(
    val id: String,
    val code: String,
    kind: LobbyKind,
    settings: LobbySettingsDto,
    /** The order lobbies opened in, for the public list and Quick play: less is older. */
    val openedOrder: Long,
    private val env: LobbyEnvironment,
) {
    private val timings = env.timings
    private val inbox = Channel<LobbyCommand>(INBOX_CAPACITY)

    var kind: LobbyKind = kind
        private set
    private var settings: LobbySettingsDto = settings

    private val members = LinkedHashMap<String, Member>()

    /** Who may not come back while the lobby lasts, and why: a join is told a vote from a host's kick. */
    private val banned = mutableMapOf<String, CloseReason>()
    private var host: String? = null
    private var version = 0L
    private var phase: Phase = Phase.Waiting(lastResults = null)
    private var epoch = 0L
    private var loads = 0L
    private var nextOrder = 0L
    private var timer: Job? = null
    private var draining = false
    private var drainDeadline: ComparableTimeMark? = null
    private var closeWaitingAt: ComparableTimeMark? = null
    private var closing = false
    private var closed = false
    private val openedAt = now()
    private var waitingSince = openedAt

    /** The [waitingSince] the room was last told it would close for, so it hears of it once. */
    private var idleWarnedFor: ComparableTimeMark? = null
    private var lastPresence: ServerMessage.Presence? = null

    /** Members whose socket refused a message during the current command: dropped once it is done. */
    private val slow = LinkedHashSet<Member>()

    private val publishedSummary = MutableStateFlow(summary())

    /** What the lobby shows outside itself, after the last command it handled. */
    val summary: StateFlow<LobbySummary> get() = publishedSummary

    /** Queues [command] for the lobby's loop. False when the inbox is full or the lobby has closed. */
    fun send(command: LobbyCommand): Boolean = inbox.trySend(command).isSuccess

    /**
     * Queues [command] as [send] does, but waits for room in a full inbox, up to [within]: for what must not
     * be dropped, a socket's coming and going, which a flood filling the inbox would otherwise lose. A
     * socket's end dropped so left its member connected for good, their grace never begun. Not cancelled
     * once begun, so a socket's end is told however its handler ends.
     */
    suspend fun deliver(
        command: LobbyCommand,
        within: Duration = Duration.INFINITE,
    ): Delivery =
        withContext(NonCancellable) {
            try {
                withTimeoutOrNull(within) { inbox.send(command) }?.let { Delivery.QUEUED } ?: Delivery.NO_ROOM
            } catch (closed: ClosedSendChannelException) {
                Delivery.CLOSED
            }
        }

    /** Holds a seat for [seat], answered by the lobby's loop; [ReserveResult.Closed] if it never answers. */
    suspend fun reserve(
        seat: Seat,
        heldOnly: Boolean = false,
    ): ReserveResult {
        val reply = CompletableDeferred<ReserveResult>()
        if (!send(LobbyCommand.Reserve(seat, reply, heldOnly))) return ReserveResult.Closed
        return withTimeoutOrNull(RESERVE_TIMEOUT) { reply.await() } ?: ReserveResult.Closed
    }

    /** Starts the lobby's loop in the environment's scope, and its tick. */
    fun start(): Job =
        env.scope.launch(CoroutineName("lobby-$code")) {
            val ticker =
                launch {
                    while (isActive) {
                        delay(timings.tick)
                        inbox.trySend(LobbyCommand.Tick)
                    }
                }
            try {
                for (command in inbox) {
                    try {
                        handle(command)
                        settleKickVotes()
                        dropSlowMembers()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failed: Exception) {
                        log.error("lobby $code failed on ${command::class.simpleName}", failed)
                        closeAfterFailure()
                    }
                    publishedSummary.value = summary()
                    if (closed) break
                }
            } finally {
                ticker.cancel()
                timer?.cancel()
                inbox.close()
                // However the loop ended, no socket is left to a lobby that no longer listens.
                if (!closed) abandon()
                // Whatever was still queued: a reservation waiting for its answer is told the lobby closed.
                while (true) {
                    val left = inbox.tryReceive().getOrNull() ?: break
                    if (left is LobbyCommand.Reserve) left.reply.complete(ReserveResult.Closed)
                }
            }
        }

    // --- Commands ---

    private fun handle(command: LobbyCommand) {
        when (command) {
            is LobbyCommand.Reserve -> {
                reserve(command.seat, command.reply, command.heldOnly)
            }

            is LobbyCommand.Attach -> {
                attach(command.connection)
            }

            is LobbyCommand.Detach -> {
                detach(command.connection)
            }

            is LobbyCommand.FromClient -> {
                fromClient(command)
            }

            is LobbyCommand.Remove -> {
                members[command.playerId]
                    ?.takeIf { command.sessionId == null || it.sessionId == command.sessionId }
                    ?.let { remove(it, command.reason) }
            }

            is LobbyCommand.Presence -> {
                presence(ServerMessage.Presence(command.online, command.searching))
            }

            is LobbyCommand.Drain -> {
                drain(command.deadline)
            }

            LobbyCommand.Tick -> {
                tick()
            }

            is LobbyCommand.PhaseTimeout -> {
                if (command.epoch == epoch) phaseTimedOut()
            }

            is LobbyCommand.QuestionsLoaded -> {
                questionsLoaded(command.epoch, command.result)
            }
        }
    }

    private fun reserve(
        seat: Seat,
        reply: CompletableDeferred<ReserveResult>,
        heldOnly: Boolean,
    ) {
        val existing = members[seat.playerId]
        val result =
            when {
                closing -> {
                    ReserveResult.Closed
                }

                draining -> {
                    ReserveResult.Draining
                }

                seat.playerId in banned -> {
                    ReserveResult.Banned(banned.getValue(seat.playerId))
                }

                existing != null -> {
                    existing.sessionId = seat.sessionId
                    updateIdentity(existing, seat.name, seat.avatar, seat.xp)
                    if (existing.connection ==
                        null
                    ) {
                        existing.holdUntil = latest(existing.holdUntil, now() + ticketHold())
                    }
                    ReserveResult.Reserved
                }

                // A rejoin for a seat let go meanwhile: gone, as the lobby is to them.
                heldOnly -> {
                    ReserveResult.Closed
                }

                members.size >= settings.maxPlayers -> {
                    ReserveResult.Full
                }

                else -> {
                    // A player of the game in progress who left and sits down again plays on: their answers
                    // count again, and the questions wait for them as for anyone.
                    (phase as? Phase.Playing)?.game?.left?.remove(seat.playerId)
                    val member =
                        Member(
                            playerId = seat.playerId,
                            sessionId = seat.sessionId,
                            name = seat.name,
                            avatar = seat.avatar,
                            xp = seat.xp,
                            order = nextOrder++,
                            seat = freeSeat(),
                            activeAt = now(),
                            reactions = ReactionBudget(timings.reactionEvery, timings.reactionBurst),
                        )
                    member.holdUntil = now() + ticketHold()
                    members[member.playerId] = member
                    if (host == null) host = member.playerId
                    ReserveResult.Reserved
                }
            }
        reply.complete(result)
    }

    private fun attach(connection: LobbyConnection) {
        val member = members[connection.playerId]
        if (member == null || closing) {
            // The seat was never held, or went while the socket came: the client asks REST again.
            connection.send(ServerMessage.Closing(CloseReason.LOBBY_CLOSED))
            connection.close(CloseCodes.LOBBY_GONE)
            return
        }
        val previous = member.connection
        if (previous != null && previous.id != connection.id) {
            previous.send(ServerMessage.Closing(CloseReason.REPLACED))
            previous.close(CloseCodes.REPLACED)
        }
        member.connection = connection
        member.sessionId = connection.sessionId
        member.holdUntil = null
        member.goneSince = null
        member.activeAt = now()

        connection.send(ServerMessage.Welcome(Protocol.VERSION, member.playerId, timings.pingEvery.inWholeMilliseconds))
        if (!member.announced) {
            member.announced = true
            changed(except = member) { v, reader -> ServerMessage.MemberJoined(v, viewOf(member, reader)) }
        } else if (previous == null) {
            changed(except = member) { v, reader -> ServerMessage.MemberUpdated(v, viewOf(member, reader)) }
        }
        send(member, snapshotFor(member))
        lastPresence?.let { send(member, it) }
    }

    private fun detach(connection: LobbyConnection) {
        val member = members[connection.playerId] ?: return
        if (member.connection?.id != connection.id) return
        disconnect(member)
    }

    /** [member]'s socket is gone: they keep their seat through their grace, and in a game to its end. */
    private fun disconnect(member: Member) {
        member.connection = null
        member.goneSince = now()
        member.holdUntil = now() + timings.lobbyGrace
        if (member.announced) changed { v, reader -> ServerMessage.MemberUpdated(v, viewOf(member, reader)) }
        revealIfEveryoneAnswered()
    }

    private fun fromClient(command: LobbyCommand.FromClient) {
        val member = members[command.connection.playerId] ?: return
        if (member.connection?.id != command.connection.id) return
        val message = command.message
        // What a client sends by itself, a pong, a resync, is no sign of anyone at the screen.
        if (message !is ClientMessage.Pong && message != ClientMessage.Resync && message != ClientMessage.Unknown) {
            member.activeAt = now()
        }
        when (message) {
            is ClientMessage.Answer -> answer(member, message, command.receivedAt, command.rtt)

            is ClientMessage.React -> react(member, message.reaction)

            is ClientMessage.UpdateSettings -> updateSettings(member, message.id, message.settings)

            is ClientMessage.Start -> startGame(member, message.id)

            is ClientMessage.Kick -> kick(member, message.id, message.player)

            is ClientMessage.VoteKick -> voteKick(member, message.id, message.player)

            is ClientMessage.TransferHost -> transferHost(member, message.id, message.player)

            is ClientMessage.BackToLobby -> backToLobby(member, message.id)

            // Still here, waiting: the member's activity above restarts a host's time, this the room's.
            ClientMessage.Stay -> if (phase is Phase.Waiting) waitingSince = now()

            ClientMessage.Leave -> remove(member, LeaveReason.LEFT)

            ClientMessage.Resync -> send(member, snapshotFor(member))

            is ClientMessage.Hello -> command.connection.close(CloseCodes.PROTOCOL_ERROR)

            is ClientMessage.Pong, ClientMessage.Unknown -> Unit
        }
    }

    private fun updateSettings(
        member: Member,
        id: Int,
        asked: LobbySettingsDto,
    ) {
        val wanted = asked.tidied()
        if (member.playerId != host) return reject(member, id, RejectCode.NOT_HOST)
        if (phase !is Phase.Waiting) return reject(member, id, RejectCode.WRONG_PHASE)
        if (settingsProblem(wanted, kind, env.knownTopics(), members.size) != null) {
            return reject(member, id, RejectCode.INVALID_SETTINGS)
        }
        settings = wanted
        if (kind != LobbyKind.SOLO) kind = kindOf(wanted)
        changed { v, _ -> ServerMessage.SettingsChanged(v, wanted) }
        ack(member, id)
    }

    private fun startGame(
        member: Member,
        id: Int,
    ) {
        when {
            member.playerId != host -> {
                reject(member, id, RejectCode.NOT_HOST)
            }

            phase !is Phase.Waiting || member.onResults -> {
                reject(member, id, RejectCode.WRONG_PHASE)
            }

            draining -> {
                reject(member, id, RejectCode.DRAINING)
            }

            else -> {
                ack(member, id)
                beginCountdown()
            }
        }
    }

    private fun kick(
        member: Member,
        id: Int,
        target: String,
    ) {
        if (member.playerId != host) return reject(member, id, RejectCode.NOT_HOST)
        val kicked =
            members[target]?.takeIf { it.playerId != member.playerId }
                ?: return reject(member, id, RejectCode.NO_SUCH_PLAYER)
        ack(member, id)
        remove(kicked, LeaveReason.KICKED)
    }

    /**
     * [voter]'s vote to put [target] out of the room, or, for none, their vote taken back. Cast only while
     * the room waits, by a member in it rather than on the last game's results; one vote at a time, and a
     * new one at most once per [GameTimings.kickVoteEvery], so nobody spams them, though taking one back is
     * never held up. [settleKickVotes], after the command, puts out whoever the votes reach.
     */
    private fun voteKick(
        voter: Member,
        id: Int,
        target: String?,
    ) {
        if (kind == LobbyKind.SOLO || phase !is Phase.Waiting || voter.onResults) {
            return reject(voter, id, RejectCode.WRONG_PHASE)
        }
        if (target == null) {
            voter.kickVote = null
            return ack(voter, id)
        }
        val against =
            members[target]?.takeIf { it.announced && it.playerId != voter.playerId }
                ?: return reject(voter, id, RejectCode.NO_SUCH_PLAYER)
        if (voter.kickVote == against.playerId) return ack(voter, id)
        val at = now()
        if (voter.nextVoteAt?.let { at < it } == true) return reject(voter, id, RejectCode.TOO_SOON)
        voter.kickVote = against.playerId
        voter.nextVoteAt = at + timings.kickVoteEvery
        ack(voter, id)
    }

    private fun transferHost(
        member: Member,
        id: Int,
        target: String,
    ) {
        if (member.playerId != host) return reject(member, id, RejectCode.NOT_HOST)
        val next =
            members[target]?.takeIf { it.announced && it.playerId != member.playerId }
                ?: return reject(member, id, RejectCode.NO_SUCH_PLAYER)
        host = next.playerId
        changed { v, _ -> ServerMessage.HostChanged(v, next.playerId) }
        ack(member, id)
    }

    private fun backToLobby(
        member: Member,
        id: Int,
    ) {
        if (member.onResults) {
            member.onResults = false
            changed { v, reader -> ServerMessage.MemberUpdated(v, viewOf(member, reader)) }
        }
        ack(member, id)
    }

    private fun react(
        member: Member,
        reaction: String,
    ) {
        if (reaction !in Reactions.ALL || !member.reactions.take(now())) return
        val reacted = ServerMessage.Reacted(member.playerId, reaction)
        members.values.filter { it.announced }.forEach { send(it, reacted) }
    }

    private fun presence(message: ServerMessage.Presence) {
        if (message == lastPresence) return
        lastPresence = message
        if (phase is Phase.Playing) return
        members.values.filter { it.announced }.forEach { send(it, message) }
    }

    private fun drain(deadline: ComparableTimeMark) {
        if (draining) return
        draining = true
        drainDeadline = deadline
        val notice = ServerMessage.Notice(NoticeKind.SERVER_RESTARTING, remainingMs(deadline))
        members.values.filter { it.announced }.forEach { send(it, notice) }
        when (val current = phase) {
            is Phase.Waiting -> {
                closeWaitingAt = now() + timings.drainWaitingLobby
            }

            is Phase.Countdown -> {
                abort(AbortReason.SERVER_RESTARTING, current.lastResults)
                closeWaitingAt = now() + timings.drainWaitingLobby
            }

            is Phase.Playing -> {
                if (!fitsBefore(current.game, deadline)) current.game.endAfterThis = true
            }
        }
    }

    private fun tick() {
        val at = now()
        members.values
            .filter { it.connection == null && it.holdUntil?.let { hold -> hold <= at } == true && !isPlaying(it) }
            .toList()
            .forEach { remove(it, LeaveReason.TIMED_OUT) }
        if (closed) return
        handOverAbsentHost(at)
        warnIdle(at)
        // A drop's grace may have run out with everyone else answered.
        revealIfEveryoneAnswered()
        when {
            draining && phase !is Phase.Playing && closeWaitingAt?.let { at >= it } == true -> {
                close(CloseReason.SERVER_RESTARTING, CloseCodes.SERVER_RESTARTING)
            }

            phase is Phase.Waiting && at - waitingSince >= timings.idle -> {
                close(CloseReason.IDLE, CloseCodes.NORMAL)
            }

            phase !is Phase.Playing && at - openedAt >= timings.maxLifetime -> {
                close(CloseReason.LOBBY_CLOSED, CloseCodes.NORMAL)
            }
        }
    }

    /**
     * A public lobby's host who is gone, or does nothing, while others wait, hands hosting to the member
     * there longest, so strangers are never stuck behind them. A private lobby's host keeps it through
     * their grace: friends wait for each other.
     */
    private fun handOverAbsentHost(at: ComparableTimeMark) {
        if (kind != LobbyKind.PUBLIC || phase !is Phase.Waiting) return
        val current = host?.let { members[it] } ?: return
        val others = members.values.filter { it.announced && it.connection != null && it.playerId != current.playerId }
        if (others.isEmpty()) return
        val absent = current.connection == null && at - (current.goneSince ?: at) >= timings.absentPublicHost
        val idle = at - current.activeAt >= timings.afkHost
        // An idle host hands over only to someone who did something since, so two idle members never
        // pass hosting back and forth.
        val next =
            when {
                absent -> others.minBy { it.order }
                idle -> others.filter { it.activeAt > current.activeAt }.maxByOrNull { it.activeAt }
                else -> null
            } ?: return
        next.activeAt = at
        host = next.playerId
        changed { v, _ -> ServerMessage.HostChanged(v, next.playerId) }
    }

    /**
     * Says what is about to happen to a host and a room that do nothing, so no one is surprised by it: to a
     * public lobby's host, that hosting passes to another, [GameTimings.afkHostWarning] before it does, and to
     * the whole waiting lobby, that it closes, [GameTimings.idleWarning] before. Each is said once for a stretch
     * of nothing: the host doing something, or any member sending [ClientMessage.Stay], starts it over.
     */
    private fun warnIdle(at: ComparableTimeMark) {
        if (phase !is Phase.Waiting) return
        val current = host?.let { members[it] }
        if (kind == LobbyKind.PUBLIC && current != null && current.connection != null) {
            val others = members.values.any { it.announced && it.connection != null && it !== current }
            val left = timings.afkHost - (at - current.activeAt)
            if (others && left <= timings.afkHostWarning && current.warnedIdleFor != current.activeAt) {
                current.warnedIdleFor = current.activeAt
                send(
                    current,
                    ServerMessage.Notice(NoticeKind.HOST_IDLE, left.coerceAtLeast(Duration.ZERO).inWholeMilliseconds),
                )
            }
        }
        val closesIn = timings.idle - (at - waitingSince)
        if (closesIn <= timings.idleWarning && idleWarnedFor != waitingSince) {
            idleWarnedFor = waitingSince
            val notice =
                ServerMessage.Notice(
                    NoticeKind.ROOM_IDLE,
                    closesIn.coerceAtLeast(Duration.ZERO).inWholeMilliseconds,
                )
            members.values.filter { it.announced }.forEach { send(it, notice) }
        }
    }

    // --- The game ---

    private fun beginCountdown() {
        // A game wipes the slate: the votes to put someone out are for a room that waits.
        members.values.forEach { it.kickVote = null }
        val stragglers = members.values.any { it.announced && it.connection != null && it.onResults }
        val length = if (stragglers) timings.countdownWithStragglers else timings.countdown
        val loadEpoch = ++loads
        val lastResults = (phase as? Phase.Waiting)?.lastResults
        phase = Phase.Countdown(endsAt = now() + length, loadEpoch = loadEpoch, lastResults = lastResults)
        val request =
            PickRequest(
                count = settings.questionCount,
                topics = settings.topics,
                players =
                    members.values
                        .filter { it.announced }
                        .map { it.playerId }
                        .toSet(),
                soloPlayer = if (kind == LobbyKind.SOLO) host else null,
                difficulty = settings.difficulty,
            )
        env.scope.launch(CoroutineName("lobby-$code-questions")) {
            val result =
                try {
                    Result.success(env.questions.pick(request))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failed: Exception) {
                    Result.failure(failed)
                }
            try {
                inbox.send(LobbyCommand.QuestionsLoaded(loadEpoch, result))
            } catch (_: ClosedSendChannelException) {
                // The lobby closed meanwhile.
            }
        }
        schedule(length)
        changed { v, _ -> ServerMessage.CountdownStarted(v, length.inWholeMilliseconds) }
    }

    private fun questionsLoaded(
        loadEpoch: Long,
        result: Result<PickResult>,
    ) {
        val countdown = phase as? Phase.Countdown ?: return
        if (loadEpoch != countdown.loadEpoch) return
        val picked = result.getOrNull()
        if (picked == null || picked.questions.isEmpty()) {
            result.exceptionOrNull()?.let { log.warn("lobby $code could not load questions: ${it::class.simpleName}") }
            return abort(AbortReason.NO_QUESTIONS, countdown.lastResults)
        }
        countdown.loaded = picked
        if (countdown.over) beginGame(picked)
    }

    private fun phaseTimedOut() {
        when (val current = phase) {
            is Phase.Waiting -> {}

            is Phase.Countdown -> {
                val loaded = current.loaded
                when {
                    loaded != null -> {
                        beginGame(loaded)
                    }

                    !current.over -> {
                        current.over = true
                        schedule(timings.questionLoadTimeout)
                    }

                    else -> {
                        abort(AbortReason.NO_QUESTIONS, current.lastResults)
                    }
                }
            }

            is Phase.Playing -> {
                val game = current.game
                when (game.step) {
                    Step.READING -> openAnswers(game)
                    Step.ANSWERING -> reveal(game)
                    Step.REVEALING -> if (game.isLastQuestion()) finishGame(game) else nextQuestion(game)
                }
            }
        }
    }

    private fun abort(
        reason: AbortReason,
        lastResults: ResultsView?,
    ) {
        timer?.cancel()
        phase = Phase.Waiting(lastResults)
        waitingSince = now()
        changed { v, _ -> ServerMessage.GameAborted(v, reason) }
    }

    private fun beginGame(picked: PickResult) {
        // Whoever still looks at the last game's results as the next one starts never came back: they leave
        // the room, so nobody away from it holds a seat, and may join again like anyone.
        members.values
            .filter { it.announced && it.onResults }
            .toList()
            .forEach { remove(it, LeaveReason.NOT_BACK) }
        if (closing) return
        val at = now()
        val players =
            members.values
                .filter { it.announced && !it.onResults }
                .filter { it.connection != null || it.holdUntil?.let { hold -> hold > at } == true }
                .map { it.playerId }
        if (players.isEmpty()) return abort(AbortReason.NO_PLAYERS, (phase as? Phase.Countdown)?.lastResults)

        val asked = picked.questions.take(settings.questionCount).map(::shuffled)
        val game =
            Game(
                id = env.newId(),
                settings = settings,
                questions = asked,
                players = players,
                startedAt = env.wallClock(),
                soloBestBefore = picked.soloBestBefore,
            )
        players.forEach { player ->
            game.tallies[player] = Tally(player)
            members[player]?.let { game.shown[player] = Shown(it.name, it.avatar, it.xp) }
        }
        phase = Phase.Playing(game)
        log.info("lobby $code started game ${game.id}: ${players.size} players, ${asked.size} questions")

        val notices =
            listOfNotNull(
                NoticeKind.TOPICS_TOPPED_UP.takeIf { picked.toppedUp },
                NoticeKind.GAME_SHORTENED.takeIf { picked.shortened || asked.size < settings.questionCount },
            )
        notices.forEach { kind ->
            val notice = ServerMessage.Notice(kind)
            members.values.filter { it.announced }.forEach { send(it, notice) }
        }
        changed { v, _ -> ServerMessage.GameStarted(v, game.id, players, asked.size) }
        showQuestion(game)
    }

    /** [question] with its answers in an order of this game's own, and the right one's place in it. */
    private fun shuffled(question: GameQuestion): AskedQuestion {
        val order = question.options.indices.shuffled(env.random)
        return AskedQuestion(
            question,
            options = order.map { question.options[it] },
            correct = order.indexOf(question.correct),
        )
    }

    private fun showQuestion(game: Game) {
        game.step = Step.READING
        game.answers.clear()
        game.openedAt = null
        val read = timings.readTime(game.current().source.text)
        game.stepEndsAt = now() + read
        schedule(read)
        changed { v, _ -> ServerMessage.QuestionShown(v, questionView(game), read.inWholeMilliseconds) }
    }

    private fun openAnswers(game: Game) {
        val opened = now()
        val duration = game.duration()
        game.step = Step.ANSWERING
        game.openedAt = opened
        game.stepEndsAt = opened + duration
        // The clients' clocks run the question's time; the server takes answers a little past it.
        schedule(duration + timings.answerGrace)
        val question = game.current()
        changed { v, _ ->
            ServerMessage.AnswersOpened(
                v,
                game.index,
                question.options,
                duration.inWholeMilliseconds,
                duration.inWholeMilliseconds,
            )
        }
    }

    private fun answer(
        member: Member,
        message: ClientMessage.Answer,
        receivedAt: ComparableTimeMark,
        rtt: Duration,
    ) {
        val game = (phase as? Phase.Playing)?.game ?: return reject(member, message.id, RejectCode.WRONG_PHASE)
        if (message.question != game.index) return reject(member, message.id, RejectCode.WRONG_QUESTION)
        if (member.playerId !in game.players || member.playerId in game.left) {
            return reject(member, message.id, RejectCode.NOT_PLAYING)
        }
        when (game.step) {
            Step.READING -> return reject(member, message.id, RejectCode.TOO_EARLY)
            Step.REVEALING -> return reject(member, message.id, RejectCode.TOO_LATE)
            Step.ANSWERING -> Unit
        }
        val question = game.current()
        if (message.option !in question.options.indices) return reject(member, message.id, RejectCode.INVALID_OPTION)
        game.answers[member.playerId]?.let { locked ->
            return if (locked.option == message.option) {
                ack(member, message.id)
            } else {
                reject(member, message.id, RejectCode.ALREADY_ANSWERED)
            }
        }
        val opened = checkNotNull(game.openedAt) { "answers are open" }
        val duration = game.duration()
        if (receivedAt - opened > duration + timings.answerGrace) return reject(member, message.id, RejectCode.TOO_LATE)

        val taken = answerTime(receivedAt - opened, rtt, timings.rttCap, duration)
        game.answers[member.playerId] = Answer(message.option, taken, order = game.answers.size)
        ack(member, message.id)

        val picks = game.answers.map { (player, answer) -> PickView(player, answer.option) }
        val answered = game.answers.keys.toList()
        changed { v, reader ->
            if (reader.playerId in game.answers && reader.playerId in game.players) {
                ServerMessage.Picks(v, game.index, picks)
            } else {
                ServerMessage.Progress(v, game.index, answered)
            }
        }
        revealIfEveryoneAnswered()
    }

    /**
     * Reveals at once when every player the question still waits for has locked in: every connected
     * player of the game, and one whose socket closed within [GameTimings.dropGrace], who may be back
     * with their answer in a moment. Nobody else waits on the clock. [tick] asks again as graces run out.
     */
    private fun revealIfEveryoneAnswered() {
        val game = (phase as? Phase.Playing)?.game ?: return
        if (game.step != Step.ANSWERING) return
        val waitedFor = waitedFor(game)
        if (waitedFor.isNotEmpty() && waitedFor.all { it in game.answers }) reveal(game)
    }

    /** The players of [game] its question waits for now, as [revealIfEveryoneAnswered] has them. */
    private fun waitedFor(game: Game): List<String> {
        val at = now()
        return game.players.filter { player ->
            val member = members[player]
            player !in game.left &&
                member != null &&
                (member.connection != null || member.goneSince?.let { at - it < timings.dropGrace } == true)
        }
    }

    private fun reveal(game: Game) {
        val question = game.current()
        val duration = game.duration()
        val rightInOrder =
            game.answers.entries
                .filter { it.value.option == question.correct }
                .sortedWith(compareBy({ it.value.time }, { it.value.order }))
                .mapIndexed { place, entry -> entry.key to place }
                .toMap()

        val results =
            game.players.map { player ->
                val given = game.answers[player]
                val place = rightInOrder[player]
                val points =
                    when {
                        given == null -> {
                            0
                        }

                        place != null -> {
                            env.scoring.correct(fractionLeft(given.time, duration), place)
                        }

                        else -> {
                            env.scoring.wrong(
                                fractionLeft(given.time, duration),
                                question.options.size,
                                game.settings.wrongAnswerPenalty,
                            )
                        }
                    }
                val tally = game.tallies.getValue(player)
                game.tallies[player] =
                    tally.copy(
                        score = tally.score + points,
                        correct = tally.correct + if (place != null) 1 else 0,
                        answered = tally.answered + if (given != null) 1 else 0,
                        correctTimeMs =
                            tally.correctTimeMs + if (place != null) given?.time?.inWholeMilliseconds ?: 0 else 0,
                    )
                AnswerResultView(
                    player = player,
                    option = given?.option,
                    points = points,
                    timeMs = given?.time?.inWholeMilliseconds?.let { it / SHOWN_TIME_STEP_MS * SHOWN_TIME_STEP_MS },
                    order = place?.plus(1)?.takeIf { it <= env.scoring.orderBonus.size },
                )
            }
        game.records +=
            QuestionRecord(
                questionId = question.source.questionId,
                topicId = question.source.topicId,
                answers =
                    game.answers.mapValues { (_, given) ->
                        AnswerRecord(
                            correct = given.option == question.correct,
                            timeMs = given.time.inWholeMilliseconds,
                        )
                    },
                silent = waitedFor(game).count { it !in game.answers },
            )

        val showFor = if (question.source.explanation != null) timings.revealWithExplanation else timings.reveal
        val reveal =
            RevealView(
                index = game.index,
                count = game.questions.size,
                questionId = question.source.questionId,
                text = question.source.text,
                options = question.options,
                correct = question.correct,
                results = results,
                standings = standingsOf(game),
                explanation = question.source.explanation,
                nextInMs = showFor.inWholeMilliseconds,
                last = game.isLastQuestion(),
            )
        game.step = Step.REVEALING
        game.lastReveal = reveal
        game.stepEndsAt = now() + showFor
        schedule(showFor)
        changed { v, _ -> ServerMessage.Revealed(v, reveal) }
    }

    private fun nextQuestion(game: Game) {
        game.index++
        showQuestion(game)
    }

    private fun finishGame(game: Game) {
        timer?.cancel()
        val endedEarly = game.index < game.questions.lastIndex || game.step != Step.REVEALING
        // Those who stayed to the end stand ahead of those who left, whatever the score: a tie never
        // favours someone gone, and nobody gone is crowned.
        val (stayed, gone) = game.players.partition { it !in game.left }
        val ahead = rank(stayed.map { game.tallies.getValue(it) })
        val ranked = ahead + rank(gone.map { game.tallies.getValue(it) }).map { it.copy(rank = it.rank + ahead.size) }
        val finishers = stayed.size

        fun won(
            tally: Tally,
            rank: Int,
        ) = rank == 1 && tally.playerId !in game.left && finishers >= 2
        // Only a game in a room earns experience, and only for those who stayed to its end.
        val earned =
            if (kind == LobbyKind.SOLO) {
                emptyMap()
            } else {
                ranked
                    .filter { (tally, _) -> tally.playerId !in game.left }
                    .associate { (tally, rank) -> tally.playerId to Levels.award(tally.correct, won(tally, rank)) }
            }
        val standings =
            ranked.map { (tally, rank) ->
                val shown = game.shown[tally.playerId]
                FinalStandingView(
                    player = tally.playerId,
                    name = shown?.name ?: tally.playerId,
                    avatar = shown?.avatar.orEmpty(),
                    score = tally.score,
                    correct = tally.correct,
                    rank = rank,
                    finished = tally.playerId !in game.left,
                    level = shown?.let { Levels.of(it.xp + (earned[tally.playerId] ?: 0)).number } ?: 0,
                )
            }
        val personalBest =
            if (kind == LobbyKind.SOLO && !endedEarly && finishers == 1) {
                val score = ranked.single().tally.score
                val previous = game.soloBestBefore
                PersonalBestView(score = score, previous = previous, isNew = previous == null || score > previous)
            } else {
                null
            }
        val results = ResultsView(game.id, game.questions.size, standings, endedEarly, personalBest)

        val record =
            GameRecord(
                gameId = game.id,
                kind = kind,
                settings = game.settings,
                startedAt = game.startedAt,
                endedAt = env.wallClock(),
                endedEarly = endedEarly,
                players =
                    ranked.map { (tally, rank) ->
                        val finished = tally.playerId !in game.left
                        PlayerRecord(
                            playerId = tally.playerId,
                            score = tally.score,
                            correct = tally.correct,
                            answered = tally.answered,
                            rank = rank,
                            finished = finished,
                            won = won(tally, rank),
                        )
                    },
                questions = game.records.toList(),
                spectators = members.values.filter { it.announced && it.playerId !in game.players }.map { it.playerId },
            )
        // The players' results come first: a record that cannot be kept is logged, and the game ends all the same.
        try {
            env.results.submit(record)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            log.error("lobby $code could not record game ${game.id}", failed)
        }
        log.info("lobby $code ended game ${game.id}${if (endedEarly) " early" else ""}")

        phase = Phase.Waiting(results)
        waitingSince = now()
        game.players.forEach { player -> members[player]?.onResults = true }
        changed { v, _ -> ServerMessage.GameOver(v, results) }
        // The record adds the same to their profiles; here it is what their seats show, until they sit down again.
        earned.forEach { (player, gain) ->
            val member = members[player] ?: return@forEach
            val before = Levels.of(member.xp).number
            member.xp += gain
            if (member.announced && Levels.of(member.xp).number != before) {
                changed { v, reader -> ServerMessage.MemberUpdated(v, viewOf(member, reader)) }
            }
        }

        if (draining && !closing) close(CloseReason.SERVER_RESTARTING, CloseCodes.SERVER_RESTARTING)
    }

    /** Whether the rest of [game] can be played before [deadline], every step at its longest. */
    private fun fitsBefore(
        game: Game,
        deadline: ComparableTimeMark,
    ): Boolean {
        val answering = game.duration() + timings.answerGrace
        val stepLeft = (game.stepEndsAt - now()).coerceAtLeast(Duration.ZERO)
        val current =
            when (game.step) {
                Step.READING -> stepLeft + answering + timings.revealWithExplanation
                Step.ANSWERING -> stepLeft + timings.answerGrace + timings.revealWithExplanation
                Step.REVEALING -> stepLeft
            }
        val perQuestion = timings.readMax + answering + timings.revealWithExplanation
        return now() + current + perQuestion * (game.questions.lastIndex - game.index) <= deadline
    }

    // --- Members ---

    private fun remove(
        member: Member,
        reason: LeaveReason,
    ) {
        if (members.remove(member.playerId) == null) return
        if (reason == LeaveReason.KICKED || reason == LeaveReason.VOTED_OUT) {
            banned[member.playerId] = closeReasonFor(reason)
        }
        // Votes against them have nobody left to count for; a vote that put them out was no spam, so its
        // voters may vote again at once.
        members.values.filter { it.kickVote == member.playerId }.forEach { voter ->
            voter.kickVote = null
            if (reason == LeaveReason.VOTED_OUT) voter.nextVoteAt = null
        }
        member.connection?.let { connection ->
            connection.send(ServerMessage.Closing(closeReasonFor(reason)))
            connection.close(closeCodeFor(reason))
            member.connection = null
        }
        if (member.announced) changed { v, _ -> ServerMessage.MemberLeft(v, member.playerId, reason) }
        env.events.memberGone(this, member.playerId)

        val game = (phase as? Phase.Playing)?.game
        if (game != null && member.playerId in game.players) {
            game.left += member.playerId
            if (game.players.all { it in game.left }) {
                finishGame(game)
            } else {
                revealIfEveryoneAnswered()
            }
        }
        if (host == member.playerId) chooseHost()
        if (members.isEmpty()) close(CloseReason.LOBBY_CLOSED, CloseCodes.NORMAL)
    }

    /** The member there longest takes over, preferring one who is connected. */
    private fun chooseHost() {
        val next =
            members.values
                .filter { it.announced }
                .sortedWith(compareBy({ it.connection == null }, { it.order }))
                .firstOrNull()
                ?: members.values.minByOrNull { it.order }
        host = next?.playerId
        if (next != null && next.announced) changed { v, _ -> ServerMessage.HostChanged(v, next.playerId) }
    }

    private fun updateIdentity(
        member: Member,
        name: String,
        avatar: String,
        xp: Int,
    ) {
        val levelChanged = Levels.of(member.xp).number != Levels.of(xp).number
        member.xp = xp
        if (member.name == name && member.avatar == avatar && !levelChanged) return
        member.name = name
        member.avatar = avatar
        if (member.announced) changed { v, reader -> ServerMessage.MemberUpdated(v, viewOf(member, reader)) }
    }

    private fun close(
        reason: CloseReason,
        closeCode: Short,
    ) {
        if (closing) return
        closing = true
        (phase as? Phase.Playing)?.game?.let(::finishGame)
        timer?.cancel()
        members.values.forEach { member ->
            member.connection?.let { connection ->
                connection.send(ServerMessage.Closing(reason))
                connection.close(closeCode)
            }
            member.connection = null
        }
        closed = true
        log.info("lobby $code closed: $reason")
        env.events.closed(this)
    }

    /** After a failure: a clean close if the lobby can still make one, else [abandon]. */
    private fun closeAfterFailure() {
        try {
            closing = false
            close(CloseReason.LOBBY_CLOSED, CloseCodes.INTERNAL)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failedAgain: Exception) {
            log.error("lobby $code failed closing", failedAgain)
            abandon()
        }
    }

    /**
     * Closes every socket as a failure, with nothing more sent, and leaves the registry: what is left
     * when even [close] failed, or the loop ended some other way. The members come back to no lobby and
     * go home; a game under way is not recorded.
     */
    private fun abandon() {
        if (closed) return
        closed = true
        members.values.forEach { member ->
            runCatching { member.connection?.close(CloseCodes.INTERNAL) }
            member.connection = null
        }
        log.warn("lobby $code abandoned")
        runCatching { env.events.closed(this) }
    }

    // --- Votes to put a member out ---

    /** Who may vote [target] out now: every member in the room, connected and back from the results, but them. */
    private fun votersAgainst(target: Member): List<Member> =
        members.values.filter { it.announced && it.connection != null && !it.onResults && it !== target }

    /** The votes against [target] that count now: none but while the room waits. */
    private fun tallyOf(target: Member): KickTally {
        if (phase !is Phase.Waiting) return KickTally.NONE
        val voters = votersAgainst(target)
        val votes = voters.count { it.kickVote == target.playerId }
        return if (votes == 0) KickTally.NONE else KickTally(votes, kickVotesNeeded(voters.size))
    }

    /**
     * Puts out whoever the room has voted out, then tells everyone each tally that moved. Run after every
     * command, since more than a vote can tip one: a member who could vote leaving, dropping or coming back
     * from the results changes how many it takes, and whose votes count.
     */
    private fun settleKickVotes() {
        if (closing) return
        while (true) {
            val out = members.values.firstOrNull { it.announced && tallyOf(it).reached } ?: break
            remove(out, LeaveReason.VOTED_OUT)
            if (closing) return
        }
        members.values.filter { it.announced }.forEach { member ->
            val tally = tallyOf(member)
            if (tally != member.toldTally) {
                member.toldTally = tally
                changed { v, reader -> ServerMessage.MemberUpdated(v, viewOf(member, reader)) }
            }
        }
    }

    // --- Sending ---

    /**
     * One state change: moves the version on and sends every connected member but [except] what [build]
     * makes of it for them. A member whose socket cannot keep up is sent nothing more and disconnected
     * once the command is done ([dropSlowMembers]), so no member ever gets a later version with an
     * earlier one missing, and a member joining gets their snapshot before anyone's drop.
     */
    private fun changed(
        except: Member? = null,
        build: (v: Long, reader: Member) -> ServerMessage,
    ) {
        val v = ++version
        members.values.filter { it.announced && it !== except }.forEach { reader -> send(reader, build(v, reader)) }
    }

    private fun send(
        member: Member,
        message: ServerMessage,
    ) {
        if (member in slow) return
        val connection = member.connection ?: return
        if (!connection.send(message)) slow += member
    }

    /** Disconnects every member whose socket fell behind, each drop a state change of its own. */
    private fun dropSlowMembers() {
        while (slow.isNotEmpty()) {
            val member = slow.first()
            slow.remove(member)
            val connection = member.connection ?: continue
            connection.close(CloseCodes.TOO_MUCH)
            if (members[member.playerId] === member) disconnect(member) else member.connection = null
        }
    }

    private fun ack(
        member: Member,
        id: Int,
    ) = send(member, ServerMessage.Ack(id))

    private fun reject(
        member: Member,
        id: Int,
        code: RejectCode,
    ) = send(member, ServerMessage.Rejected(id, code))

    // --- Views ---

    private fun snapshotFor(member: Member): ServerMessage.Snapshot =
        ServerMessage.Snapshot(version, member.playerId, lobbyView(member), phaseViewFor(member))

    private fun lobbyView(reader: Member): LobbyView =
        LobbyView(
            id = id,
            code = code,
            kind = kind,
            settings = settings,
            host = host.orEmpty(),
            members = members.values.filter { it.announced }.map { viewOf(it, reader) },
        )

    /** [member] as [reader] sees them: whether the reader is one who votes them out is theirs alone to know. */
    private fun viewOf(
        member: Member,
        reader: Member,
    ): MemberView {
        val game = (phase as? Phase.Playing)?.game
        return MemberView(
            player = member.playerId,
            name = member.name,
            avatar = member.avatar,
            seat = member.seat,
            level = Levels.of(member.xp).number,
            connected = member.connection != null,
            onResults = member.onResults,
            playing = game != null && member.playerId in game.players && member.playerId !in game.left,
            kickVotes = member.toldTally.votes,
            kickVotesNeeded = member.toldTally.needed,
            kickVoted = reader.kickVote == member.playerId,
        )
    }

    private fun phaseViewFor(member: Member): PhaseView =
        when (val current = phase) {
            is Phase.Waiting -> {
                PhaseView.Waiting(current.lastResults)
            }

            is Phase.Countdown -> {
                PhaseView.Countdown(remainingMs(current.endsAt), current.lastResults)
            }

            is Phase.Playing -> {
                val game = current.game
                when (game.step) {
                    Step.READING -> {
                        PhaseView.Reading(
                            game.id,
                            game.players,
                            questionView(game),
                            remainingMs(game.stepEndsAt),
                            standingsOf(game),
                        )
                    }

                    Step.ANSWERING -> {
                        val mine = game.answers[member.playerId]
                        PhaseView.Answering(
                            gameId = game.id,
                            players = game.players,
                            question = questionView(game),
                            options = game.current().options,
                            remainingMs = remainingMs(game.stepEndsAt),
                            durationMs = game.duration().inWholeMilliseconds,
                            answered = game.answers.keys.toList(),
                            yourPick = mine?.option,
                            picks =
                                if (mine !=
                                    null
                                ) {
                                    game.answers.map { (player, given) -> PickView(player, given.option) }
                                } else {
                                    emptyList()
                                },
                            standings = standingsOf(game),
                        )
                    }

                    Step.REVEALING -> {
                        PhaseView.Revealing(
                            game.id,
                            game.players,
                            checkNotNull(game.lastReveal),
                            remainingMs(game.stepEndsAt),
                        )
                    }
                }
            }
        }

    private fun questionView(game: Game): QuestionView {
        val question = game.current()
        return QuestionView(
            index = game.index,
            count = game.questions.size,
            text = question.source.text,
            topic = question.source.topicId,
            optionCount = question.options.size,
            kind = question.source.kind,
        )
    }

    private fun standingsOf(game: Game): List<StandingView> =
        rank(game.players.map { game.tallies.getValue(it) }).map { (tally, rank) ->
            StandingView(tally.playerId, tally.score, rank, tally.correct)
        }

    private fun summary(): LobbySummary {
        val hostMember = host?.let { members[it] }
        return LobbySummary(
            id = id,
            code = code,
            kind = kind,
            settings = settings,
            hostName = hostMember?.name.orEmpty(),
            hostAvatar = hostMember?.avatar.orEmpty(),
            seatsTaken = members.size,
            connected =
                members.values
                    .filter { it.connection != null }
                    .map { it.playerId }
                    .toSet(),
            inGame = phase !is Phase.Waiting,
            draining = draining,
            closed = closing,
            openedOrder = openedOrder,
        )
    }

    // --- Helpers ---

    private fun schedule(after: Duration) {
        timer?.cancel()
        val scheduled = ++epoch
        timer =
            env.scope.launch(CoroutineName("lobby-$code-timer")) {
                delay(after)
                try {
                    inbox.send(LobbyCommand.PhaseTimeout(scheduled))
                } catch (_: ClosedSendChannelException) {
                    // The lobby closed meanwhile.
                }
            }
    }

    private fun isPlaying(member: Member): Boolean {
        val game = (phase as? Phase.Playing)?.game ?: return false
        return member.playerId in game.players && member.playerId !in game.left
    }

    private fun freeSeat(): Int {
        val taken = members.values.map { it.seat }.toSet()
        return (0 until SEATS).first { it !in taken }
    }

    private fun ticketHold(): Duration = timings.ticketTtl + RESERVATION_MARGIN

    private fun now(): ComparableTimeMark = env.timeSource.markNow()

    private fun remainingMs(until: ComparableTimeMark): Long = (until - now()).inWholeMilliseconds.coerceAtLeast(0)

    private fun latest(
        a: ComparableTimeMark?,
        b: ComparableTimeMark,
    ): ComparableTimeMark = if (a == null || b > a) b else a

    private fun closeReasonFor(reason: LeaveReason): CloseReason =
        when (reason) {
            LeaveReason.KICKED -> CloseReason.KICKED
            LeaveReason.VOTED_OUT -> CloseReason.VOTED_OUT
            LeaveReason.NOT_BACK -> CloseReason.NOT_BACK
            LeaveReason.SESSION_ENDED -> CloseReason.SESSION_ENDED
            LeaveReason.JOINED_ANOTHER, LeaveReason.LEFT -> CloseReason.LEFT
            LeaveReason.TIMED_OUT, LeaveReason.UNKNOWN -> CloseReason.LOBBY_CLOSED
        }

    private fun closeCodeFor(reason: LeaveReason): Short =
        when (reason) {
            LeaveReason.KICKED, LeaveReason.VOTED_OUT -> CloseCodes.KICKED
            LeaveReason.SESSION_ENDED -> CloseCodes.SESSION_ENDED
            else -> CloseCodes.NORMAL
        }

    // --- State ---

    private class Member(
        val playerId: String,
        /** The session their seat was last held or their socket opened for: a logout of another leaves them. */
        var sessionId: String,
        var name: String,
        var avatar: String,
        /** What they have earned in games: their level is worked out from it, and a game finished adds to it here. */
        var xp: Int,
        val order: Long,
        val seat: Int,
        var activeAt: ComparableTimeMark,
        val reactions: ReactionBudget,
    ) {
        var connection: LobbyConnection? = null

        /** False while only a seat is held for them: nobody else sees them yet. */
        var announced = false
        var onResults = false

        /** The member they vote out of the room, while it waits: one at a time. */
        var kickVote: String? = null

        /** Until when a vote of theirs is refused: one cast per [GameTimings.kickVoteEvery]. */
        var nextVoteAt: ComparableTimeMark? = null

        /** The votes against them as the room was last told, so only a change is sent. */
        var toldTally: KickTally = KickTally.NONE

        /** The [activeAt] a host was last told their hosting would pass on for, so they hear of it once. */
        var warnedIdleFor: ComparableTimeMark? = null

        /** While they have no socket: when their seat goes. */
        var holdUntil: ComparableTimeMark? = null
        var goneSince: ComparableTimeMark? = null
    }

    /** A member's reactions: a burst of [burst], then one per [every]. */
    private class ReactionBudget(
        private val every: Duration,
        private val burst: Int,
    ) {
        private var tokens = burst.toDouble()
        private var refilledAt: ComparableTimeMark? = null

        fun take(at: ComparableTimeMark): Boolean {
            val last = refilledAt
            if (last != null) tokens = (tokens + (at - last) / every).coerceAtMost(burst.toDouble())
            refilledAt = at
            if (tokens < 1.0) return false
            tokens -= 1.0
            return true
        }
    }

    private sealed interface Phase {
        data class Waiting(
            val lastResults: ResultsView?,
        ) : Phase

        class Countdown(
            val endsAt: ComparableTimeMark,
            val loadEpoch: Long,
            val lastResults: ResultsView?,
        ) : Phase {
            var loaded: PickResult? = null

            /** Whether the countdown has run out, and the start waits on its questions. */
            var over = false
        }

        class Playing(
            val game: Game,
        ) : Phase
    }

    private enum class Step { READING, ANSWERING, REVEALING }

    private class AskedQuestion(
        val source: GameQuestion,
        val options: List<String>,
        val correct: Int,
    )

    private class Answer(
        val option: Int,
        val time: Duration,
        /** Which accepted answer this was, 0 first: the tie-break when two took the same time. */
        val order: Int,
    )

    /** A player as the game began: what its results show of them if they have left by then. */
    private class Shown(
        val name: String,
        val avatar: String,
        val xp: Int,
    )

    private class Game(
        val id: String,
        val settings: LobbySettingsDto,
        val questions: List<AskedQuestion>,
        val players: List<String>,
        val startedAt: Long,
        val soloBestBefore: Int?,
    ) {
        val tallies = LinkedHashMap<String, Tally>()

        /** Each player's name, avatar and experience as the game began, for results shown after they left. */
        val shown = HashMap<String, Shown>()
        val left = mutableSetOf<String>()
        var index = 0
        var step = Step.READING
        lateinit var stepEndsAt: ComparableTimeMark
        var openedAt: ComparableTimeMark? = null
        val answers = LinkedHashMap<String, Answer>()
        var lastReveal: RevealView? = null
        val records = mutableListOf<QuestionRecord>()

        /** Set by a drain that the rest of the game would not fit: the next reveal is the last. */
        var endAfterThis = false

        fun current(): AskedQuestion = questions[index]

        fun duration(): Duration = settings.secondsPerQuestion.seconds

        fun isLastQuestion(): Boolean = index >= questions.lastIndex || endAfterThis
    }

    private companion object {
        val log = LoggerFactory.getLogger(Lobby::class.java)

        const val INBOX_CAPACITY = 512
        const val SEATS = 8
        const val SHOWN_TIME_STEP_MS = 100L
        val RESERVE_TIMEOUT = 5.seconds

        /** How much longer a seat is held than its ticket lives, for a socket that opened just in time. */
        val RESERVATION_MARGIN = 5.seconds
    }
}
