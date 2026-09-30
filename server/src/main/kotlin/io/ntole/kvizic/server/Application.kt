package io.ntole.kvizic.server

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.server.admin.AdminToken
import io.ntole.kvizic.server.admin.LiveCounts
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
import io.ntole.kvizic.server.player.GuestCleanupJob
import io.ntole.kvizic.server.player.playerRoutes
import io.ntole.kvizic.server.plugins.installPlugins
import io.ntole.kvizic.server.plugins.installRateLimits
import io.ntole.kvizic.server.topic.topicAdminRoutes
import io.ntole.kvizic.server.topic.topicRoutes
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

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
    config.guestRetentionDays?.let { days ->
        GuestCleanupJob(db, days.days, config.refreshTokenTtlSeconds.seconds, background).start()
    }

    routing {
        // Render checks this to decide whether the service is live. In no rate-limit group.
        get(KvizicApi.Paths.HEALTH) {
            call.respond(mapOf("status" to "ok"))
        }

        authRoutes(db, tokens, config, sessionEnded = { _, _ -> })
        playGamesRoutes(db, tokens, config, playGames)
        playerRoutes(db, playerDeleted = {})
        topicRoutes(db)
        adminRoutes(adminToken) { token ->
            accountDeletionRoutes(db, token, playerDeleted = {})
            topicAdminRoutes(db, token)
            overviewRoutes(db, token, live = { LiveCounts() })
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
