package io.ntole.kvizic.server

import io.ntole.kvizic.server.config.GameConfig
import io.ntole.kvizic.server.config.RateLimits
import io.ntole.kvizic.server.config.ServerConfig
import io.ntole.kvizic.server.lobby.GameTimings

/**
 * The configuration a flow test's server runs on [database] with: the test secret, issuer and audience
 * the tests sign their own tokens with, moderated with [adminToken], budgets no test's traffic comes near
 * unless [rateLimits] says otherwise, and the game's fast timings. Every optional feature is off.
 */
internal fun testServerConfig(
    database: TestDatabaseSettings,
    adminToken: String? = null,
    rateLimits: RateLimits = NO_PRACTICAL_LIMIT,
): ServerConfig =
    ServerConfig(
        port = 0,
        jdbcUrl = database.jdbcUrl,
        dbUser = database.user,
        dbPassword = database.password,
        jwtSecret = "test-secret",
        jwtIssuer = "kvizic-test",
        jwtAudience = "kvizic-test-client",
        accessTokenTtlSeconds = 300,
        refreshTokenTtlSeconds = 3_600,
        refreshGraceSeconds = null,
        allowedWebOrigins = emptyList(),
        adminToken = adminToken,
        rateLimits = rateLimits,
        clientIpHeader = null,
        onRender = false,
        guestRetentionDays = null,
        drainSeconds = 5,
        game = GameConfig(timings = GameTimings.FAST),
    )
