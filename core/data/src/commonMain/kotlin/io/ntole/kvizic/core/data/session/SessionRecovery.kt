package io.ntole.kvizic.core.data.session

import io.ntole.kvizic.core.data.mapper.runApi
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.KvizicException

/**
 * Runs [call] through [runApi], recovering once from a session the server has stopped accepting.
 *
 * Ktor's bearer provider already refreshes an expired access token transparently. This handles the case
 * it cannot: the session itself is dead, its refresh token rejected (a dev server's data reset, a row
 * pruned, storage restored from an old backup) or its player gone. Without it, a player in that state
 * would see every authenticated call fail for good, with no way out but reinstalling. The session opened
 * in its place is a fresh guest's.
 *
 * Exactly one retry: if a freshly minted guest is refused as well, the failure is real and goes to the
 * caller.
 */
internal suspend fun <T> DefaultSessionRepository.withSessionRecovery(call: suspend () -> T): T {
    // Captured before the call goes out. By the time it fails, a concurrent caller may already have
    // replaced the dead session, or another client sharing the store refreshed it, and reading the store
    // then would reset the new one too.
    val sentWith = storedSession()
    return try {
        runApi(call)
    } catch (failure: KvizicException) {
        if (failure.error != CoreError.UNAUTHORIZED) throw failure

        resetIfStill(sentWith)
        runApi(call)
    }
}
