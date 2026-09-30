package io.ntole.kvizic.core.network.environment

/**
 * The server a client build talks to, chosen when the build is made, so a production build cannot reach
 * a development server by accident, nor the other way round.
 *
 * Here rather than in `:app:shared`, which is the game's UI, so that any client can name one, the
 * moderation app included: where a client's requests go is this module's business.
 */
public enum class KvizicEnvironment(
    /** Root URL of the API, scheme included, with no path: every route is an absolute path. */
    public val apiBaseUrl: String,
    /** How a person reads the environment's name, as the game and the moderation app show it. */
    public val displayName: String,
) {
    /**
     * A server on the developer's own machine, `./gradlew :server:run`, at `localhost:8080` on every
     * platform: an Android emulator or phone reaches it through `adb reverse tcp:8080 tcp:8080`, which
     * forwards the device's own port 8080 to the machine's, so no build needs the emulator's `10.0.2.2`.
     */
    LOCAL(apiBaseUrl = "http://localhost:8080", displayName = "Local"),

    /** `kvizic-server-dev` on Render, deployed from every green commit on `main`. */
    DEV(apiBaseUrl = "https://kvizic-server-dev.onrender.com", displayName = "Dev"),

    /**
     * `kvizic-server` on Render, deployed only by hand, through the custom domain rather than Render's own
     * name, so the service can move off Render without stranding an installed build.
     */
    PROD(apiBaseUrl = "https://kvizic-api.ntole.com", displayName = "Prod"),
    ;

    public companion object {
        /**
         * The environment [name] names: `local`, `dev` or `prod`, in any case, trimmed. No name, or a blank
         * one, is [LOCAL], which is what a build that sets none is for.
         *
         * Anything else throws, naming the value, rather than falling back: a build that meant to name an
         * environment and got it wrong should stop at launch, not talk to a server nobody chose.
         */
        public fun parse(name: String?): KvizicEnvironment {
            val trimmed = name?.trim()
            if (trimmed.isNullOrEmpty()) return LOCAL
            return requireNotNull(entries.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }) {
                "KVIZIC_ENV must be local, dev or prod, but is \"$name\""
            }
        }
    }
}
