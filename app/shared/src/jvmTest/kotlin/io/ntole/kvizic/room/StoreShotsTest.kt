package io.ntole.kvizic.room

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.domain.lobby.LobbyVisibility
import io.ntole.kvizic.core.domain.player.NameSource
import io.ntole.kvizic.core.domain.player.PlayerLevel
import io.ntole.kvizic.core.domain.player.PlayerStats
import io.ntole.kvizic.core.domain.player.Profile
import io.ntole.kvizic.design.component.Stage
import io.ntole.kvizic.home.HomeActions
import io.ntole.kvizic.home.HomeCounts
import io.ntole.kvizic.home.HomeScreen
import io.ntole.kvizic.home.HomeState
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.renderSettled
import io.ntole.kvizic.theme.GameTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Google Play's phone screenshots, 1080 by 1920: Home, the lobby, a question with the picks on its tiles, the
 * reveal and the results, drawn by the game itself from the screens' tests' room. With `KVIZIC_DESIGN_DIR`
 * set, each is written there as `store-shot-<n>-<name>.png`, to upload in this order.
 */
class StoreShotsTest {
    @Test
    fun `the store's screenshots are drawn at a phone's full resolution`() {
        val named = lobby(settings = LobbySettings(name = "Петак у осам", visibility = LobbyVisibility.PUBLIC))
        val onResults = MEMBERS.map { if (it.playerId == YOU) it.copy(onResults = true) else it }
        val shots: List<Pair<String, @Composable () -> Unit>> =
            listOf(
                "home" to {
                    HomeScreen(
                        HomeState(profile = PROFILE),
                        HomeActions(),
                        counts = HomeCounts(online = 128, searching = 7, publicRooms = 12),
                    )
                },
                "lobby" to { room(inLobby(GamePhase.Waiting(null), lobby = named)) },
                "question" to {
                    room(
                        inLobby(
                            answering(
                                answered = setOf("sova", "bojan", YOU),
                                myPick = 0,
                                picks = mapOf("sova" to 0, "bojan" to 1),
                            ),
                        ),
                    )
                },
                "reveal" to {
                    val shown = revealing()
                    room(
                        inLobby(
                            shown.copy(
                                reveal = shown.reveal.copy(explanation = "Нови Сад лежи на левој обали Дунава."),
                            ),
                        ),
                    )
                },
                "results" to { room(inLobby(GamePhase.Waiting(RESULTS), lobby = lobby(members = onResults))) },
            )
        shots.forEachIndexed { index, (name, content) ->
            val scene =
                ImageComposeScene(width = WIDTH * SCALE, height = HEIGHT * SCALE, density = Density(SCALE.toFloat())) {
                    GameTheme(Language.DEFAULT) { Stage(Modifier.fillMaxSize()) { content() } }
                }
            try {
                val image = scene.renderSettled()
                assertEquals(WIDTH * SCALE to HEIGHT * SCALE, image.width to image.height, name)
                System.getenv("KVIZIC_DESIGN_DIR")?.takeIf { it.isNotBlank() }?.let(::File)?.let { directory ->
                    directory.mkdirs()
                    File(directory, "store-shot-${index + 1}-$name.png")
                        .writeBytes(checkNotNull(image.encodeToData()).bytes)
                }
            } finally {
                scene.close()
            }
        }
    }

    @Composable
    private fun room(state: io.ntole.kvizic.core.domain.lobby.LobbySessionState.InLobby) =
        RoomScreen(state, TOPICS, note = null, bursts = emptyMap(), actions = RoomActions())

    private companion object {
        /** A phone of 360 by 640 dp at 3x: Play's 9:16, 1080 by 1920. */
        const val WIDTH = 360
        const val HEIGHT = 640
        const val SCALE = 3

        val PROFILE =
            Profile(
                playerId = YOU,
                displayName = "Марко",
                nameSource = NameSource.PLAY_GAMES,
                avatarId = "hedgehog",
                playGamesLinked = true,
                stats = PlayerStats(gamesPlayed = 12, gamesWon = 3),
                level = PlayerLevel(number = 7, xpIntoLevel = 40, xpForLevel = 130),
            )
    }
}
