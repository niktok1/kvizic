package io.ntole.kvizic.server

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.routing
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.server.admin.AdminToken
import io.ntole.kvizic.server.admin.accountAdminRoutes
import io.ntole.kvizic.server.admin.accountDeletionRoutes
import io.ntole.kvizic.server.admin.adminRoutes
import io.ntole.kvizic.server.admin.overviewRoutes
import io.ntole.kvizic.server.auth.GooglePlayGames
import io.ntole.kvizic.server.auth.TokenService
import io.ntole.kvizic.server.auth.authRoutes
import io.ntole.kvizic.server.auth.playGamesRoutes
import io.ntole.kvizic.server.config.ServerConfig
import io.ntole.kvizic.server.db.DatabaseFactory
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.google.googleHttpClient
import io.ntole.kvizic.server.lobby.CodeGuessGuard
import io.ntole.kvizic.server.lobby.LobbyRegistry
import io.ntole.kvizic.server.lobby.QuestionSource
import io.ntole.kvizic.server.lobby.ResultSink
import io.ntole.kvizic.server.lobby.lobbyRoutes
import io.ntole.kvizic.server.match.ResultWriter
import io.ntole.kvizic.server.player.GuestCleanupJob
import io.ntole.kvizic.server.player.playerRoutes
import io.ntole.kvizic.server.plugins.installPlugins
import io.ntole.kvizic.server.plugins.installRateLimits
import io.ntole.kvizic.server.question.DbQuestionSource
import io.ntole.kvizic.server.question.QuestionSeed
import io.ntole.kvizic.server.question.questionAdminRoutes
import io.ntole.kvizic.server.realtime.PlaySockets
import io.ntole.kvizic.server.realtime.playRoutes
import io.ntole.kvizic.server.report.reportAdminRoutes
import io.ntole.kvizic.server.report.reportRoutes
import io.ntole.kvizic.server.topic.TopicCatalog
import io.ntole.kvizic.server.topic.topicAdminRoutes
import io.ntole.kvizic.server.topic.topicRoutes
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

fun main() {
    val config = ServerConfig.fromEnvironment()

    embeddedServer(
        factory = Netty,
        port = config.port,
        host = "0.0.0.0",
        module = { kvizicModule(config) },
    ).start(wait = true)
}

/**
 * Assembles the application. Takes its [config] as a parameter rather than reading the environment, so
 * tests can stand the whole server up against an isolated in-memory database, and the engine it calls
 * Google through, [googleEngine], so they can answer for Google with a MockEngine.
 */
