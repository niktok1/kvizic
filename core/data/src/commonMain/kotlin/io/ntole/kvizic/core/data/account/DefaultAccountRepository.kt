package io.ntole.kvizic.core.data.account

import io.ntole.kvizic.core.data.mapper.runApi
import io.ntole.kvizic.core.data.session.DefaultSessionRepository
import io.ntole.kvizic.core.domain.account.AccountRepository
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.network.api.AuthApi
import io.ntole.kvizic.core.network.api.PlayerApi

/**
 * Logs out through [AuthApi] and deletes the account through [PlayerApi]. Neither goes through
 * `withSessionRecovery`: a logout of a dead session has nothing to end, and a deletion's retry would
 * delete the fresh guest minted for a dead session.
 *
 * - A logout is best effort: whatever the server answers, or if it never answers, the session is dropped
 *   here, and the next call mints a fresh guest.
 * - A deletion is not: the session is dropped only once the server has deleted the account, or refuses
 *   the session as a player it no longer has.
 */
public class DefaultAccountRepository(
    private val auth: AuthApi,
    private val players: PlayerApi,
    private val session: DefaultSessionRepository,
) : AccountRepository {
    override suspend fun logOut() {
        // No session, nothing to end: the request would only be refused.
        if (session.storedSession() == null) return
        try {
            runApi { auth.logOut() }
        } catch (unheard: KvizicException) {
            // Best effort. The server keeps the session until its refresh token expires unused, and this
            // device forgets it all the same.
        }
        session.clear()
    }

    override suspend fun deleteAccount() {
        // No session, no account: nothing to delete, and the request would only be refused.
        if (session.storedSession() != null) {
            try {
                runApi { players.deleteAccount() }
            } catch (failure: KvizicException) {
                // A 401, the refresh refused too, is a player the server no longer has: deleted already, from
                // another device or by an answer that never arrived. Anything else deleted nothing.
                if (failure.error != CoreError.UNAUTHORIZED) throw failure
            }
        }
        session.clear()
    }
}
