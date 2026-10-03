package io.ntole.kvizic.server.realtime

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.auth.SessionDto
import io.ntole.kvizic.core.lobby.CreateLobbyRequest
import io.ntole.kvizic.core.lobby.JoinLobbyRequest
import io.ntole.kvizic.core.lobby.LobbyListDto
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.lobby.TicketDto
import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.Protocol
import io.ntole.kvizic.core.protocol.ProtocolJson
import io.ntole.kvizic.core.protocol.ServerMessage
import io.ntole.kvizic.server.GameParts
import io.ntole.kvizic.server.config.ServerConfig
import io.ntole.kvizic.server.lobby.GameQuestion
import io.ntole.kvizic.server.lobby.GameRecord
import io.ntole.kvizic.server.lobby.GameTimings
import io.ntole.kvizic.server.lobby.PickResult
import io.ntole.kvizic.server.lobby.QuestionSource
import io.ntole.kvizic.server.lobby.sampleQuestions
import io.ntole.kvizic.server.mintGuest
import io.ntole.kvizic.server.runTestServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The whole server with questions of the test's own, and every game it records. */
internal class GameServer(
    val client: HttpClient,
    val records: List<GameRecord>,
    private val scope: CoroutineScope,
) {
    /** A fresh guest, with a name for the test's messages. */
    suspend fun guest(): SessionDto = client.mintGuest()

    /** Opens a socket for [ticket] and says hello; [autoPong] answers every ping. */
    suspend fun connect(
        ticket: TicketDto,
        protocol: Int = Protocol.VERSION,
        platform: String = "android",
        build: Int = 100,
        autoPong: Boolean = true,
    ): TestSocket =
        open(autoPong).also { socket ->
            socket.send(ClientMessage.Hello(ticket.ticket, protocol, platform, build))
        }

    /** Opens a socket and sends nothing. */
    suspend fun open(autoPong: Boolean = true): TestSocket =
        TestSocket(client.webSocketSession(KvizicApi.Paths.PLAY), scope, autoPong)

    suspend fun create(
        who: SessionDto,
        settings: LobbySettingsDto = LobbySettingsDto(questionCount = 5, secondsPerQuestion = 10),
    ): TicketDto = client.createLobby(who, settings).bodyOrFail()

    suspend fun join(
        who: SessionDto,
        code: String,
    ): TicketDto = client.joinLobby(who, code).bodyOrFail()

    /** A guest with a seat in the lobby [code] names, and a socket in it, welcomed and snapshotted. */
    suspend fun joinWithSocket(code: String): Pair<SessionDto, TestSocket> {
        val who = guest()
        val socket = connect(join(who, code))
        socket.await<ServerMessage.Snapshot>()
        return who to socket
    }
}

/**
 * The whole server, as [runTestServer] stands it up, asking [questions] in every game and keeping the
 * games it records.
 */
internal fun runGameServer(
    name: String,
    questions: List<GameQuestion> = sampleQuestions(10),
    configure: (ServerConfig) -> ServerConfig = { it },
    block: suspend ApplicationTestBuilder.(GameServer) -> Unit,
) {
    val records = CopyOnWriteArrayList<GameRecord>()
    val parts =
        GameParts(
            questions = QuestionSource { request -> PickResult(questions.take(request.count)) },
            results = { record -> records += record },
        )
    runTestServer(name, configure, parts = parts) { client, _ ->
        kotlinx.coroutines.coroutineScope {
            val server = GameServer(client, records, this)
            try {
                block(server)
            } finally {
                coroutineContext[Job]?.children?.forEach { it.cancel() }
            }
        }
    }
}

internal suspend fun HttpClient.createLobby(
    who: SessionDto,
    settings: LobbySettingsDto,
): HttpResponse =
    post(KvizicApi.Paths.LOBBIES) {
        bearerAuth(who.accessToken)
        contentType(ContentType.Application.Json)
        setBody(CreateLobbyRequest(settings))
    }

internal suspend fun HttpClient.joinLobby(
    who: SessionDto,
    code: String,
): HttpResponse =
    post(KvizicApi.Paths.LOBBY_JOINS) {
        bearerAuth(who.accessToken)
        contentType(ContentType.Application.Json)
        setBody(JoinLobbyRequest(code))
    }

internal suspend fun HttpClient.previewLobby(
    who: SessionDto,
    code: String,
): HttpResponse = get(KvizicApi.Paths.LOBBY_PREVIEWS.replace("{code}", code)) { bearerAuth(who.accessToken) }

internal suspend fun HttpClient.quickPlay(who: SessionDto): HttpResponse =
    post(KvizicApi.Paths.QUICK_PLAY) { bearerAuth(who.accessToken) }

internal suspend fun HttpClient.soloRun(who: SessionDto): HttpResponse =
    post(KvizicApi.Paths.SOLO_RUNS) { bearerAuth(who.accessToken) }

internal suspend fun HttpClient.rejoinLobby(who: SessionDto): HttpResponse =
    post(KvizicApi.Paths.LOBBY_REJOINS) { bearerAuth(who.accessToken) }

