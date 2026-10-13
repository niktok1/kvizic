package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.auth.SessionDto
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.lobby.TicketDto
import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.ServerMessage
import io.ntole.kvizic.core.region.ContentLanguages
import io.ntole.kvizic.core.region.Country
import io.ntole.kvizic.server.db.Players
import io.ntole.kvizic.server.db.inTransaction
import io.ntole.kvizic.server.db.serverPool
import io.ntole.kvizic.server.realtime.GameServer
import io.ntole.kvizic.server.realtime.TestSocket
import io.ntole.kvizic.server.realtime.bodyOrFail
import io.ntole.kvizic.server.realtime.eventually
import io.ntole.kvizic.server.realtime.quickPlay
import io.ntole.kvizic.server.realtime.rejoinLobby
import io.ntole.kvizic.server.realtime.runGameServer
import io.ntole.kvizic.server.realtime.soloRun
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The country a seat is taken from, as REST hears it, and what a game is asked for by it. */
class LobbyCountryRoutesTest {
    /** Starts the game of the lobby [host] hosts, and returns what it asked the questions for. */
    private suspend fun GameServer.start(host: TestSocket): PickRequest {
        val before = asked.size
        host.send(ClientMessage.Start(host.nextId()))
        host.await<ServerMessage.CountdownStarted>()
        eventually(what = "the questions asked for") { asked.size > before }
        return asked.last()
    }

    private suspend fun GameServer.connected(ticket: TicketDto): TestSocket =
        connect(ticket).also { it.await<ServerMessage.Snapshot>() }

    /** The country kept for [who], none before one was said. */
    private fun GameServer.keptCountry(who: SessionDto): Country? =
        database.serverPool().use { pool ->
            pool.inTransaction {
                Players
                    .select(Players.country)
                    .where { Players.id eq who.playerId }
                    .single()[Players.country]
            }
        }

    @Test
    fun `the header names the seat's country, Serbia when there is none, UNKNOWN for a name not known here`() =
        runGameServer("country-header") { server ->
            val ana = server.guest()
            val host = server.connected(server.create(ana, country = "HR"))
            server.connected(server.join(server.guest(), host.code(), country = null))
            server.connected(server.join(server.guest(), host.code(), country = "XX"))
            // Any case, as the header's value is parsed.
            server.connected(server.join(server.guest(), host.code(), country = "ba"))

            val asked = server.start(host)
            assertEquals(setOf(Country.HR, Country.RS, Country.UNKNOWN, Country.BA), asked.countries)
            assertEquals(setOf(ContentLanguages.SERBIAN), asked.languages, "no hello said a language")
        }

    @Test
    fun `a country said is kept as the player's last, and none or one not known here leaves it`() =
        runGameServer("country-kept") { server ->
            val ana = server.guest()
            assertNull(server.keptCountry(ana), "never said")

            val room = server.create(ana, country = "HR")
            assertEquals(Country.HR, server.keptCountry(ana))
            server.join(ana, room.code, country = null)
            assertEquals(Country.HR, server.keptCountry(ana), "an old client says nothing")
            server.join(ana, room.code, country = "XX")
            assertEquals(Country.HR, server.keptCountry(ana), "UNKNOWN is not kept")
            server.join(ana, room.code, country = "SI")
            assertEquals(Country.SI, server.keptCountry(ana))
        }

    @Test
    fun `a rejoin takes the held seat to the country it is taken from now`() =
        runGameServer("country-rejoin") { server ->
            val ana = server.guest()
            server.connected(server.create(ana, country = "RS"))
            val back = server.client.rejoinLobby(ana, country = "HR").bodyOrFail<TicketDto>()
            val host = server.connected(back)

            assertEquals(setOf(Country.HR), server.start(host).countries)
        }

    @Test
    fun `a solo run is asked for its player's country and the language their hello said`() =
        runGameServer("country-solo") { server ->
            val ana = server.guest()
            val ticket = server.client.soloRun(ana, country = "MK").bodyOrFail<TicketDto>()
            val host = server.connect(ticket, language = "mk").also { it.await<ServerMessage.Snapshot>() }

            val asked = server.start(host)
            assertEquals(setOf(Country.MK), asked.countries)
            assertEquals(setOf(ContentLanguages.MACEDONIAN), asked.languages)
            assertEquals(ana.playerId, asked.soloPlayer)
        }

    @Test
    fun `quick play hears the country too`() =
        runGameServer("country-quick-play") { server ->
            val ticket = server.client.quickPlay(server.guest(), country = "SI").bodyOrFail<TicketDto>()
            assertEquals(setOf(Country.SI), server.start(server.connected(ticket)).countries)
        }

    private fun TestSocket.code(): String = all<ServerMessage.Snapshot>().first().lobby.code
}
