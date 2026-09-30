package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.PhaseView
import io.ntole.kvizic.core.protocol.ProtocolJson
import io.ntole.kvizic.core.protocol.RejectCode
import io.ntole.kvizic.core.protocol.ServerMessage
import io.ntole.kvizic.server.rules.ScoringRules
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/** The topics the scenarios' lobbies know. */
internal val TEST_TOPICS = setOf("GEOGRAPHY", "HISTORY", "SPORT")

/** [count] questions of four answers, the first always right, so a test knows each by its source. */
internal fun sampleQuestions(
    count: Int,
    options: Int = 4,
): List<GameQuestion> =
    List(count) { index ->
        GameQuestion(
            questionId = "q$index",
            text = "Питање број $index?",
            options = List(options) { option -> "Одговор $index.$option" },
            correct = 0,
            topicId = TEST_TOPICS.first(),
        )
    }

/**
 * One lobby on the test's virtual clock, with players who connect through recording connections. Every
 * message a connection gets is round-tripped through [ProtocolJson] as it would travel, and every
 * connection's state changes are checked to arrive in order with no version missing ([assertInvariants]).
 */
internal class LobbyScenario(
    private val test: TestScope,
    settings: LobbySettingsDto = LobbySettingsDto(questionCount = 3),
    kind: LobbyKind = LobbyKind.PRIVATE,
    val timings: GameTimings = GameTimings.DEFAULT,
    val scoring: ScoringRules = ScoringRules.DEFAULT,
    var questions: List<GameQuestion> = sampleQuestions(10),
    seed: Int = 7,
) {
    val timeSource = test.testScheduler.timeSource
    val records = mutableListOf<GameRecord>()
    val gone = mutableListOf<String>()
    var closed = false
        private set
    var soloBestBefore: Int? = null
    var failLoading: Exception? = null
    val asked = mutableListOf<PickRequest>()
    private var ids = 0
    private var connections = 0L

    val lobby: Lobby =
        Lobby(
            id = "lobby-1",
            code = "482915",
            kind = kind,
            settings = settings,
            openedOrder = 0,
            env =
                LobbyEnvironment(
                    scope = test.backgroundScope,
                    timeSource = timeSource,
                    timings = timings,
                    scoring = scoring,
                    questions =
                        QuestionSource { request ->
                            asked += request
                            failLoading?.let { throw it }
                            PickResult(questions.take(request.count), soloBestBefore = soloBestBefore)
                        },
                    results = { record -> records += record },
                    knownTopics = { TEST_TOPICS },
                    random = Random(seed),
                    wallClock = { 1_000_000L + test.testScheduler.currentTime },
                    newId = { "id-${ids++}" },
                    events =
                        object : LobbyEvents {
                            override fun memberGone(
                                lobby: Lobby,
                                playerId: String,
                            ) {
                                gone += playerId
                            }

                            override fun closed(lobby: Lobby) {
                                closed = true
                            }
                        },
                ),
        ).also { it.start() }

    private val players = mutableListOf<TestPlayer>()

    fun player(name: String): TestPlayer = TestPlayer(name, this).also { players += it }

    internal fun nextConnectionId(): Long = ++connections

    /** Lets everything due now run: the lobby's loop, its timers, its question loading. */
    fun settle() = test.runCurrent()

    /** Moves the virtual clock on by [duration], running whatever falls due. */
    fun wait(duration: Duration) {
        test.advanceTimeBy(duration)
        test.runCurrent()
    }

    fun summary(): LobbySummary = lobby.summary.value

    /** Every player's messages decoded and in version order. */
    fun assertInvariants() = players.forEach { it.assertVersionsInOrder() }
}

