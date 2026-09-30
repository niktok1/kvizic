package io.ntole.kvizic.server

import io.ntole.kvizic.server.config.RateLimits
import io.ntole.kvizic.server.config.RequestBudget
import kotlin.time.Duration.Companion.minutes

/** [budget] for every group of routes. */
internal fun rateLimitsOf(budget: RequestBudget): RateLimits =
    RateLimits(
        guests = budget,
        refreshes = budget,
        playGames = budget,
        logouts = budget,
        deletions = budget,
        me = budget,
        avatars = budget,
        topics = budget,
        lobbyList = budget,
        lobbyCreates = budget,
        lobbyJoins = budget,
        lobbyCodeFailures = budget,
        quickPlay = budget,
        soloRuns = budget,
        reports = budget,
        socketUpgrades = budget,
        admin = budget,
        adminTokenFailures = budget,
    )

/** Budgets no test's traffic comes near, for a server under test for something else. */
internal val NO_PRACTICAL_LIMIT: RateLimits = rateLimitsOf(RequestBudget(requests = 1_000_000, per = 1.minutes))
