package io.ntole.kvizic.core.domain.session

import kotlinx.coroutines.flow.Flow

/**
 * Who plays on this device, as its stored session names them, watched without minting anyone: for what
 * follows the session rather than asks for one, such as the launch's Play Games sign-in, which waits for
 * a session and signs in again for each new one. Implemented in `:core:data`, beside
 * [SessionRepository], which mints.
 */
public interface CurrentSession {
    /** The player id of the session stored now, or null when there is none: never mints. */
    public fun current(): String?

    /**
     * The player id of the session stored now, if there is one, and then of every session stored after
     * it, a mint's or a Play Games sign-in's, each once, however it came: a new session of the same
     * player's is heard too. A refresh, which keeps its session, is not. Never mints.
     */
    public val sessions: Flow<String>
}
