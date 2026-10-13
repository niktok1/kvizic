package io.ntole.kvizic.server.player

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.auth.SessionDto
import io.ntole.kvizic.core.player.ProfileDto
import io.ntole.kvizic.core.region.Country
import io.ntole.kvizic.server.realtime.GameServer
import io.ntole.kvizic.server.realtime.bodyOrFail
import io.ntole.kvizic.server.realtime.runGameServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The profile tells the country the server last heard a player's seat taken from. */
class ProfileCountryRouteTest {
    private suspend fun GameServer.profile(who: SessionDto): ProfileDto =
        client.get(KvizicApi.Paths.ME) { bearerAuth(who.accessToken) }.bodyOrFail()

    @Test
    fun `a fresh guest's profile names no country, and one whose seat was taken from Croatia names HR`() =
        runGameServer("profile-country") { server ->
            val ana = server.guest()
            assertNull(server.profile(ana).country, "never said")

            server.create(ana, country = "HR")
            assertEquals(Country.HR, server.profile(ana).country)
        }
}
