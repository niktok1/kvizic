package io.ntole.kvizic.core.data.playgames

import io.ntole.kvizic.core.auth.PlayGamesSignInRequest
import io.ntole.kvizic.core.data.session.DefaultSessionRepository
import io.ntole.kvizic.core.data.session.withSessionRecovery
import io.ntole.kvizic.core.domain.playgames.PlayGamesRepository
import io.ntole.kvizic.core.network.api.AuthApi

/**
 * Signs in with a Play Games server auth code through [AuthApi], and stores the session answered in place
 * of the device's, which settles who plays here.
 *
 * The sign-in goes with the session's bearer, so a Play Games player linked to nobody is linked to the
 * player playing, who keeps everything they have. It goes through [withSessionRecovery]: a dead session is
 * a 401 before the code goes anywhere, so the retry sends it, still unspent, as the fresh guest minted for
 * it. Only a sign-in the server took changes the stored session, and only while the device still plays as
 * the player it went out as ([DefaultSessionRepository.replaceIfStill]): the exchange with Google takes
 * seconds, and a change of player landing meanwhile stands.
 */
public class DefaultPlayGamesRepository(
    private val api: AuthApi,
    private val session: DefaultSessionRepository,
) : PlayGamesRepository {
    override fun isSettled(): Boolean = session.isPlayGamesSettled()

    override suspend fun signIn(serverAuthCode: String): String? {
        var sentAs: String? = null
        val signedIn =
            session.withSessionRecovery {
                // Each attempt's own: a dead session's retry goes out as the fresh guest.
                sentAs = session.current()
                api.playGames(PlayGamesSignInRequest(serverAuthCode))
            }
        return signedIn.playerId.takeIf { session.replaceIfStill(sentAs, signedIn) }
    }
}