internal suspend fun HttpClient.publicLobbies(who: SessionDto): LobbyListDto =
    get(KvizicApi.Paths.LOBBIES) { bearerAuth(who.accessToken) }.bodyOrFail()

internal suspend inline fun <reified T> HttpResponse.bodyOrFail(): T {
    if (!status.isSuccess()) fail("expected success, got $status: ${body<String>()}")
    return body()
}

/**
 * One client socket: every frame it gets decoded into [history], pings answered unless [autoPong] is
 * off, and how the server closed it in [closed].
 */
internal class TestSocket(
    val session: DefaultClientWebSocketSession,
    scope: CoroutineScope,
    private val autoPong: Boolean,
) {
    val history = CopyOnWriteArrayList<ServerMessage>()
    val closed = CompletableDeferred<CloseReason?>()
    private var cursor = 0
    private var commands = 0

    private val reader: Job =
        scope.launch {
            try {
                for (frame in session.incoming) {
                    if (frame !is Frame.Text) continue
                    val message = ProtocolJson.decodeFromString(ServerMessage.serializer(), frame.readText())
                    history += message
                    // Once the server's close frame has come, a pong cannot go out, but the frames the
                    // server sent before it are still to be read.
                    if (message is ServerMessage.Ping && autoPong) runCatching { send(ClientMessage.Pong(message.seq)) }
                }
            } catch (ended: Exception) {
                // The session went: its close reason says how.
            }
            closed.complete(withTimeoutOrNull(5.seconds) { session.closeReason.await() })
        }

    suspend fun send(message: ClientMessage) {
        session.send(Frame.Text(ProtocolJson.encodeToString(ClientMessage.serializer(), message)))
    }

    fun nextId(): Int = ++commands

    /** The first [T] matching [where] after the one this socket awaited last, waiting up to [timeout]. */
    suspend inline fun <reified T : ServerMessage> await(
        timeout: Duration = 5.seconds,
        crossinline where: (T) -> Boolean = { true },
    ): T = awaitMatching(timeout, T::class.simpleName.orEmpty()) { it is T && where(it) } as T

    suspend fun awaitMatching(
        timeout: Duration,
        what: String,
        matches: (ServerMessage) -> Boolean,
    ): ServerMessage =
        withTimeoutOrNull(timeout) {
            while (true) {
                val found = (cursor until history.size).firstOrNull { matches(history[it]) }
                if (found != null) {
                    cursor = found + 1
                    return@withTimeoutOrNull history[found]
                }
                delay(5.milliseconds)
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        } ?: fail("no $what within $timeout; got ${history.map { it::class.simpleName }}")

    /** The close code the server closed this socket with, waiting up to [timeout]. */
    suspend fun closeCode(timeout: Duration = 5.seconds): Short? =
        withTimeoutOrNull(timeout) { closed.await() }?.code
            ?: if (closed.isCompleted) null else fail("still open after $timeout")

    /** Whether the socket is still open after [wait]. */
    suspend fun staysOpenFor(wait: Duration): Boolean = withTimeoutOrNull(wait) { closed.await() } == null

    inline fun <reified T : ServerMessage> all(): List<T> = history.filterIsInstance<T>()

    /** After its snapshot, every state change of this socket is the next version, none missed. */
    fun assertVersionsInOrder() {
        var version: Long? = null
        history.forEach { message ->
            when (message) {
                is ServerMessage.Snapshot -> {
                    version?.let { assertTrue(message.v >= it, "a snapshot at ${message.v} after $it") }
                    version = message.v
                }

                is ServerMessage.StateChange -> {
                    val before = version ?: fail("${message::class.simpleName} before any snapshot")
                    assertEquals(before + 1, message.v, "${message::class.simpleName} out of order")
                    version = message.v
                }

                else -> {}
            }
        }
    }

    suspend fun leave() = send(ClientMessage.Leave)

    fun cancelReader() = reader.cancel()
}

/** Where the right answer is among [opened]'s options: the sample questions' first, `….0`. */
internal fun rightOptionOf(opened: ServerMessage.AnswersOpened): Int = opened.options.indexOfFirst { it.endsWith(".0") }

/** Waits for [block] to hold, up to [timeout]. */
internal suspend fun eventually(
    timeout: Duration = 5.seconds,
    what: String = "the condition",
    block: suspend () -> Boolean,
) {
    withTimeoutOrNull(timeout) {
        while (!block()) delay(10.milliseconds)
    } ?: fail("$what did not hold within $timeout")
}

/** Runs [block] with a time limit, failing with [what] when it is not done in time. */
internal suspend fun <T> within(
    timeout: Duration,
    what: String,
    block: suspend () -> T,
): T = runCatching { withTimeout(timeout) { block() } }.getOrElse { fail("$what: ${it.message}") }

/** [this] with the game's timings changed by [change]. */
internal fun ServerConfig.withTimings(change: GameTimings.() -> GameTimings): ServerConfig =
    copy(game = game.copy(timings = game.timings.change()))
