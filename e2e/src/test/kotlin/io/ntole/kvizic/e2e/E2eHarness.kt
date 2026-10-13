package io.ntole.kvizic.e2e

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.data.lobby.DefaultLobbySession
import io.ntole.kvizic.core.data.lobby.ReconnectPolicy
import io.ntole.kvizic.core.data.session.DefaultSessionRepository
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.LobbyEvent
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.core.network.ClientBuild
import io.ntole.kvizic.core.network.InMemoryTokenStorage
import io.ntole.kvizic.core.network.KvizicHttpClient
import io.ntole.kvizic.core.network.SessionStore
import io.ntole.kvizic.core.network.UpgradeSignal
import io.ntole.kvizic.core.network.api.AuthApi
import io.ntole.kvizic.core.network.api.LobbyApi
import io.ntole.kvizic.core.network.environment.KvizicEnvironment
import io.ntole.kvizic.core.network.realtime.KtorPlayTransport
import io.ntole.kvizic.core.region.ContentLanguages
import io.ntole.kvizic.server.GameParts
import io.ntole.kvizic.server.config.GameConfig
import io.ntole.kvizic.server.config.RequestBudget
import io.ntole.kvizic.server.config.ServerConfig
import io.ntole.kvizic.server.kvizicModule
import io.ntole.kvizic.server.lobby.GameQuestion
import io.ntole.kvizic.server.lobby.GameRecord
import io.ntole.kvizic.server.lobby.GameTimings
import io.ntole.kvizic.server.lobby.PickRequest
import io.ntole.kvizic.server.lobby.PickResult
import io.ntole.kvizic.server.lobby.QuestionSource
import io.ntole.kvizic.server.lobby.QuestionText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** [count] questions of four answers; the right one is always `Тачно N`, wherever the game shuffles it. */
internal fun e2eQuestions(count: Int = 10): List<GameQuestion> =
    List(count) { n ->
        GameQuestion(
            questionId = "q$n",
            text = "Питање број $n?",
            options = listOf("Тачно $n", "Погрешно А$n", "Погрешно Б$n", "Погрешно В$n"),
            correct = 0,
            topicId = "GEOGRAPHY",
        )
    }

/**
 * [e2eQuestions], each explained and with a Croatian translation whose answers stand in the source's slots:
 * the right one `Točno N`, the first.
 */
internal fun e2eTranslatedQuestions(count: Int = 10): List<GameQuestion> =
    e2eQuestions(count).mapIndexed { n, question ->
        question.copy(
            explanation = "Објашњење $n",
            texts =
                mapOf(
                    ContentLanguages.CROATIAN to
                        QuestionText(
                            text = "Pitanje broj $n?",
                            options = listOf("Točno $n", "Netočno A$n", "Netočno B$n", "Netočno C$n"),
                            explanation = "Objašnjenje $n",
                        ),
                ),
        )
    }

/**
 * The real server on Netty, on a port of its own and its own in-memory database, with the game's fast
 * timings, budgets no test comes near, and [questions] in every game. Every game it records is kept, and
 * every pick a game asked for.
 */
internal class E2eServer(
    questions: List<GameQuestion> = e2eQuestions(),
    configure: (ServerConfig) -> ServerConfig = { it },
) : AutoCloseable {
    val records = CopyOnWriteArrayList<GameRecord>()
    val asked = CopyOnWriteArrayList<PickRequest>()
    private val plenty = RequestBudget(requests = 100_000, per = 1.hours)
    val config: ServerConfig =
        ServerConfig
            .fromEnvironment { null }
            .let { base ->
                base.copy(
                    jdbcUrl = "jdbc:h2:mem:kvizic-e2e-${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
                    game = GameConfig(timings = GameTimings.FAST),
                    drainSeconds = 20,
                    rateLimits =
                        base.rateLimits.copy(
                            guests = plenty,
                            refreshes = plenty,
                            me = plenty,
                            lobbyList = plenty,
                            lobbyCreates = plenty,
                            lobbyJoins = plenty,
                            lobbyCodeFailures = plenty,
                            quickPlay = plenty,
                            soloRuns = plenty,
                            socketUpgrades = plenty,
                        ),
                )
            }.let(configure)

    private val server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
        embeddedServer(Netty, port = 0, host = "127.0.0.1") {
            kvizicModule(
                config,
                parts =
                    GameParts(
                        questions =
                            QuestionSource { request ->
                                asked += request
                                PickResult(questions.take(request.count))
                            },
                        results = { record -> records += record },
                    ),
            )
        }.start(wait = false)

    val port: Int =
        runBlocking {
            server.engine
                .resolvedConnectors()
                .first()
                .port
        }
    val baseUrl: String = "http://127.0.0.1:$port"

    /** Stops the server as Render does, draining its lobbies first, and returns once it is down. */
    fun stop() = server.stop(1_000, 5_000)

    override fun close() = server.stop(0, 0)
}

