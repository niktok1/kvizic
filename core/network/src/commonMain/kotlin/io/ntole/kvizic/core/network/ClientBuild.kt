package io.ntole.kvizic.core.network

import io.ntole.kvizic.core.api.KvizicApi

/**
 * The build of the game a request comes from: its [platform], one of [KvizicApi.ClientPlatform]'s names,
 * and its build [number], made from the app's version the same way on every platform. The game's HTTP
 * client names both on every request, in [KvizicApi.Headers.CLIENT_PLATFORM] and
 * [KvizicApi.Headers.CLIENT_VERSION], so a server can refuse a build too old to play with the others;
 * the moderation app's names neither, being no build the server ever refuses.
 */
public data class ClientBuild(
    public val platform: String,
    public val number: Int,
) {
    init {
        require(platform in PLATFORMS) { "no client platform: \"$platform\"" }
        require(number >= 1) { "a build number is at least 1: $number" }
    }

    public companion object {
        private val PLATFORMS =
            setOf(
                KvizicApi.ClientPlatform.ANDROID,
                KvizicApi.ClientPlatform.IOS,
                KvizicApi.ClientPlatform.WEB,
                KvizicApi.ClientPlatform.DESKTOP,
            )

        /**
         * This platform's build [number], as its entry point read it from the build, or null when it read
         * none, or none of at least 1: a desktop app started without its `kvizic.app.build` property, say.
         * Such a build names neither header, which the server serves as it serves any build without a
         * minimum.
         */
        public fun of(number: Int?): ClientBuild? =
            number?.takeIf { it >= 1 }?.let { ClientBuild(clientPlatform(), it) }
    }
}

/** This platform's name as [KvizicApi.Headers.CLIENT_PLATFORM] carries it. */
internal expect fun clientPlatform(): String
