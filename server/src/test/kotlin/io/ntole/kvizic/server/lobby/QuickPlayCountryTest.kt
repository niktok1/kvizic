package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.region.Country
import io.ntole.kvizic.server.rules.ScoringRules
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Quick play seats a player first where everyone connected plays from their country, then as it always did. */
class QuickPlayCountryTest {
    private class Registry(
        private val test: TestScope,
    ) {
        val registry =
            LobbyRegistry(
                scope = test.backgroundScope,
                timeSource = test.testScheduler.timeSource,
                timings = GameTimings.DEFAULT,
                limits = LobbyLimits.DEFAULT,
                scoring = ScoringRules.DEFAULT,
                questions = { PickResult(sampleQuestions(10)) },
                results = {},
                topics = { TEST_TOPICS },
            )
        private var connections = 0L

        private fun seat(
            name: String,
            country: Country,
        ) = Seat(name, "session-$name", "Играч $name", "fox", country = country)

        /** Seats [name] by [take] and opens their socket, as the client does with the ticket. */
        private suspend fun seated(
            name: String,
            country: Country,
            take: suspend (Seat) -> Joined,
        ): Lobby {
            val joined = take(seat(name, country))
            check(joined.lobby.send(LobbyCommand.Attach(TestConnection(++connections, name, "session-$name"))))
            test.runCurrent()
            return joined.lobby
        }

        suspend fun opens(
            name: String,
            country: Country,
        ): Lobby = seated(name, country) { registry.create(it, PUBLIC, LobbyKind.PUBLIC) }

        suspend fun joins(
            name: String,
            country: Country,
            lobby: Lobby,
        ): Lobby = seated(name, country) { registry.join(it, lobby.code) }

        suspend fun quickPlays(
            name: String,
            country: Country,
        ): Lobby = seated(name, country) { registry.quickPlay(it) }
    }

    @Test
    fun `a room of the player's own country comes before an older and fuller one`() =
        runTest {
            val rooms = Registry(this)
            val serbian = rooms.opens("ana", Country.RS)
            rooms.joins("boris", Country.RS, serbian)
            val croatian = rooms.opens("ceca", Country.HR)

            assertEquals(croatian, rooms.quickPlays("dragan", Country.HR))
            assertEquals(serbian, rooms.quickPlays("eva", Country.RS))
        }

    @Test
    fun `with no room of their country, a player is seated as before, in the fullest waiting one`() =
        runTest {
            val rooms = Registry(this)
            val serbian = rooms.opens("ana", Country.RS)
            rooms.joins("boris", Country.RS, serbian)
            rooms.opens("ceca", Country.HR)

            assertEquals(serbian, rooms.quickPlays("dragan", Country.SI))
        }

    @Test
    fun `a room of mixed countries is nobody's own`() =
        runTest {
            val rooms = Registry(this)
            val mixed = rooms.opens("ana", Country.HR)
            rooms.joins("boris", Country.RS, mixed)
            rooms.joins("ceca", Country.RS, mixed)
            val croatian = rooms.opens("dragan", Country.HR)
            val serbian = rooms.opens("eva", Country.RS)

            assertEquals(croatian, rooms.quickPlays("filip", Country.HR), "the mixed one is fuller and older")
            assertEquals(serbian, rooms.quickPlays("goran", Country.RS), "for all the Serbs in the mixed one")
            assertEquals(mixed, rooms.quickPlays("hana", Country.SI), "and for a Slovene, the fullest as ever")
        }

    @Test
    fun `only those connected count, so a room whose Croat has not come yet is a Serbian room`() =
        runTest {
            val rooms = Registry(this)
            rooms.opens("ana", Country.RS)
            rooms.opens("boris", Country.HR)
            val held = rooms.opens("ceca", Country.RS)
            // A Croat's seat held in it, their socket not open: counted, it would make the room nobody's own.
            rooms.registry.join(
                Seat("dragan", "session-dragan", "Играч dragan", "fox", country = Country.HR),
                held.code,
            )
            testScheduler.runCurrent()

            assertEquals(held, rooms.quickPlays("eva", Country.RS), "the fuller of two Serbian rooms")
        }

    private companion object {
        val PUBLIC = LobbySettingsDto.QUICK_PLAY
    }
}