/**
 * One player's app: their own session storage, REST client, and lobby session, whose socket goes
 * through a [FaultProxy] of their own so a test can slow or cut this player alone. REST goes straight
 * to the server. Against a server elsewhere ([baseUrl], the prod check) there is no proxy.
 *
 * A player may say the UI [language] they read in their socket's hello and the [country] they play from on
 * every REST call, as a build after 1.0.x does; one that says neither is a 1.0.x client.
 */
internal class E2ePlayer private constructor(
    val name: String,
    baseUrl: String,
    private val faults: FaultProxy?,
    socketEngine: () -> HttpClientEngine,
    language: String? = null,
    country: String? = null,
) : AutoCloseable {
    constructor(
        name: String,
        server: E2eServer,
        socketEngine: () -> HttpClientEngine = { CIO.create() },
        language: String? = null,
        country: String? = null,
    ) : this(name, server.baseUrl, FaultProxy(server.port), socketEngine, language, country)

    constructor(
        name: String,
        baseUrl: String,
        socketEngine: () -> HttpClientEngine = { CIO.create() },
    ) : this(name, baseUrl, null, socketEngine)

    val proxy: FaultProxy get() = checkNotNull(faults) { "$name plays a server elsewhere, with no proxy" }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = SessionStore(InMemoryTokenStorage(), KvizicEnvironment.LOCAL)
    private val http =
        KvizicHttpClient.create(baseUrl, store, CIO.create()).also { client ->
            if (country != null) {
                client.plugin(HttpSend).intercept { request ->
                    request.headers[KvizicApi.Headers.COUNTRY] = country
                    execute(request)
                }
            }
        }
    val sessions = DefaultSessionRepository(AuthApi(http), store)
    val upgrade = UpgradeSignal()
    val events = CopyOnWriteArrayList<LobbyEvent>()
    private val socketClient = KtorPlayTransport.realtimeClient(socketEngine())
    val lobby =
        DefaultLobbySession(
            api = LobbyApi(http),
            transport =
                if (language == null) {
                    KtorPlayTransport(faults?.baseUrl ?: baseUrl, BUILD, socketClient)
                } else {
                    LanguageTransport(faults?.baseUrl ?: baseUrl, BUILD, language, socketClient)
                },
            session = sessions,
            scope = scope,
            upgrade = upgrade,
            policy = ReconnectPolicy(firstWait = 100.milliseconds, longestWait = 1.seconds, attempts = 30),
        )

    init {
        scope.launch { lobby.events.collect { events += it } }
    }

    val state: LobbySessionState get() = lobby.state.value

    /** The player's own id, once they are in a lobby. */
    val id: String get() = (state as LobbySessionState.InLobby).you

    /** Waits until the state is one [matches] takes, and returns it. */
    suspend fun awaitState(
        timeout: Duration = 10.seconds,
        what: String = "the state",
        matches: (LobbySessionState) -> Boolean,
    ): LobbySessionState =
        try {
            withTimeout(timeout) { lobby.state.first(matches) }
        } catch (late: Exception) {
            throw AssertionError("$name: $what did not come within $timeout; the state is $state", late)
        }

    suspend fun awaitPhase(
        timeout: Duration = 10.seconds,
        what: String = "the phase",
        matches: (GamePhase) -> Boolean,
    ): GamePhase =
        (
            awaitState(timeout, what) { state ->
                state is LobbySessionState.InLobby && !state.reconnecting && matches(state.phase)
            } as LobbySessionState.InLobby
        ).phase

    suspend fun awaitAnswering(index: Int): GamePhase.Answering =
        awaitPhase(what = "question $index's answers") {
            it is GamePhase.Answering && it.question.index == index
        } as GamePhase.Answering

    suspend fun awaitReveal(index: Int): GamePhase.Revealing =
        awaitPhase(what = "question $index's reveal") {
            it is GamePhase.Revealing && it.reveal.index == index
        } as GamePhase.Revealing

    override fun close() {
        lobby.leave()
        scope.cancel()
        faults?.close()
    }

    private companion object {
        val BUILD = ClientBuild("desktop", 100)
    }
}

/** Where the right answer is among [phase]'s options, as the game shuffled them, in Serbian or Croatian. */
internal fun rightOf(phase: GamePhase.Answering): Int =
    phase.options.indexOfFirst { it.startsWith("Тачно") || it.startsWith("Točno") }

internal fun wrongOf(phase: GamePhase.Answering): Int = (rightOf(phase) + 1) % phase.options.size
