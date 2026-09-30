package io.ntole.kvizic.server.plugins

import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.path
import io.ntole.kvizic.core.api.KvizicApi

/** What [ClientVersionCheck] is installed with. */
class ClientVersionCheckConfig {
    /** The oldest build served on each platform, by its [KvizicApi.ClientPlatform] name; none left out. */
    var minimums: Map<String, Int> = emptyMap()
}

/**
 * Refuses a build older than its platform's minimum with 426
 * [io.ntole.kvizic.core.error.ErrorCode.UPGRADE_REQUIRED], so a
 * build that can no longer work with the server says so, rather than failing some other way. Before
 * anything else of the request, its rate limit, its body and its authentication included, on every
 * path but [KvizicApi.Paths.HEALTH], which Render checks with no headers of the game's.
 *
 * A request is judged only by what it says of itself ([KvizicApi.Headers.CLIENT_PLATFORM] and
 * [KvizicApi.Headers.CLIENT_VERSION]), so one that says nothing passes: the moderation app, and every
 * build from before the headers. So does a platform with no minimum, and a version that is no whole
 * number, which only a broken build sends: refusing it would lock that build out for good, whatever
 * its number. A client could claim a newer build than it is, which only lets it fail some other way.
 */
val ClientVersionCheck =
    createApplicationPlugin("ClientVersionCheck", ::ClientVersionCheckConfig) {
        val minimums = pluginConfig.minimums
        onCall { call ->
            if (minimums.isEmpty() || call.request.path() == KvizicApi.Paths.HEALTH) return@onCall
            val headers = call.request.headers
            val platform = headers[KvizicApi.Headers.CLIENT_PLATFORM]?.trim()?.lowercase() ?: return@onCall
            val minimum = minimums[platform] ?: return@onCall
            val version = headers[KvizicApi.Headers.CLIENT_VERSION]?.trim()?.toIntOrNull() ?: return@onCall
            if (version < minimum) throw ApiFailure.upgradeRequired(platform, version, minimum)
        }
    }
