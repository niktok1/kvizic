package io.ntole.kvizic.server.player

import io.ntole.kvizic.core.player.PlayerLevelDto
import io.ntole.kvizic.server.db.Profiles
import io.ntole.kvizic.server.db.appTables
import io.ntole.kvizic.server.db.connectH2
import io.ntole.kvizic.server.db.h2Url
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.Connection
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** What a player's profile says of their level. */
class ProfileStoreTest {
    private val database =
        connectH2(h2Url("kvizic-profile-${UUID.randomUUID()}"), Connection.TRANSACTION_READ_COMMITTED)

    init {
        transaction(database) { SchemaUtils.create(*appTables) }
    }

    @Test
    fun `a new player is at the first level, and one with experience is where it puts them`() {
        val id = transaction(database) { PlayerStore.createGuest().id }
        assertEquals(PlayerLevelDto(1, 0, 10), transaction(database) { ProfileStore.of(id) }?.level)

        transaction(database) { Profiles.update({ Profiles.playerId eq id }) { it[xp] = 95 } }

        assertEquals(
            PlayerLevelDto(number = 4, xpIntoLevel = 5, xpForLevel = 70),
            transaction(database) {
                ProfileStore.of(id)
            }?.level,
        )
    }
}
