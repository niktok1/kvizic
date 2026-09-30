package io.ntole.kvizic.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What turns Play Games on, and the one line a build without it logs. */
class GoogleServiceSettingsTest {
    @Test
    fun `a build with no ids has Play Games off and says which it lacks`() {
        val none = GoogleServiceSettings()

        assertFalse(none.playGamesOn)
        assertEquals(
            listOf("Play Games is off on this build: no kvizic.playgames.appId, kvizic.playgames.serverClientId"),
            none.offLines(),
        )
    }

    @Test
    fun `Play Games needs both its ids`() {
        assertFalse(GoogleServiceSettings(playGamesAppId = "123456789").playGamesOn)
        assertFalse(GoogleServiceSettings(playGamesServerClientId = "1-abc.apps.googleusercontent.com").playGamesOn)
        assertFalse(GoogleServiceSettings(playGamesAppId = " ", playGamesServerClientId = "1-abc").playGamesOn)
        assertEquals(
            "Play Games is off on this build: no kvizic.playgames.serverClientId",
            GoogleServiceSettings(playGamesAppId = "123456789").offLines().single(),
        )

        val both = GoogleServiceSettings("123456789", "1-abc.apps.googleusercontent.com")
        assertTrue(both.playGamesOn)
        assertEquals(emptyList(), both.offLines())
    }
}
