package io.ntole.kvizic.server.player

import io.ntole.kvizic.server.db.Players
import io.ntole.kvizic.server.db.appTables
import io.ntole.kvizic.server.db.connectH2
import io.ntole.kvizic.server.db.h2Url
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.Connection
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** When a player was last seen: written when they are minted, then at most once in a few minutes. */
class LastSeenTest {
    private val database =
        connectH2(h2Url("kvizic-last-seen-${UUID.randomUUID()}"), Connection.TRANSACTION_READ_COMMITTED)

    init {
        transaction(database) { SchemaUtils.create(*appTables) }
    }

    private fun lastSeen(id: String): Long? =
        transaction(database) {
            Players.select(Players.lastSeenAt).where { Players.id eq id }.single()[Players.lastSeenAt]
        }

    @Test
    fun `a new guest is seen when they are minted`() {
        val id = transaction(database) { PlayerStore.createGuest(now = 1_000L).id }
        assertEquals(1_000L, lastSeen(id))
    }

    @Test
    fun `a touch inside the window changes nothing and one past it moves the time`() {
        val id = transaction(database) { PlayerStore.createGuest(now = 1_000L).id }

        transaction(database) { PlayerStore.touch(id, now = 1_000L + PlayerStore.TOUCH_EVERY_MILLIS) }
        assertEquals(1_000L, lastSeen(id))

        val later = 1_000L + PlayerStore.TOUCH_EVERY_MILLIS + 1
        transaction(database) { PlayerStore.touch(id, now = later) }
        assertEquals(later, lastSeen(id))
    }

    @Test
    fun `a player never seen is touched whenever`() {
        val id = transaction(database) { PlayerStore.createGuest(now = 1_000L).id }
        transaction(database) { Players.update({ Players.id eq id }) { it[lastSeenAt] = null } }

        transaction(database) { PlayerStore.touch(id, now = 2_000L) }

        assertEquals(2_000L, lastSeen(id))
    }
}
