package io.ntole.kvizic.server.player

import io.ntole.kvizic.core.region.Country
import io.ntole.kvizic.server.db.Players
import io.ntole.kvizic.server.db.appTables
import io.ntole.kvizic.server.db.connectH2
import io.ntole.kvizic.server.db.h2Url
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The country a player last said they play from: written when it changes, and never UNKNOWN. */
class PlayerCountryTest {
    private val database =
        connectH2(h2Url("kvizic-player-country-${UUID.randomUUID()}"), Connection.TRANSACTION_READ_COMMITTED)

    init {
        transaction(database) { SchemaUtils.create(*appTables) }
    }

    private fun guest(): String = transaction(database) { PlayerStore.createGuest().id }

    private fun set(
        id: String,
        country: Country,
    ): Boolean = transaction(database) { PlayerStore.setCountry(id, country) }

    private fun kept(id: String): Country? =
        transaction(database) {
            Players.select(Players.country).where { Players.id eq id }.single()[Players.country]
        }

    @Test
    fun `a new guest has said no country`() {
        assertNull(kept(guest()))
    }

    @Test
    fun `the same country said again writes nothing, and another writes once`() {
        val id = guest()
        assertTrue(set(id, Country.HR))
        assertFalse(set(id, Country.HR), "already kept")
        assertEquals(Country.HR, kept(id))

        assertTrue(set(id, Country.BA))
        assertFalse(set(id, Country.BA))
        assertEquals(Country.BA, kept(id))
    }

    @Test
    fun `UNKNOWN is never kept, over none or over a country`() {
        val id = guest()
        assertFalse(set(id, Country.UNKNOWN))
        assertNull(kept(id))

        set(id, Country.SI)
        assertFalse(set(id, Country.UNKNOWN))
        assertEquals(Country.SI, kept(id))
    }

    @Test
    fun `a player who is not there is not written`() {
        assertFalse(set("no-such-player", Country.MK))
    }
}
