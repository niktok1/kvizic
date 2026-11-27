package io.ntole.kvizic.server.shop

import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.db.PlayerItems
import io.ntole.kvizic.server.db.Profiles
import io.ntole.kvizic.server.db.appTables
import io.ntole.kvizic.server.db.connectH2
import io.ntole.kvizic.server.db.h2Url
import io.ntole.kvizic.server.player.AccountDeletion
import io.ntole.kvizic.server.plugins.ApiFailure
import io.ntole.kvizic.server.question.BankFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.Connection
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Buying with coins and wearing what was bought, alone and with buys at once. */
class ShopStoreTest {
    private val database =
        connectH2(h2Url("kvizic-shop-${UUID.randomUUID()}"), Connection.TRANSACTION_READ_COMMITTED)
    private val db = Db(database)

    init {
        transaction(database) {
            SchemaUtils.create(*appTables)
            BankFixtures.player("ana")
        }
    }

    private fun <T> read(block: () -> T): T = runBlocking { db.query { block() } }

    private fun give(coins: Int) =
        read { Profiles.update({ Profiles.playerId eq "ana" }) { it[Profiles.coins] = coins } }

    private fun profile() =
        read {
            Profiles
                .select(Profiles.coins, Profiles.frameId, Profiles.titleId, Profiles.crownId)
                .where { Profiles.playerId eq "ana" }
                .single()
                .let { listOf(it[Profiles.coins], it[Profiles.frameId], it[Profiles.titleId], it[Profiles.crownId]) }
        }

    private fun paid() =
        read { PlayerItems.selectAll().associate { it[PlayerItems.itemId] to it[PlayerItems.pricePaid] } }

    private fun buy(id: String) = read { ShopStore.buy("ana", checkNotNull(Shop.item(id)), now = 7) }

    private fun failure(block: () -> Unit) = assertFailsWith<ApiFailure> { block() }.code

    @Test
    fun `a buy takes the price once, and one the coins do not cover takes nothing`() {
        give(120)
        buy("frame.iron")
        buy("frame.iron")
        assertEquals(ErrorCode.NOT_ENOUGH_COINS, failure { buy("crown.iron") })

        assertEquals(20, profile().first())
        assertEquals(mapOf("frame.iron" to 100), paid())
        read { AccountDeletion.delete("ana") }
        assertEquals(emptyMap(), paid(), "what a player bought goes with their account")
    }

    @Test
    fun `buys at once never spend more coins than there are`() {
        give(150)
        val ids = Shop.ITEMS.map { it.id }.take(10)
        runBlocking(Dispatchers.IO) {
            ids
                .map { id ->
                    async { runCatching { db.query { ShopStore.buy("ana", checkNotNull(Shop.item(id)), 7) } } }
                }.awaitAll()
        }

        val left = profile().first() as Int
        assertTrue(left >= 0, "$left left")
        assertEquals(150, left + paid().values.sum())
    }

    @Test
    fun `only a frame, a title and a crown of theirs are worn`() {
        give(300)
        buy("frame.iron")
        buy("crown.iron")
        read {
            PlayerItems.insert {
                it[playerId] = "ana"
                it[itemId] = "title.lucky"
                it[pricePaid] = 30
                it[boughtAt] = 1
            }
        }

        assertTrue(read { ShopStore.wear("ana", "frame.iron", "title.lucky", "crown.iron") })
        assertEquals(listOf(120, "frame.iron", "title.lucky", "crown.iron"), profile())
        val refused =
            listOf(
                Triple("frame.bronze", null, null),
                Triple("crown.iron", null, null),
                Triple(null, "frame.iron", null),
                Triple(null, null, "frame.iron"),
                Triple(null, "no.such", null),
            )
        refused.forEach { (frame, title, crown) ->
            val code = failure { read { ShopStore.wear("ana", frame, title, crown) } }
            assertEquals(ErrorCode.VALIDATION_FAILED, code, "$frame $title $crown")
        }
        read { ShopStore.wear("ana", null, null, null) }
        assertEquals(listOf(120, null, null, null), profile())
    }
}
