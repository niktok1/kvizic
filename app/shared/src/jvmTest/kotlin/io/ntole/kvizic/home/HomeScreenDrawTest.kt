package io.ntole.kvizic.home

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.GameError
import io.ntole.kvizic.core.domain.lobby.LobbyExit
import io.ntole.kvizic.core.domain.player.NameSource
import io.ntole.kvizic.core.domain.player.PlayerStats
import io.ntole.kvizic.core.domain.player.Profile
import io.ntole.kvizic.descriptions
import io.ntole.kvizic.everyText
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.nodes
import io.ntole.kvizic.renderSettled
import io.ntole.kvizic.room.Entry
import io.ntole.kvizic.room.EntryWay
import io.ntole.kvizic.tap
import io.ntole.kvizic.texts
import io.ntole.kvizic.theme.GameTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Home drawn off screen in each language, on a small phone: the player and their games, the game's sign,
 * Quick play first with how many play, the other ways into a room under it, and solo last; why the last
 * room ended, and why a seat could not be taken, each until taken down.
 */
class HomeScreenDrawTest {
    @Test
    fun `Home shows the player, the counts and every way into a room`() {
        Language.entries.forEach { language ->
            val strings = stringsOf(language)
            val words = strings.game
            val scene = scene(HomeState(profile = PROFILE), language, counts = HomeCounts(128, 7, 12))
            try {
                val shown = scene.everyText()
                listOf(
                    words.games.of(12, language) + " · " + words.wins.of(3, language),
                    words.tagline,
                    words.quickPlay,
                    words.quickPlayHint,
                    words.presence.fill(128, 7),
                    words.createRoom,
                    words.joinByCode,
                    words.publicRooms,
                    "12",
                    words.solo,
                ).forEach { text -> assertTrue(text in shown, "$language: \"$text\" is not in $shown") }
                assertTrue(strings.aboutScreen.title in scene.descriptions(), "$language: ${scene.descriptions()}")
                // Everything stands on the phone's screen, Solo, the last, too.
                val solo = scene.nodes().single { words.solo in it.texts }
                assertTrue(solo.boundsInRoot.bottom <= SHORT_PHONE_HEIGHT, "$language: Solo runs off the screen")
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun `each button does what it says`() {
        val words = stringsOf(Language.DEFAULT).game
        val tapped = mutableListOf<String>()
        val scene =
            scene(
                HomeState(profile = PROFILE),
                Language.DEFAULT,
                actions =
                    HomeActions(
                        quickPlay = { tapped += "quick" },
                        createRoom = { tapped += "create" },
                        joinByCode = { tapped += "join" },
                        publicRooms = { tapped += "public" },
                        solo = { tapped += "solo" },
                        about = { tapped += "about" },
                    ),
            )
        try {
            listOf(
                words.quickPlay,
                words.createRoom,
                words.joinByCode,
                words.publicRooms,
                words.solo,
            ).forEach(scene::tap)
            scene.tap(stringsOf(Language.DEFAULT).aboutScreen.title)
        } finally {
            scene.close()
        }
        assertEquals(listOf("quick", "create", "join", "public", "solo", "about"), tapped)
    }

    @Test
    fun `why the last room ended and why a seat could not be taken show until taken down`() {
        Language.entries.forEach { language ->
            val strings = stringsOf(language)
            var dismissed = 0
            val scene =
                scene(
                    HomeState(profile = PROFILE),
                    language,
                    exit = LobbyExit.KICKED,
                    entry = Entry.Failed(EntryWay.QUICK_PLAY, GameError.TOO_MANY_LOBBIES),
                    actions = HomeActions(dismissExit = { dismissed++ }),
                )
            try {
                val shown = scene.everyText()
                assertTrue(strings.game.exitKicked in shown, "$language: $shown")
                assertTrue(strings.game.tooManyRooms in shown, "$language: $shown")
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun `leaving a room by hand says nothing`() {
        val strings = stringsOf(Language.DEFAULT)
        val scene = scene(HomeState(profile = PROFILE), Language.DEFAULT, exit = LobbyExit.LEFT)
        try {
            assertFalse(strings.game.ok in scene.everyText(), "${scene.everyText()}")
        } finally {
            scene.close()
        }
    }

    @Test
    fun `a seat being taken says so on its button and turns the others off`() {
        val words = stringsOf(Language.DEFAULT).game
        var taps = 0
        val scene =
            scene(
                HomeState(profile = PROFILE),
                Language.DEFAULT,
                entry = Entry.Taking(EntryWay.QUICK_PLAY),
                actions = HomeActions(createRoom = { taps++ }),
            )
        try {
            assertTrue(words.entering in scene.everyText(), "${scene.everyText()}")
            val create = scene.nodes().single { words.createRoom in it.texts }
            assertTrue(SemanticsProperties.Disabled in create.config, "Create is off")
        } finally {
            scene.close()
        }
        assertEquals(0, taps)
    }

    @Test
    fun `a failed read of the player says why and offers Try again`() {
        Language.entries.forEach { language ->
            val strings = stringsOf(language)
            var retries = 0
            val scene =
                scene(
                    HomeState(failure = HomeFailure(CoreError.NETWORK)),
                    language,
                    actions = HomeActions(retry = { retries++ }),
                )
            try {
                assertTrue(strings.offline in scene.everyText(), "$language: ${scene.everyText()}")
                scene.tap(strings.tryAgain)
            } finally {
                scene.close()
            }
            assertEquals(1, retries, "$language")
        }
    }

    private fun scene(
        state: HomeState,
        language: Language,
        actions: HomeActions = HomeActions(),
        counts: HomeCounts? = null,
        entry: Entry = Entry.None,
        exit: LobbyExit? = null,
    ): ImageComposeScene =
        ImageComposeScene(width = SHORT_PHONE_WIDTH, height = SHORT_PHONE_HEIGHT, density = Density(1f)) {
            GameTheme(language) { HomeScreen(state, actions, counts = counts, entry = entry, exit = exit) }
        }.also { it.renderSettled() }

    private companion object {
        /** A small phone, 360 by 640, less its status bar. */
        const val SHORT_PHONE_WIDTH = 360
        const val SHORT_PHONE_HEIGHT = 616

        val PROFILE =
            Profile(
                playerId = "p1",
                displayName = "Брзи Јеж",
                nameSource = NameSource.GENERATED,
                avatarId = "hedgehog",
                playGamesLinked = false,
                stats = PlayerStats(gamesPlayed = 12, gamesWon = 3, answersGiven = 120, answersCorrect = 80),
            )
    }
}
