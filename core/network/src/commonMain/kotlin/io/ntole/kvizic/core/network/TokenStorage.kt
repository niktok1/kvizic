package io.ntole.kvizic.core.network

/**
 * Minimal persistent key-value storage for the session and the device's own settings.
 *
 * Deliberately tiny, so each platform can back it with whatever it already has. The implementations live
 * in this module's platform source sets and are bound by DI in `:app:shared`, which keeps Android's
 * `Context` out of common code.
 *
 * Every key the game keeps starts with `kvizic.`, so nothing of another app's that shares the storage (a
 * browser origin's, say) is ever read or written.
 *
 * Security note: the platform implementations use ordinary preference storage, not the Keychain or
 * EncryptedSharedPreferences. That is enough for a game that stores nothing personal: the one credential
 * kept is the session's refresh token, and no password exists.
 */
public interface TokenStorage {
    public fun read(key: String): String?

    /**
     * Stores [value] under [key], and returns only once it would survive the app's process being killed.
     * Refresh tokens rotate on every use: once the server has answered a refresh, the token it answered
     * with is the one sure to work. The one the app sent works once more at most, so losing the new one to
     * a kill can orphan the guest.
     *
     * Suspending, so that an implementation whose durable write blocks can make it off the caller's thread,
     * which in the app is often the main one. A write asked for is made whole even if the caller is
     * cancelled meanwhile, as a write that did not suspend would be.
     */
    public suspend fun write(
        key: String,
        value: String,
    )

    /** Removes [key], as durably as [write] stores one. */
    public suspend fun remove(key: String)
}

/** Non-persistent storage: for tests, and for a client that keeps nothing, the moderation app's. */
public class InMemoryTokenStorage : TokenStorage {
    private val values = mutableMapOf<String, String>()

    override fun read(key: String): String? = values[key]

    override suspend fun write(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}
