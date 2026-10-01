package io.ntole.kvizic.server.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * [requests] for one key, a player or a client's address, in a fixed window of [per] that starts at the
 * key's first request and refills whole when it ends, as Ktor's limiter counts. So up to twice [requests]
 * can pass where two windows meet, though over any longer span the average holds.
 */
data class RequestBudget(
    val requests: Int,
    val per: Duration,
)

/**
 * What one client may send: a budget for each group of routes, spent apart from every other group's.
 * Each count is overridable by the environment variable [fromEnvironment] names.
 */
data class RateLimits(
    /** `POST /v1/auth/guest`, per address: what bounds a script minting guests. */
    val guests: RequestBudget,
    /** `POST /v1/auth/refresh`, per address. */
    val refreshes: RequestBudget,
    /** `POST /v1/auth/play-games`, per address: each sign-in costs two calls to Google. */
    val playGames: RequestBudget,
    /** `POST /v1/auth/logout`. */
    val logouts: RequestBudget,
    /** `POST /v1/me/deletion`. */
    val deletions: RequestBudget,
    /** `GET /v1/me`. */
    val me: RequestBudget,
    /**
     * `POST /v1/me/avatar`: one row's write, so per minute, which bounds the wait to one; an hour's budget
     * of picks, spent browsing, kept the player waiting most of an hour.
     */
    val avatars: RequestBudget,
    /** `GET /v1/topics`, per address: it needs no session. */
    val topics: RequestBudget,
    /** `GET /v1/lobbies`: the public list, which its screen polls. */
    val lobbyList: RequestBudget,
    /** `POST /v1/lobbies`: each opens a lobby, an actor and a code. */
    val lobbyCreates: RequestBudget,
    /** `POST /v1/lobby-joins`: every join and every rejoin asks for a ticket here. */
    val lobbyJoins: RequestBudget,
    /** Codes that name no lobby, per address, on top of [lobbyJoins]: what bounds guessing a code. */
    val lobbyCodeFailures: RequestBudget,
    /** `POST /v1/quick-play`. */
    val quickPlay: RequestBudget,
    /** `POST /v1/solo-runs`. */
    val soloRuns: RequestBudget,
    /** `POST /v1/reports`. */
    val reports: RequestBudget,
    /** The realtime socket's upgrade, per address. */
    val socketUpgrades: RequestBudget,
    /** Every admin route together, per address. */
    val admin: RequestBudget,
    /** Admin requests with a missing or wrong token, per address: once spent, the address is locked out. */
    val adminTokenFailures: RequestBudget,
) {
    companion object {
        val DEFAULT: RateLimits =
            RateLimits(
                guests = RequestBudget(requests = 60, per = 1.hours),
                refreshes = RequestBudget(requests = 30, per = 1.minutes),
                playGames = RequestBudget(requests = 20, per = 1.minutes),
                logouts = RequestBudget(requests = 30, per = 1.minutes),
                deletions = RequestBudget(requests = 10, per = 1.hours),
                me = RequestBudget(requests = 120, per = 1.minutes),
                avatars = RequestBudget(requests = 30, per = 1.minutes),
                topics = RequestBudget(requests = 60, per = 1.minutes),
                lobbyList = RequestBudget(requests = 30, per = 1.minutes),
                lobbyCreates = RequestBudget(requests = 30, per = 1.hours),
                lobbyJoins = RequestBudget(requests = 30, per = 1.minutes),
                lobbyCodeFailures = RequestBudget(requests = 10, per = 1.minutes),
                quickPlay = RequestBudget(requests = 20, per = 1.minutes),
                soloRuns = RequestBudget(requests = 30, per = 1.hours),
                reports = RequestBudget(requests = 30, per = 1.hours),
                socketUpgrades = RequestBudget(requests = 30, per = 1.minutes),
                admin = RequestBudget(requests = 60, per = 1.minutes),
                adminTokenFailures = RequestBudget(requests = 10, per = 1.minutes),
            )

        /**
         * [DEFAULT], with the count of each budget whose variable is set replaced. A value that is not a
         * whole number of at least 1 fails the boot, naming the variable. Blank counts as unset.
         */
        fun fromEnvironment(env: (String) -> String?): RateLimits {
            fun budget(
                variable: String,
                default: RequestBudget,
            ): RequestBudget {
                val raw = env(variable)?.trim()?.takeIf { it.isNotEmpty() } ?: return default
                val requests = raw.toIntOrNull()
                require(requests != null && requests >= 1) {
                    "$variable is \"$raw\"; expected a whole number of requests of at least 1."
                }
                return default.copy(requests = requests)
            }

            return with(DEFAULT) {
                RateLimits(
                    guests = budget("RATE_LIMIT_GUESTS_PER_HOUR", guests),
                    refreshes = budget("RATE_LIMIT_REFRESHES_PER_MINUTE", refreshes),
                    playGames = budget("RATE_LIMIT_PLAY_GAMES_PER_MINUTE", playGames),
                    logouts = budget("RATE_LIMIT_LOGOUTS_PER_MINUTE", logouts),
                    deletions = budget("RATE_LIMIT_DELETIONS_PER_HOUR", deletions),
                    me = budget("RATE_LIMIT_ME_PER_MINUTE", me),
                    avatars = budget("RATE_LIMIT_AVATARS_PER_MINUTE", avatars),
                    topics = budget("RATE_LIMIT_TOPICS_PER_MINUTE", topics),
                    lobbyList = budget("RATE_LIMIT_LOBBY_LIST_PER_MINUTE", lobbyList),
                    lobbyCreates = budget("RATE_LIMIT_LOBBY_CREATES_PER_HOUR", lobbyCreates),
                    lobbyJoins = budget("RATE_LIMIT_LOBBY_JOINS_PER_MINUTE", lobbyJoins),
                    lobbyCodeFailures = budget("RATE_LIMIT_LOBBY_CODE_FAILURES_PER_MINUTE", lobbyCodeFailures),
                    quickPlay = budget("RATE_LIMIT_QUICK_PLAY_PER_MINUTE", quickPlay),
                    soloRuns = budget("RATE_LIMIT_SOLO_RUNS_PER_HOUR", soloRuns),
                    reports = budget("RATE_LIMIT_REPORTS_PER_HOUR", reports),
                    socketUpgrades = budget("RATE_LIMIT_SOCKET_UPGRADES_PER_MINUTE", socketUpgrades),
                    admin = budget("RATE_LIMIT_ADMIN_PER_MINUTE", admin),
                    adminTokenFailures = budget("RATE_LIMIT_ADMIN_TOKEN_FAILURES_PER_MINUTE", adminTokenFailures),
                )
            }
        }
    }
}
