package io.ntole.kvizic.home

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.GameError
import io.ntole.kvizic.core.domain.lobby.LobbyExit
import io.ntole.kvizic.core.domain.player.NameSource
import io.ntole.kvizic.core.domain.player.PlayerLevel
import io.ntole.kvizic.core.domain.player.PlayerStats
import io.ntole.kvizic.core.domain.player.Profile
import io.ntole.kvizic.cutTexts
import io.ntole.kvizic.descriptions
import io.ntole.kvizic.design.skin.Skin
import io.ntole.kvizic.design.skin.Skins
import io.ntole.kvizic.everyNode
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
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Home drawn off screen in each language, on a small phone: the player and their level, the game's sign,
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
                write("home-${language.name.lowercase()}", scene.renderSettled())
                val shown = scene.everyText()
                listOf(
                    words.level.fill(7),
                    words.quickPlay,
                    words.createRoom,
                    words.joinByCode,
                    words.publicRooms,
                    "12",
                    words.solo,
                ).forEach { text -> assertTrue(text in shown, "$language: \"$text\" is not in $shown") }
                assertTrue(strings.settingsScreen.title in scene.descriptions(), "$language: ${scene.descriptions()}")
                // The games and wins are the account's, not Home's; the level's bar is said whole.
                assertFalse(words.games.of(12, language) in shown, "$language: games are on Home")
                assertFalse(words.wins.of(3, language) in shown, "$language: wins are on Home")
                assertTrue(words.xpProgress.fill(40, 130) in scene.descriptions(), "$language: ${scene.descriptions()}")
                // The counts are said whole and shown in no words.
                assertTrue(words.presence.fill(128, 7) in scene.descriptions(), "$language: ${scene.descriptions()}")
                // Everything stands on the phone's screen, Solo, the last, too.
                val solo = scene.nodes().single { words.solo in it.texts }
                assertTrue(solo.boundsInRoot.bottom <= SHORT_PHONE_HEIGHT, "$language: Solo runs off the screen")
            } finally {
                scene.close()
            }
        }
    }

    /**
     * Every word on Home whole, in every skin, on a small phone at the usual font and at a phone's large one
     * (130%, a common setting): „Направи собу“ wraps on its tile rather than losing „собу“.
     */
    @Test
    fun `Home keeps every word whole at a large font`() {
        val words = stringsOf(Language.DEFAULT).game
        Skins.ALL.forEach { skin ->
            listOf(1f, LARGE_FONT).forEach { fontScale ->
                val where = "${skin.id} at $fontScale"
                val scene =
                    scene(
                        HomeState(profile = PROFILE),
                        Language.DEFAULT,
                        counts = HomeCounts(128, 7, 12),
                        skin = skin,
                        fontScale = fontScale,
                    )
                try {
                    write("home-${skin.id}-$fontScale", scene.renderSettled())
                    assertEquals(emptyList(), scene.cutTexts(), "$where: cut")
                    val solo = scene.nodes().single { words.solo in it.texts }
                    assertTrue(solo.boundsInRoot.bottom <= SHORT_PHONE_HEIGHT, "$where: Solo runs off the screen")
                } finally {
                    scene.close()
                }
            }
        }
    }

    @Test
    fun `the game's name stands in the middle of the room between the player and the buttons`() {
        val strings = stringsOf(Language.DEFAULT)
        val scene = scene(HomeState(profile = PROFILE), Language.DEFAULT, counts = HomeCounts(128, 7, 12))
        try {
            val nodes = scene.nodes()
            // The player's block and the settings button share a row; the row ends with the lower of the two.
            val above =
                listOf(strings.game.xpProgress.fill(40, 130), strings.settingsScreen.title)
                    .maxOf { said -> nodes.last { said in it.descriptions }.boundsInRoot.bottom }
            val logo = nodes.single { strings.gameName in it.texts }.boundsInRoot
            val below = nodes.single { strings.game.quickPlay in it.texts }.boundsInRoot.top
            assertTrue(logo.top - above > 0 && below - logo.bottom > 0, "the name is between the two")
            assertEquals(logo.top - above, below - logo.bottom, 1f, "as far from the player as from the buttons")
        } finally {
            scene.close()
        }
    }

    @Test
    fun `the count of players is on Quick play's foot and the button keeps its size without it`() {
        val words = stringsOf(Language.DEFAULT).game
        val with = scene(HomeState(profile = PROFILE), Language.DEFAULT, counts = HomeCounts(128, 7, 12))
        val without = scene(HomeState(profile = PROFILE), Language.DEFAULT)
        try {
            val said = words.presence.fill(128, 7)
            assertTrue(said in with.descriptions(), "${with.descriptions()}")
            assertFalse(said in without.descriptions(), "the count is said before it is known")
            // Said by the button, the one thing to tap, and the count is no row of its own between the buttons.
            val quick = with.nodes().single { words.quickPlay in it.texts }
            assertTrue(said in quick.descriptions, "Quick play does not carry the count: ${quick.descriptions}")

            // Before the first read its place is held, so nothing on Home moves as the counts come.
            fun top(
                scene: ImageComposeScene,
                text: String,
            ) = scene.nodes().single { text in it.texts }.boundsInRoot

            // Its words stay in the middle of the button, as they were without the count.
            fun middle(scene: ImageComposeScene) =
                scene
                    .everyNode()
                    .single { words.quickPlay in it.texts }
                    .boundsInRoot.center.y
            assertEquals(middle(without), middle(with), "the count pushed Quick play's words from the middle")
            listOf(words.quickPlay, words.createRoom, words.publicRooms, words.solo).forEach {
                assertEquals(top(without, it), top(with, it), "\"$it\" moved as the counts came")
            }
        } finally {
            with.close()
            without.close()
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
                        settings = { tapped += "settings" },
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
            scene.tap(stringsOf(Language.DEFAULT).settingsScreen.title)
        } finally {
            scene.close()
        }
        assertEquals(listOf("quick", "create", "join", "public", "solo", "settings"), tapped)
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
    fun `a guest is offered Play Games under their name and the buttons stay where they were`() {
        Language.entries.forEach { language ->
            val words = stringsOf(language).game
            var taps = 0
            val guest = HomeState(profile = PROFILE, playGamesAvailable = true)
            val offered = scene(guest, language, actions = HomeActions(playGames = { taps++ }))
            val plain = scene(HomeState(profile = PROFILE), language)
            val linked = scene(guest.copy(profile = PROFILE.copy(playGamesLinked = true)), language)
            try {
                write("home-guest-${language.name.lowercase()}", offered.renderSettled())
                val offer = offered.nodes().single { words.signInPlayGames in it.texts }.boundsInRoot
                val name = offered.nodes().single { words.level.fill(7) in it.texts }.boundsInRoot
                assertTrue(offer.top >= name.bottom, "$language: the offer is not under the name")
                assertTrue(offer.right <= SHORT_PHONE_WIDTH, "$language: the offer runs off the screen")
                listOf(words.quickPlay, words.solo).forEach { text ->
                    fun top(scene: ImageComposeScene) =
                        scene
                            .nodes()
                            .single { text in it.texts }
                            .boundsInRoot.top
                    assertEquals(top(plain), top(offered), "$language: \"$text\" moved for the offer")
                }
                assertFalse(words.signInPlayGames in linked.everyText(), "$language: a linked player is offered it")
                offered.tap(words.signInPlayGames)
            } finally {
                offered.close()
                plain.close()
                linked.close()
            }
            assertEquals(1, taps, "$language")
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

    /** With `KVIZIC_DESIGN_DIR` set, a still of Home is written there as a PNG for the owner to see. */
    private fun write(
        name: String,
        image: Image,
    ) {
        val directory = System.getenv("KVIZIC_DESIGN_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        directory.mkdirs()
        File(directory, "$name.png").writeBytes(checkNotNull(image.encodeToData()).bytes)
    }

    private fun scene(
        state: HomeState,
        language: Language,
        actions: HomeActions = HomeActions(),
        counts: HomeCounts? = null,
        entry: Entry = Entry.None,
        exit: LobbyExit? = null,
        skin: Skin = Skins.Default,
        fontScale: Float = 1f,
    ): ImageComposeScene =
        ImageComposeScene(width = SHORT_PHONE_WIDTH, height = SHORT_PHONE_HEIGHT, density = Density(1f, fontScale)) {
            GameTheme(language, skin) { HomeScreen(state, actions, counts = counts, entry = entry, exit = exit) }
        }.also { it.renderSettled() }

    private companion object {
        /** A small phone, 360 by 640, less its status bar. */
        const val SHORT_PHONE_WIDTH = 360
        const val SHORT_PHONE_HEIGHT = 616

        /** A phone's large font, 130%, a common setting. */
        const val LARGE_FONT = 1.3f

        val PROFILE =
            Profile(
                playerId = "p1",
                displayName = "Брзи Јеж",
                nameSource = NameSource.GENERATED,
                avatarId = "hedgehog",
                playGamesLinked = false,
                stats = PlayerStats(gamesPlayed = 12, gamesWon = 3, answersGiven = 120, answersCorrect = 80),
                level = PlayerLevel(number = 7, xpIntoLevel = 40, xpForLevel = 130),
            )
    }
}
