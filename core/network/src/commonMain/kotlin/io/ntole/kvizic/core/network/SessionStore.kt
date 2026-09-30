package io.ntole.kvizic.core.network

import io.ntole.kvizic.core.auth.SessionDto
import io.ntole.kvizic.core.network.environment.KvizicEnvironment
import kotlinx.serialization.json.Json

/**
 * Typed view over [TokenStorage] holding the current session for [environment]'s server.
 *
 * The whole [SessionDto] is stored as one JSON blob rather than as separate keys, so a session can never
 * be half-written: a torn write would otherwise leave an access token paired with someone else's refresh
 * token.
 *
 * Each environment keeps its session under a key of its own ([keyFor]), because on desktop, iOS and web
 * one storage serves every environment's build. With one key, a build for one server would send
 * another's tokens to it, have them refused, and replace that server's guest by a fresh one.
 */
public class SessionStore(
    private val storage: TokenStorage,
    environment: KvizicEnvironment,
    private val json: Json = KvizicJson,
) {
    private val key = keyFor(environment)

    public fun read(): SessionDto? {
        val raw = storage.read(key) ?: return null
        return runCatching { json.decodeFromString<SessionDto>(raw) }.getOrNull()
    }

    /** Returns once [session] is durable, as [TokenStorage.write] promises. */
    public suspend fun write(session: SessionDto) {
        storage.write(key, json.encodeToString(session))
    }

    public suspend fun clear() {
        storage.remove(key)
    }

    internal companion object {
        /** The key [environment]'s session is stored under: `kvizic.session.` and the environment's name. */
        fun keyFor(environment: KvizicEnvironment): String = "kvizic.session.${environment.name.lowercase()}"
    }
}