/** A player of a [LobbyScenario]: their seat, their socket, and what they were sent. */
internal class TestPlayer(
    val id: String,
    private val scenario: LobbyScenario,
    val sessionId: String = "session-$id",
    var avatar: String = "fox",
) {
    var connection: TestConnection? = null
        private set
    private val all = mutableListOf<TestConnection>()
    private var commands = 0

    /** Every socket they opened, oldest first. */
    fun sockets(): List<TestConnection> = all

    /** The close code of their latest socket, or null while it is open. */
    fun closedCode(): Short? = all.lastOrNull()?.closedWith

    /** Every message every socket of theirs ever got, in order. */
    val history: List<ServerMessage> get() = all.flatMap { it.messages }

    /** Every message every socket of theirs got since the last [clear], in order. */
    val received: List<ServerMessage> get() = all.flatMap { it.messages.drop(it.clearedUpTo) }

    /** Holds a seat, as REST does before issuing a ticket. */
    fun reserve(): ReserveResult {
        val reply = CompletableDeferred<ReserveResult>()
        check(scenario.lobby.send(LobbyCommand.Reserve(Seat(id, sessionId, "Играч $id", avatar), reply)))
        scenario.settle()
        return reply.getCompleted()
    }

    /** Opens a socket with a redeemed ticket. */
    fun attach(): TestConnection {
        val socket = TestConnection(scenario.nextConnectionId(), id, sessionId)
        connection = socket
        all += socket
        check(scenario.lobby.send(LobbyCommand.Attach(socket)))
        scenario.settle()
        return socket
    }

    /** A seat and a socket, as a join does. */
    fun join(): TestPlayer {
        assertEquals(ReserveResult.Reserved, reserve(), "$id reserves")
        attach()
        return this
    }

    /** The socket closes under them. */
    fun drop() {
        val socket = checkNotNull(connection)
        socket.closedWith = socket.closedWith ?: 1006
        connection = null
        check(scenario.lobby.send(LobbyCommand.Detach(socket)))
        scenario.settle()
    }

    fun say(
        message: ClientMessage,
        rtt: Duration = ZERO,
    ) {
        val socket = checkNotNull(connection) { "$id has no socket" }
        check(scenario.lobby.send(LobbyCommand.FromClient(socket, message, scenario.timeSource.markNow(), rtt)))
        scenario.settle()
    }

    fun nextId(): Int = ++commands

    fun start(): Int = nextId().also { say(ClientMessage.Start(it)) }

    fun back(): Int = nextId().also { say(ClientMessage.BackToLobby(it)) }

    fun kick(player: TestPlayer): Int = nextId().also { say(ClientMessage.Kick(it, player.id)) }

    fun leave() = say(ClientMessage.Leave)

    /** Answers the question asked now with [option], [rtt] the connection's round trip. */
    fun answer(
        option: Int,
        rtt: Duration = ZERO,
        question: Int = currentQuestion(),
    ): Int = nextId().also { say(ClientMessage.Answer(it, question, option), rtt) }

    /** Answers the question asked now right, as the reveal will count it. */
    fun answerRight(rtt: Duration = ZERO): Int = answer(rightOption(), rtt)

    /** Answers the question asked now wrong. */
    fun answerWrong(rtt: Duration = ZERO): Int = answer((rightOption() + 1) % optionsNow().size, rtt)

    /** Where the right answer is among the options as this game shuffled them: the source's first one. */
    fun rightOption(): Int {
        val options = optionsNow()
        val right = scenario.questions.first { q -> q.options.toSet() == options.toSet() }
        return options.indexOf(right.options[right.correct])
    }

    fun optionsNow(): List<String> =
        history.lastOrNull { it is ServerMessage.AnswersOpened }?.let { (it as ServerMessage.AnswersOpened).options }
            ?: (lastSnapshot()?.phase as? PhaseView.Answering)?.options
            ?: fail("$id has seen no answers")

    fun currentQuestion(): Int =
        history.lastOrNull { it is ServerMessage.QuestionShown }?.let {
            (it as ServerMessage.QuestionShown)
                .question.index
        }
            ?: fail("$id has seen no question")

    inline fun <reified T : ServerMessage> last(): T =
        received.filterIsInstance<T>().lastOrNull() ?: fail("$id got no ${T::class.simpleName}")

    inline fun <reified T : ServerMessage> all(): List<T> = received.filterIsInstance<T>()

    fun lastSnapshot(): ServerMessage.Snapshot? = history.filterIsInstance<ServerMessage.Snapshot>().lastOrNull()

    /** The code of the refusal of command [id], or null when it was acknowledged. */
    fun answerTo(id: Int): RejectCode? {
        val reply =
            received.lastOrNull {
                (it is ServerMessage.Ack && it.id == id) ||
                    (it is ServerMessage.Rejected && it.id == id)
            }
        return when (reply) {
            is ServerMessage.Ack -> null
            is ServerMessage.Rejected -> reply.code
            else -> fail("${this.id} got no answer to command $id")
        }
    }

    /** Forgets what was received so far, for [received]; the version check still sees everything. */
    fun clear() = all.forEach { it.clearedUpTo = it.messages.size }

    /** Within each socket: after its snapshot, every state change is the next version, none missed. */
    fun assertVersionsInOrder() {
        all.forEach { socket ->
            var version: Long? = null
            socket.messages.forEach { message ->
                when (message) {
                    is ServerMessage.Snapshot -> {
                        version?.let { assertTrue(message.v >= it, "$id: snapshot at ${message.v} after $it") }
                        version = message.v
                    }

                    is ServerMessage.StateChange -> {
                        val before = version ?: fail("$id got ${message::class.simpleName} before any snapshot")
                        assertEquals(before + 1, message.v, "$id: ${message::class.simpleName} out of order")
                        version = message.v
                    }

                    else -> {
                        Unit
                    }
                }
            }
        }
    }
}

/** A socket that records what it was sent, round-tripped through the protocol's JSON. */
internal class TestConnection(
    override val id: Long,
    override val playerId: String,
    override val sessionId: String,
) : LobbyConnection {
    val messages = mutableListOf<ServerMessage>()
    var clearedUpTo = 0
    var closedWith: Short? = null

    /** False makes every send fail, as a socket too slow to take more does. */
    var accepting = true

    override fun send(message: ServerMessage): Boolean {
        if (closedWith != null) return true
        if (!accepting) return false
        val wire = ProtocolJson.encodeToString(ServerMessage.serializer(), message)
        val decoded = ProtocolJson.decodeFromString(ServerMessage.serializer(), wire)
        assertEquals(message, decoded, "a message that does not survive the wire: $wire")
        messages += decoded
        return true
    }

    override fun close(code: Short) {
        if (closedWith == null) closedWith = code
    }
}
