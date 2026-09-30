package io.ntole.kvizic.server.player

import io.ntole.kvizic.server.auth.IdentityProvider
import io.ntole.kvizic.server.auth.IdentityStore
import io.ntole.kvizic.server.auth.SessionStore
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.db.Players
import io.ntole.kvizic.server.db.Profiles
import io.ntole.kvizic.server.db.appTables
import io.ntole.kvizic.server.db.connectH2
import io.ntole.kvizic.server.db.h2Url
import io.ntole.kvizic.server.printed
import io.ntole.kvizic.server.withLogCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.Connection
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * The guest clean-up through the stores at READ COMMITTED, with the clock fixed: who is deleted, with
 * everything of theirs, and who is kept.
 */
class GuestCleanupTest {
    private val url = h2Url("kvizic-guest-cleanup-${UUID.randomUUID()}")
    private val database = connectH2(url, Connection.TRANSACTION_READ_COMMITTED)
    private val db = Db(database)
    private val now = System.currentTimeMillis()

    init {
        transaction(database) { SchemaUtils.create(*appTables) }
    }

    @Test
    fun `guests idle past the retention are deleted and everyone who can still play is kept`() {
        val idleNoSession = guest(mintedDaysAgo = 91)
        val idleExpiredSession = guest(mintedDaysAgo = 200).also { session(it, expiresDaysAgo = 61) }
        val fresh = guest(mintedDaysAgo = 0).also { session(it, expiresDaysAgo = -30) }
        val justInside = guest(mintedDaysAgo = 89)
        val refreshedLately = guest(mintedDaysAgo = 200).also { session(it, expiresDaysAgo = 10) }
        val liveSession = guest(mintedDaysAgo = 200).also { session(it, expiresDaysAgo = -1) }
        val oneLiveOfTwo =
            guest(mintedDaysAgo = 200).also {
                session(it, expiresDaysAgo = 100)
                session(it, expiresDaysAgo = -5)
            }
        val linked = guest(mintedDaysAgo = 200).also { linkPlayGames(it) }

        val deleted = runBlocking { job().runOnce() }

        assertEquals(2, deleted)
        assertFalse(exists(idleNoSession), "idle 91 days, and nothing left to refresh")
        assertFalse(exists(idleExpiredSession), "its last refresh 91 days ago")
        listOf(fresh, justInside, refreshedLately, liveSession, oneLiveOfTwo, linked).forEach { kept ->
            assertTrue(exists(kept), "$kept is kept")
        }
    }

    @Test
    fun `a second run deletes nothing more and changes nothing`() {
        guest(mintedDaysAgo = 120)

        assertEquals(1, runBlocking { job().runOnce() })
        assertEquals(0, runBlocking { job().runOnce() })
    }

    @Test
    fun `a deleted guest's profile goes with them`() {
        val idle = guest(mintedDaysAgo = 100)

        assertEquals(1, runBlocking { job().runOnce() })

        val profiles =
            transaction(database) { Profiles.select(Profiles.playerId).where { Profiles.playerId eq idle }.count() }
        assertEquals(0, profiles)
    }

    @Test
    fun `two instances cleaning up at once delete each guest once and fail nothing`() {
        val idle = List(GuestCleanupJob.BATCH_SIZE + 20) { guest(mintedDaysAgo = 100) }
        val kept = guest(mintedDaysAgo = 0)

        val counts =
            runBlocking(Dispatchers.IO) {
                List(2) { async { job().runOnce() } }.awaitAll()
            }

        assertEquals(idle.size, counts.sum(), "each deleted by one of the two")
        assertTrue(idle.none(::exists))
        assertTrue(exists(kept))
    }

    @Test
    fun `a run logs one line with the count and no id`() {
        val idle = List(3) { guest(mintedDaysAgo = 100) }

        withLogCapture { logged ->
            runBlocking { job().runOnce() }

            val lines = logged.printed().filter { it.contains("guest clean-up") }
            assertEquals(1, lines.size, "$lines")
            assertTrue(lines.single().startsWith("guest clean-up deleted 3 guests idle 90 days"), lines.single())
            idle.forEach { id -> assertFalse(logged.printed().any { it.contains(id) }, "no id logged") }
        }
    }

    @Test
    fun `the job runs at once when started and stops with its scope`() {
        val idle = guest(mintedDaysAgo = 100)
        val scope = CoroutineScope(Dispatchers.IO)
        val running = GuestCleanupJob(db, 90.days, REFRESH_TTL, scope, interval = 1.hours).start()

        runBlocking {
            withTimeout(10.seconds) {
                while (exists(idle)) delay(10)
            }
            running.cancelAndJoin()
        }

        assertTrue(running.isCancelled)
    }

    private fun job() = GuestCleanupJob(db, 90.days, REFRESH_TTL, CoroutineScope(Dispatchers.IO), now = { now })

    private fun guest(mintedDaysAgo: Int): String =
        transaction(database) {
            val id = PlayerStore.createGuest().id
            Players.update(
                { Players.id eq id },
            ) { row -> row[createdAt] = now - mintedDaysAgo.days.inWholeMilliseconds }
            id
        }

    /** A session of [player]'s whose refresh token expires [expiresDaysAgo] days ago, or ahead when negative. */
    private fun session(
        player: String,
        expiresDaysAgo: Int,
    ) = transaction(database) {
        SessionStore.open(
            player,
            refreshTokenHash = "h-${UUID.randomUUID()}",
            expiresAt = now - expiresDaysAgo.days.inWholeMilliseconds,
        )
    }

    private fun linkPlayGames(player: String) =
        transaction(database) { IdentityStore.signIn(IdentityProvider.PLAY_GAMES, "pg-$player", player) }

    private fun exists(player: String): Boolean =
        transaction(database) { Players.select(Players.id).where { Players.id eq player }.any() }

    private companion object {
        val REFRESH_TTL = 30.days
    }
}
