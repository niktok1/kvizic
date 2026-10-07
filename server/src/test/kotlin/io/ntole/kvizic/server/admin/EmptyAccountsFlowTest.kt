package io.ntole.kvizic.server.admin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.player.AdminAccountPageDto
import io.ntole.kvizic.core.player.DeleteEmptyAccountsRequest
import io.ntole.kvizic.core.player.DeletedAccountsDto
import io.ntole.kvizic.core.player.EmptyAccountsDto
import io.ntole.kvizic.server.FLOW_ADMIN_TOKEN
import io.ntole.kvizic.server.auth.IdentityProvider
import io.ntole.kvizic.server.db.Identities
import io.ntole.kvizic.server.db.Players
import io.ntole.kvizic.server.db.Profiles
import io.ntole.kvizic.server.db.inTransaction
import io.ntole.kvizic.server.db.serverPool
import io.ntole.kvizic.server.mintGuest
import io.ntole.kvizic.server.runTestServer
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.test.Test
import kotlin.test.assertEquals

/** The moderator clearing out the empty accounts: counted, then deleted only as counted. */
class EmptyAccountsFlowTest {
    private suspend fun HttpClient.count(idleDays: Int): EmptyAccountsDto =
        get(KvizicApi.Paths.ADMIN_EMPTY_ACCOUNTS) {
            header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN)
            parameter(KvizicApi.Query.IDLE_DAYS, idleDays)
        }.body()

    private suspend fun HttpClient.clear(
        idleDays: Int,
        expected: Int,
    ): HttpResponse =
        post(KvizicApi.Paths.ADMIN_EMPTY_ACCOUNTS) {
            header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(DeleteEmptyAccountsRequest(idleDays, expected))
        }

    private suspend fun HttpClient.everyone(): Set<String> =
        get(KvizicApi.Paths.ADMIN_ACCOUNTS) {
            header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN)
            parameter(KvizicApi.Query.LIMIT, KvizicApi.Limits.MAX_PAGE_SIZE)
        }.body<AdminAccountPageDto>().accounts.map { it.playerId }.toSet()

    @Test
    fun `only an idle account with nothing played and no Play Games link is empty, and it goes as counted`() =
        runTestServer("admin-empty-accounts") { client, database ->
            val (empty, answered, linked, played, fresh) = List(5) { client.mintGuest().playerId }
            val longAgo = System.currentTimeMillis() - THREE_DAYS
            database.serverPool().use { pool ->
                pool.inTransaction {
                    listOf(empty, answered, linked, played).forEach { id ->
                        Players.update({ Players.id eq id }) {
                            it[createdAt] = longAgo
                            it[lastSeenAt] = longAgo
                        }
                    }
                    Profiles.update({ Profiles.playerId eq answered }) { it[answersGiven] = 3 }
                    Profiles.update({ Profiles.playerId eq played }) { it[gamesPlayed] = 1 }
                    Identities.insert {
                        it[provider] = IdentityProvider.PLAY_GAMES
                        it[subject] = "g-1"
                        it[playerId] = linked
                        it[createdAt] = 1L
                    }
                }
            }

            assertEquals(1, client.count(idleDays = 1).count)
            assertEquals(0, client.count(idleDays = 7).count, "not idle that long")

            assertEquals(HttpStatusCode.Conflict, client.clear(idleDays = 1, expected = 2).status)
            assertEquals(5, client.everyone().size, "a count that is not the one seen deletes nothing")

            val response = client.clear(idleDays = 1, expected = 1)

            assertEquals(1, response.body<DeletedAccountsDto>().deleted)
            assertEquals(setOf(answered, linked, played, fresh), client.everyone())
        }

    @Test
    fun `a count needs at least a day and the admin token`() =
        runTestServer("admin-empty-accounts-guard") { client, _ ->
            assertEquals(
                HttpStatusCode.BadRequest,
                client
                    .get(KvizicApi.Paths.ADMIN_EMPTY_ACCOUNTS) {
                        header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN)
                        parameter(KvizicApi.Query.IDLE_DAYS, 0)
                    }.status,
            )
            assertEquals(HttpStatusCode.BadRequest, client.clear(idleDays = 0, expected = 0).status)
            assertEquals(
                HttpStatusCode.Forbidden,
                client.get(KvizicApi.Paths.ADMIN_EMPTY_ACCOUNTS) { parameter(KvizicApi.Query.IDLE_DAYS, 1) }.status,
            )
        }

    private companion object {
        const val THREE_DAYS: Long = 3 * 24 * 60 * 60 * 1_000L
    }
}
