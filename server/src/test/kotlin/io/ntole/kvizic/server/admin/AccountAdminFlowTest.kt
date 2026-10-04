package io.ntole.kvizic.server.admin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.player.AccountSort
import io.ntole.kvizic.core.player.AdminAccountDetailDto
import io.ntole.kvizic.core.player.AdminAccountPageDto
import io.ntole.kvizic.server.FLOW_ADMIN_TOKEN
import io.ntole.kvizic.server.mintGuest
import io.ntole.kvizic.server.runTestServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The moderator reading the players: the list, its order, its search and its pages, and one account. */
class AccountAdminFlowTest {
    private suspend fun HttpClient.accounts(
        sort: AccountSort? = null,
        search: String? = null,
        cursor: String? = null,
        limit: Int? = null,
    ): AdminAccountPageDto =
        get(KvizicApi.Paths.ADMIN_ACCOUNTS) {
            header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN)
            sort?.let { parameter(KvizicApi.Query.SORT, it.name) }
            search?.let { parameter(KvizicApi.Query.SEARCH, it) }
            cursor?.let { parameter(KvizicApi.Query.CURSOR, it) }
            limit?.let { parameter(KvizicApi.Query.LIMIT, it) }
        }.body()

    private suspend fun HttpClient.account(
        id: String,
        token: String? = FLOW_ADMIN_TOKEN,
    ): HttpResponse = get("/v1/admin/accounts/$id") { token?.let { header(KvizicApi.Headers.ADMIN_TOKEN, it) } }

    @Test
    fun `every account is listed with its stats and when it was seen`() =
        runTestServer("admin-accounts-list") { client, _ ->
            val ids = List(3) { client.mintGuest().playerId }

            val page = client.accounts()

            assertEquals(3, page.total)
            assertNull(page.nextCursor)
            assertEquals(ids.toSet(), page.accounts.map { it.playerId }.toSet())
            page.accounts.forEach {
                assertEquals(1, it.level)
                assertEquals(0, it.stats.gamesPlayed)
                assertNotNull(it.lastSeenAt)
                assertTrue(!it.playGamesLinked)
            }
        }

    @Test
    fun `pages follow each other with the cursor and end`() =
        runTestServer("admin-accounts-pages") { client, _ ->
            repeat(5) { client.mintGuest() }

            val first = client.accounts(sort = AccountSort.CREATED, limit = 2)
            val second = client.accounts(sort = AccountSort.CREATED, limit = 2, cursor = first.nextCursor)
            val third = client.accounts(sort = AccountSort.CREATED, limit = 2, cursor = second.nextCursor)

            assertEquals(listOf(2, 2, 1), listOf(first, second, third).map { it.accounts.size })
            assertNull(third.nextCursor)
            assertEquals(5, (first.accounts + second.accounts + third.accounts).map { it.playerId }.toSet().size)
        }

    @Test
    fun `a search finds a player by the start of their id or a part of their name`() =
        runTestServer("admin-accounts-search") { client, _ ->
            val ids = List(3) { client.mintGuest().playerId }
            val named = client.accounts().accounts.first { it.playerId == ids[1] }

            assertEquals(listOf(ids[1]), client.accounts(search = ids[1].take(12)).accounts.map { it.playerId })
            assertTrue(
                client.accounts(search = named.displayName.drop(1).take(4)).accounts.any { it.playerId == ids[1] },
            )
            assertEquals(0, client.accounts(search = "no such player").total)
        }

    @Test
    fun `one account comes with its topics and games, and one that is not there is 404`() =
        runTestServer("admin-accounts-detail") { client, _ ->
            val id = client.mintGuest().playerId

            val detail = client.account(id).body<AdminAccountDetailDto>()

            assertEquals(id, detail.account.playerId)
            assertEquals(emptyList(), detail.recentGames)
            assertEquals(HttpStatusCode.NotFound, client.account("nobody").status)
        }

    @Test
    fun `a wrong sort or cursor is the client's mistake, and no token is forbidden`() =
        runTestServer("admin-accounts-refusals") { client, _ ->
            client.mintGuest()

            val badSort =
                client.get(KvizicApi.Paths.ADMIN_ACCOUNTS) {
                    header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN)
                    parameter(KvizicApi.Query.SORT, "FUNNIEST")
                }
            val badCursor =
                client.get(KvizicApi.Paths.ADMIN_ACCOUNTS) {
                    header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN)
                    parameter(KvizicApi.Query.CURSOR, "-4")
                }

            assertEquals(HttpStatusCode.BadRequest, badSort.status)
            assertEquals(HttpStatusCode.BadRequest, badCursor.status)
            assertEquals(HttpStatusCode.Forbidden, client.get(KvizicApi.Paths.ADMIN_ACCOUNTS).status)
            assertEquals(HttpStatusCode.Forbidden, client.account("anyone", token = null).status)
        }
}
