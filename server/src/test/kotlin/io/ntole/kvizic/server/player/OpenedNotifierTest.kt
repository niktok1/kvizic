package io.ntole.kvizic.server.player

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.HttpRequestData
import io.ntole.kvizic.server.config.ServerConfig
import io.ntole.kvizic.server.db.appTables
import io.ntole.kvizic.server.db.connectH2
import io.ntole.kvizic.server.db.h2Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The push for an opened app: who sends it, who never does, and what begins a session. */
class OpenedNotifierTest {
    private val topic = "a-long-private-topic-name"

    private fun sent(
        excluded: Set<String>,
        vararg players: String,
    ): List<HttpRequestData> {
        val requests = CopyOnWriteArrayList<HttpRequestData>()
        val client =
            HttpClient(
                MockEngine { request ->
                    requests += request
                    respondOk()
                },
            )
        val job = Job()
        val notifier =
            NtfyOpenedNotifier(OpenedNotice(topic, excluded), client, CoroutineScope(job + Dispatchers.Default))
        players.forEach(notifier::opened)
        runBlocking { job.children.toList().joinAll() }
        return requests
    }

    @Test
    fun `a player opening the app is one post to the topic with nothing of them in it`() {
        val requests = sent(emptySet(), "player-1")
        assertEquals(1, requests.size)
        assertEquals("https://ntfy.sh/$topic", requests.single().url.toString())
    }

    @Test
    fun `an excluded player sends nothing and the others still do`() {
        assertEquals(1, sent(setOf("owner"), "owner", "someone").size)
    }

    @Test
    fun `the topic is in no printed configuration`() {
        assertFalse(OpenedNotice(topic, setOf("owner")).toString().contains(topic))
    }

    @Test
    fun `no topic is off, a short one fails naming the variable and a good one keeps the excluded ids`() {
        assertNull(ServerConfig.parseOpenedNotice(null, "owner"))
        assertNull(ServerConfig.parseOpenedNotice("  ", null))
        val failure = assertFailsWith<IllegalArgumentException> { ServerConfig.parseOpenedNotice("short", null) }
        assertTrue("NOTIFY_NTFY_TOPIC" in failure.message.orEmpty() && "short" !in failure.message.orEmpty())
        val notice = ServerConfig.parseOpenedNotice(" $topic ", " a, b ,,")
        assertEquals(topic, notice?.topic)
        assertEquals(setOf("a", "b"), notice?.excludedPlayerIds)
    }

    @Test
    fun `a touch begins a session for a player away for half an hour, once`() {
        val database =
            connectH2(h2Url("kvizic-opened-${UUID.randomUUID()}"), Connection.TRANSACTION_READ_COMMITTED)
        transaction(database) { SchemaUtils.create(*appTables) }
        val id = transaction(database) { PlayerStore.createGuest(now = 0L).id }

        val soon = PlayerStore.TOUCH_EVERY_MILLIS + 1
        assertFalse(transaction(database) { PlayerStore.touch(id, now = soon) })

        val away = soon + PlayerStore.SESSION_GAP_MILLIS + 1
        assertTrue(transaction(database) { PlayerStore.touch(id, now = away) })
        assertFalse(transaction(database) { PlayerStore.touch(id, now = away + 1) })
    }
}
