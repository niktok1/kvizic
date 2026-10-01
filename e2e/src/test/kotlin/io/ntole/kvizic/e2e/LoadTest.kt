package io.ntole.kvizic.e2e

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ntole.kvizic.core.data.lobby.DefaultLobbySession
import io.ntole.kvizic.core.data.lobby.ReconnectPolicy
import io.ntole.kvizic.core.data.session.DefaultSessionRepository
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.network.ClientBuild
import io.ntole.kvizic.core.network.InMemoryTokenStorage
import io.ntole.kvizic.core.network.KvizicHttpClient
import io.ntole.kvizic.core.network.SessionStore
import io.ntole.kvizic.core.network.UpgradeSignal
import io.ntole.kvizic.core.network.api.AuthApi
import io.ntole.kvizic.core.network.api.LobbyApi
import io.ntole.kvizic.core.network.environment.KvizicEnvironment
import io.ntole.kvizic.core.network.realtime.KtorPlayTransport
import io.ntole.kvizic.server.config.GameConfig
import io.ntole.kvizic.server.lobby.GameTimings
import io.ntole.kvizic.server.lobby.LobbyLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The plan's load test: [PLAYERS] players (480 unless `-Pkvizic.load.players` says) in rooms of eight, every
 * one a real lobby session over its own socket, play a game of five questions at once against the real
 * server, with the production ping and silence timings and short reveals. Not part of `test`: run it with
 * `./gradlew :e2e:loadTest`.
 *
 * What it measures is the fan-out: for each room and question, how far apart its eight players saw the
 * answers open, and the reveal. The server and the players share this JVM and this machine, so the CPU it
 * reports is theirs together, and a spread includes each player's own scheduling: an upper bound on the
 * server's. It fails when a game does not finish or the spread's 99th percentile passes [P99_LIMIT_MS].
 */
class LoadTest {
    @Test
    fun `four hundred and eighty players in rooms of eight play a game at once`(): Unit =
        runBlocking {
            val rooms = PLAYERS / ROOM
            val server =
                E2eServer(questions = e2eQuestions(QUESTIONS)) { config ->
                    config.copy(
                        game =
                            GameConfig(
                                timings = LOAD_TIMINGS,
                                limits = LobbyLimits.DEFAULT.copy(maxSocketsPerAddress = PLAYERS * 2),
                            ),
                    )
                }
            // Every player's REST and socket over two shared engines, which must then hold every player's
            // connection to one host: CIO's own default is a hundred.
            val rest = CIO.create { manyConnections() }
            val sockets = KtorPlayTransport.realtimeClient(CIO.create { manyConnections() })
            val players = List(PLAYERS) { LoadPlayer(server.baseUrl, rest, sockets) }
            val cpu = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
            val cpuBefore = cpu.processCpuTime
            val started = System.nanoTime()
            try {
                val setup = Semaphore(SETUP_AT_ONCE)
                described(players, "setting up the rooms") {
                    coroutineScope {
                        players
                            .chunked(ROOM)
                            .map { room ->
                                async {
                                    val code = setup.withPermit { room.first().create() }
                                    room.drop(1).forEach { setup.withPermit { it.join(code) } }
                                    room.first().awaitEveryone(ROOM)
                                }
                            }.awaitAll()
                    }
                }
                val ready = System.nanoTime()
                described(players, "playing") {
                    coroutineScope {
                        players.chunked(ROOM).forEach { room -> room.first().lobby.start() }
                        players.map { player -> async { player.play() } }.awaitAll()
                    }
                }
                val finished = System.nanoTime()

                val answersOpened = spreads(players, 'A')
                val reveals = spreads(players, 'R')
                val heapMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / MB
                val cores = (cpu.processCpuTime - cpuBefore).toDouble() / (finished - started)
                println(
                    "LOAD $PLAYERS players in $rooms rooms: set up in ${(ready - started) / MS} ms, " +
                        "played in ${(finished - ready) / MS} ms; answers opened ${answersOpened.summary()}; " +
                        "reveals ${reveals.summary()}; heap $heapMb MB; " +
                        "CPU ${"%.2f".format(cores)} cores on average, server and players together",
                )
                val p99 = maxOf(answersOpened.percentile(P99), reveals.percentile(P99))
                assertTrue(
                    p99 <= P99_LIMIT_MS,
                    "a room's players saw a frame up to $p99 ms apart at the 99th percentile",
                )
            } finally {
                players.forEach { it.close() }
                sockets.close()
                rest.close()
                server.close()
            }
        }

    private fun io.ktor.client.engine.cio.CIOEngineConfig.manyConnections() {
        maxConnectionsCount = PLAYERS * 2
        endpoint.maxConnectionsPerRoute = PLAYERS * 2
    }

    /** [block], and on its failure where every player's session stood, counted: what a timeout alone hides. */
    private suspend fun described(
        players: List<LoadPlayer>,
        what: String,
        block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (failure: Throwable) {
            val where =
                players
                    .groupingBy { player ->
                        when (val state = player.lobby.state.value) {
                            is LobbySessionState.InLobby -> {
                                "in a lobby of ${state.lobby.members.size}, ${state.phase::class.simpleName}" +
                                    if (state.reconnecting) ", reconnecting" else ""
                            }

                            else -> {
                                state.toString().take(STATE_SHOWN)
                            }
                        }
                    }.eachCount()
            throw AssertionError("$what failed; the players stood: $where", failure)
        }
    }

