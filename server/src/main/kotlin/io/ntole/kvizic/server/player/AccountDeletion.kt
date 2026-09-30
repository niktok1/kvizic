package io.ntole.kvizic.server.player

import io.ntole.kvizic.server.db.Players
import io.ntole.kvizic.server.plugins.ApiFailure
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select

/**
 * A player deleting their account, which Google Play asks a game with accounts to offer: the player's
 * own, the moderator's on their request, and the guest clean-up's.
 */
object AccountDeletion {
    /**
     * Deletes [playerId] and everything of theirs, in one transaction, which it must run inside. Every
     * table that names a player cascades from their row, but a report, which stays for the moderator with
     * nobody as its reporter. A player gone already is 401, as any request for them is.
     *
     * The row is locked first, so nothing of theirs can be added meanwhile: a row naming them waits on the
     * lock through its foreign key and fails once this commits, and its rerun finds them gone. The caller
     * closes their live sockets once this has committed.
     */
    fun delete(playerId: String) {
        Players
            .select(Players.id)
            .where { Players.id eq playerId }
            .forUpdate()
            .singleOrNull()
            ?: throw ApiFailure.unauthorized("unknown player")

        Players.deleteWhere { Players.id eq playerId }
    }
}
