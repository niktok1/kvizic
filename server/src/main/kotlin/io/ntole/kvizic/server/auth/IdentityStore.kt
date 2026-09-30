package io.ntole.kvizic.server.auth

import io.ntole.kvizic.server.db.Identities
import io.ntole.kvizic.server.player.PlayerStore
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select

/** A service whose players can be linked to players here, and sign in as them. */
enum class IdentityProvider {
    /** Google Play Games Services v2, on Android. */
    PLAY_GAMES,
}

/** The players of other services linked to players here: what makes a Play Games sign-in no-click. */
object IdentityStore {
    /**
     * The player [provider]'s player [subject] signs in as, linking it first when it is linked to none.
     * Must run inside a transaction.
     *
     * A subject linked already signs in as its player, whoever [callerId] is, so a guest meeting it
     * switches to it. One linked to nobody is linked to [callerId], the player the bearer names, who
     * keeps everything they have, unless that player is linked to another of [provider]'s players
     * already, or there is no caller, or the token outlived its player: then to a new player, minted as
     * a guest is. It never fails once Google has vouched for the subject, whose code is spent by then.
     *
     * The reads only spare a write sure to fail. A race is decided by the write and its two constraints:
     * the primary key refuses a second link of one subject, and the unique index a second subject for one
     * player. Neither refusal is caught: Exposed reruns the transaction, which then finds the link made.
     */
    fun signIn(
        provider: IdentityProvider,
        subject: String,
        callerId: String?,
        now: Long = System.currentTimeMillis(),
    ): String {
        linkedPlayerOf(provider, subject)?.let { linked -> return linked }

        val caller = callerId?.takeIf { id -> PlayerStore.find(id) != null }
        val owner = caller?.takeIf { id -> !isLinked(id, provider) } ?: PlayerStore.createGuest().id
        Identities.insert { row ->
            row[Identities.provider] = provider
            row[Identities.subject] = subject
            row[playerId] = owner
            row[createdAt] = now
        }
        return owner
    }

    /** Whether [playerId] is linked to any service's player. Must run inside a transaction. */
    fun isLinked(playerId: String): Boolean =
        Identities
            .select(Identities.playerId)
            .where { Identities.playerId eq playerId }
            .limit(1)
            .any()

    private fun isLinked(
        playerId: String,
        provider: IdentityProvider,
    ): Boolean =
        Identities
            .select(Identities.playerId)
            .where { (Identities.playerId eq playerId) and (Identities.provider eq provider) }
            .limit(1)
            .any()

    private fun linkedPlayerOf(
        provider: IdentityProvider,
        subject: String,
    ): String? =
        Identities
            .select(Identities.playerId)
            .where { (Identities.provider eq provider) and (Identities.subject eq subject) }
            .singleOrNull()
            ?.get(Identities.playerId)
}