    /** For each room and question, how far apart in milliseconds its players first saw [phase]. */
    private fun spreads(
        players: List<LoadPlayer>,
        phase: Char,
    ): List<Long> =
        players.chunked(ROOM).flatMap { room ->
            (0 until QUESTIONS).mapNotNull { index ->
                val seen = room.map { it.seen["$phase$index"] }
                if (seen.any { it == null }) return@mapNotNull null
                val times = seen.filterNotNull()
                (times.max() - times.min()) / MS
            }
        }

    private fun List<Long>.percentile(share: Double): Long =
        if (isEmpty()) Long.MAX_VALUE else sorted()[((size - 1) * share).toInt()]

    private fun List<Long>.summary(): String =
        "p50 ${percentile(P50)} ms, p99 ${percentile(P99)} ms, max ${maxOrNull()} ms over $size"

    /** One player's app, as lean as the test can make it: a session, its REST client and its lobby session. */
    private class LoadPlayer(
        baseUrl: String,
        rest: io.ktor.client.engine.HttpClientEngine,
        sockets: HttpClient,
    ) : AutoCloseable {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val store = SessionStore(InMemoryTokenStorage(), KvizicEnvironment.LOCAL)
        private val http = KvizicHttpClient.create(baseUrl, store, rest)
        val lobby =
            DefaultLobbySession(
                api = LobbyApi(http),
                transport = KtorPlayTransport(baseUrl, ClientBuild("desktop", BUILD), sockets),
                session = DefaultSessionRepository(AuthApi(http), store),
                scope = scope,
                upgrade = UpgradeSignal(),
                policy = ReconnectPolicy(firstWait = 200.milliseconds, longestWait = 2.seconds, attempts = 30),
            )

        /** When this player first saw each question's answers open (`A0`…) and its reveal (`R0`…), in nanoseconds. */
        val seen = ConcurrentHashMap<String, Long>()

        init {
            scope.launch {
                lobby.state.collect { state ->
                    if (state !is LobbySessionState.InLobby || state.reconnecting) return@collect
                    when (val phase = state.phase) {
                        is GamePhase.Answering -> seen.putIfAbsent("A${phase.question.index}", System.nanoTime())
                        is GamePhase.Revealing -> seen.putIfAbsent("R${phase.reveal.index}", System.nanoTime())
                        else -> Unit
                    }
                }
            }
        }

        suspend fun create(): String {
            lobby.create(LobbySettings(questionCount = QUESTIONS, secondsPerQuestion = SECONDS_PER_QUESTION))
            return inLobby().lobby.code ?: error("a private room has a code")
        }

        suspend fun join(code: String) {
            lobby.join(code)
            inLobby()
        }

        suspend fun awaitEveryone(count: Int) {
            withTimeout(SETUP_TIMEOUT) {
                lobby.state.first { state ->
                    state is LobbySessionState.InLobby && state.lobby.members.size == count &&
                        state.lobby.members.all { it.connected }
                }
            }
        }

        /** Answers every question at some moment in its first few seconds, at random, until the results. */
        suspend fun play() {
            withTimeout(GAME_TIMEOUT) {
                repeat(QUESTIONS) { index ->
                    lobby.state.first { state ->
                        state is LobbySessionState.InLobby &&
                            (state.phase as? GamePhase.Answering)?.question?.index == index
                    }
                    delay(Random.nextLong(ANSWER_FROM_MS, ANSWER_BY_MS))
                    lobby.answer(Random.nextInt(OPTIONS))
                }
                lobby.state.first { state ->
                    state is LobbySessionState.InLobby && (state.phase as? GamePhase.Waiting)?.lastResults != null
                }
            }
        }

        private suspend fun inLobby(): LobbySessionState.InLobby =
            withTimeout(SETUP_TIMEOUT) { lobby.state.first { it is LobbySessionState.InLobby } }
                as LobbySessionState.InLobby

        override fun close() {
            lobby.leave()
            scope.cancel()
            http.close()
        }
    }

    private companion object {
        val PLAYERS = System.getProperty("kvizic.load.players")?.toIntOrNull() ?: 480
        const val ROOM = 8
        const val QUESTIONS = 5
        const val OPTIONS = 4
        const val SECONDS_PER_QUESTION = 10
        const val SETUP_AT_ONCE = 32
        const val ANSWER_FROM_MS = 300L
        const val ANSWER_BY_MS = 3_000L
        const val BUILD = 100
        const val STATE_SHOWN = 80
        const val P99_LIMIT_MS = 150L
        const val P50 = 0.5
        const val P99 = 0.99
        const val MS = 1_000_000L
        const val MB = 1_048_576L
        val SETUP_TIMEOUT = 1.minutes
        val GAME_TIMEOUT = 3.minutes

        /** Production's pings and silence, so 480 sockets cost what they would; short waits between steps. */
        val LOAD_TIMINGS =
            GameTimings.DEFAULT.copy(
                countdown = 500.milliseconds,
                countdownWithStragglers = 500.milliseconds,
                readBase = 300.milliseconds,
                readPerCharacter = kotlin.time.Duration.ZERO,
                readMax = 300.milliseconds,
                reveal = 1.seconds,
                revealWithExplanation = 1.seconds,
            )
    }
}