fun Application.kvizicModule(
    config: ServerConfig,
    googleEngine: () -> HttpClientEngine = { CIO.create() },
    parts: GameParts = GameParts(),
) {
    warnAboutInsecureDefaults(config)

    val db = Db(DatabaseFactory.init(config, monitor))
    val tokens = TokenService(config)
    val adminToken = config.adminToken?.let { token -> AdminToken(token) }

    installPlugins(config, tokens)
    installRateLimits(config, tokens, adminToken)

    // Work that outlives a request, never the server.
    val background = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("kvizic-background"))
    val engine = if (config.playGames != null) googleEngine() else null
    val google = engine?.let(::googleHttpClient)
    monitor.subscribe(ApplicationStopped) {
        background.cancel()
        google?.close()
        engine?.close()
    }
    val playGames = config.playGames?.let { client -> GooglePlayGames(client, checkNotNull(google)) }

    // The lobbies: CPU work only, never the database, on the default dispatcher.
    val lobbies = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("kvizic-lobbies"))
    val game = config.game
    val topics = TopicCatalog(db).also { runBlocking { it.refresh() } }
    // Development only: `ServerConfig` refuses a seed file beside a real database.
    config.questionSeedFile?.let { path -> runBlocking { QuestionSeed.load(db, path, topics.ids, log) } }
    val registry =
        LobbyRegistry(
            scope = lobbies,
            timeSource = TimeSource.Monotonic,
            timings = game.timings,
            limits = game.limits,
            scoring = game.scoring,
            questions = parts.questions ?: DbQuestionSource(db),
            results = parts.results ?: ResultWriter(db, background, log),
            topics = { topics.ids },
        )
    registry.start()
    val sockets = PlaySockets(registry, game.timings, game.limits, config.minClientVersions, watchdog = lobbies)
    // Render stops an instance with SIGTERM and waits up to its shutdown delay: until then the old
    // instance keeps its sockets, so the lobbies on it end their games before it goes. Ktor raises this
    // before it closes a connection, and waits for it.
    monitor.subscribe(ApplicationStopPreparing) {
        if (config.drainSeconds == 0) return@subscribe
        val deadline = TimeSource.Monotonic.markNow() + config.drainSeconds.seconds
        log.info("draining ${registry.lobbyCount} lobbies for up to ${config.drainSeconds} s")
        runBlocking {
            registry.drain(deadline)
            sockets.awaitClosed(SOCKETS_FLUSH)
        }
        log.info("drained; ${registry.lobbyCount} lobbies and ${sockets.open} sockets left")
    }
    monitor.subscribe(ApplicationStopped) { lobbies.cancel() }
    val sessionEnded = { playerId: String, sessionId: String? -> registry.sessionEnded(playerId, sessionId) }
    config.guestRetentionDays?.let { days ->
        GuestCleanupJob(db, days.days, config.refreshTokenTtlSeconds.seconds, background).start()
    }

    routing {
        // Render checks this to decide whether the service is live, and the prod deploy's workflow that the
        // commit it deployed is the one serving. In no rate-limit group.
        get(KvizicApi.Paths.HEALTH) {
            call.respond(listOfNotNull("status" to "ok", config.commit?.let { "commit" to it }).toMap())
        }
        // Uptime monitors probe with HEAD (UptimeRobot's free plan always does); 405 read as down.
        head(KvizicApi.Paths.HEALTH) { call.respond(HttpStatusCode.OK) }

        authRoutes(db, tokens, config, sessionEnded = { playerId, sessionId -> sessionEnded(playerId, sessionId) })
        playGamesRoutes(db, tokens, config, playGames)
        playerRoutes(db, playerDeleted = { playerId -> sessionEnded(playerId, null) })
        topicRoutes(db)
        lobbyRoutes(
            db = db,
            registry = registry,
            guard = CodeGuessGuard(config.rateLimits.lobbyCodeFailures),
            topics = { topics.ids },
            ticketTtlMs = game.timings.ticketTtl.inWholeMilliseconds,
            clientIpHeader = config.clientIpHeader,
        )
        playRoutes(sockets, config.clientIpHeader)
        reportRoutes(db)
        adminRoutes(adminToken) { token ->
            accountDeletionRoutes(db, token, playerDeleted = { playerId -> sessionEnded(playerId, null) })
            accountAdminRoutes(db, token)
            topicAdminRoutes(db, token, topics)
            questionAdminRoutes(db, token, topics)
            reportAdminRoutes(db, token)
            overviewRoutes(db, token, live = registry::live)
        }
    }
}

private fun Application.warnAboutInsecureDefaults(config: ServerConfig) {
    if (config.usesDevJwtSecret) {
        log.warn(
            "JWT_SECRET is unset: using the built-in development secret. Every issued token is forgeable by " +
                "anyone with the source. Set JWT_SECRET before exposing this.",
        )
    }
    if (config.isEphemeralDatabase) {
        log.warn("DATABASE_URL is unset: running on in-memory H2. Everything is discarded on shutdown.")
    }
    if (config.onRender && config.clientIpHeader == null) {
        log.warn(
            "CLIENT_IP_HEADER is unset on Render, so every request's address is Render's proxy and every client " +
                "shares each per-address rate limit. render.yaml sets it to CF-Connecting-IP.",
        )
    }
    if (config.playGames == null) {
        log.info("PLAY_GAMES_CLIENT_ID and PLAY_GAMES_CLIENT_SECRET are unset: Play Games sign-in is off.")
    }
    if (config.guestRetentionDays == null) {
        log.info("GUEST_RETENTION_DAYS is 0: the guest clean-up is off.")
    }
    if (config.allowedWebOrigins.isEmpty()) {
        log.info("ALLOWED_WEB_ORIGINS is unset: browser clients will be blocked by CORS.")
    }
    if (config.adminToken == null) {
        log.warn("ADMIN_TOKEN is unset: moderation is off, and the admin routes are not served.")
    } else if (config.usesShortAdminToken) {
        log.warn(
            "ADMIN_TOKEN is shorter than ${ServerConfig.MIN_ADMIN_TOKEN_LENGTH} characters. Use a random one, " +
                "such as the output of openssl rand -hex 32.",
        )
    }
}

/** How long a stop waits, after the drain, for the sockets' last frames to reach the players. */
private val SOCKETS_FLUSH = 5.seconds

/** What a test puts in place of the game's own parts; null keeps the server's own. */
class GameParts(
    val questions: QuestionSource? = null,
    val results: ResultSink? = null,
)
