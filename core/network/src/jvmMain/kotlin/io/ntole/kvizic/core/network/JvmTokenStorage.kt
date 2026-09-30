package io.ntole.kvizic.core.network

import java.util.prefs.Preferences

/**
 * Desktop-backed [TokenStorage], on the JVM's own per-user preference store, so there is no file path to
 * manage and it works the same on macOS, Windows and Linux.
 *
 * The game's node is [ROOT_PATH]. A named profile ([forProfile]) keeps everything, the session, the
 * analytics id and the language, in a child node of its own, so several desktop instances on one machine
 * are separate players: the way to play a lobby with yourself while building the game.
 */
public class JvmTokenStorage(
    private val prefs: Preferences = Preferences.userRoot().node(ROOT_PATH),
) : TokenStorage {
    override fun read(key: String): String? = prefs.get(key, null)

    override suspend fun write(
        key: String,
        value: String,
    ) {
        prefs.put(key, value)
        prefs.flush()
    }

    override suspend fun remove(key: String) {
        prefs.remove(key)
        prefs.flush()
    }

    public companion object {
        /** The game's node under the user's preferences root. */
        public const val ROOT_PATH: String = "io/ntole/kvizic"

        /** The longest profile name: well within a preference node's name, which may hold 80 characters. */
        public const val MAX_PROFILE_LENGTH: Int = 32

        private val PROFILE = Regex("[A-Za-z0-9_-]{1,$MAX_PROFILE_LENGTH}")

        /**
         * The storage for [profile]: the game's own node for none, and a child node named after it
         * otherwise, under [root], the user's preferences unless a test gives its own. The profile is
         * [checkedProfile]'s first, so a bad one throws before any preference is touched.
         */
        public fun forProfile(
            profile: String?,
            root: Preferences = Preferences.userRoot(),
        ): JvmTokenStorage {
            val name = checkedProfile(profile)
            val game = root.node(ROOT_PATH)
            return JvmTokenStorage(if (name == null) game else game.node(name))
        }

        /**
         * [profile] trimmed, or null for none or a blank one. A profile is letters, digits, `_` and `-`, at
         * most [MAX_PROFILE_LENGTH]; anything else throws, naming it, so a mistyped profile stops the app at
         * launch instead of playing as someone else.
         */
        public fun checkedProfile(profile: String?): String? {
            val name = profile?.trim()
            if (name.isNullOrEmpty()) return null
            require(PROFILE.matches(name)) {
                "KVIZIC_PROFILE must be 1 to $MAX_PROFILE_LENGTH letters, digits, _ or -, but is \"$profile\""
            }
            return name
        }
    }
}
