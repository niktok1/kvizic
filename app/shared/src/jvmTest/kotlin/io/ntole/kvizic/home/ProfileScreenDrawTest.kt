package io.ntole.kvizic.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.core.domain.lobby.LobbyDifficulty
import io.ntole.kvizic.core.domain.player.NameSource
import io.ntole.kvizic.core.domain.player.PlayerStats
import io.ntole.kvizic.core.domain.player.Profile
import io.ntole.kvizic.descriptions
import io.ntole.kvizic.design.avatar.AvatarArt
import io.ntole.kvizic.design.component.Stage
import io.ntole.kvizic.everyText
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.renderSettled
import io.ntole.kvizic.room.TOPICS
import io.ntole.kvizic.tap
import io.ntole.kvizic.theme.GameTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The profile drawn off screen in each language: the player's stats, and every avatar to pick by its animal's name. */
class ProfileScreenDrawTest {
    @Test
    fun `the profile shows the player's stats and names every avatar`() {
        Language.entries.forEach { language ->
            val words = stringsOf(language).game
            assertEquals(AvatarArt.DRAWN.size, words.avatarNames.size, "$language: an animal's name for each avatar")
            val scene = scene(language)
            try {
                val shown = scene.everyText()
                listOf(
                    words.games.of(12, language),
                    words.wins.of(3, language),
                    words.correctShare.fill(66),
                    words.soloBest.fill("${words.medium} 940 · ${words.hard} 410"),
                    words.bestTopic.fill(if (language == Language.ENGLISH) "Geography" else stringsTopic(language)),
                    words.pickAvatar,
                ).forEach { assertTrue(it in shown, "$language: \"$it\" is not in $shown") }
                words.avatarNames.forEach { assertTrue(it in scene.descriptions(), "$language: $it") }
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun `an avatar tapped is asked for by its id`() {
        val words = stringsOf(Language.DEFAULT).game
        val picked = mutableListOf<String>()
        val scene = scene(Language.DEFAULT, onPick = { picked += it })
        try {
            scene.tap(words.avatarNames[AvatarArt.DRAWN.indexOf(AvatarArt.FROG)])
        } finally {
            scene.close()
        }
        assertEquals(listOf(AvatarArt.FROG), picked)
    }

    private fun stringsTopic(language: Language): String =
        if (language ==
            Language.SERBIAN_LATIN
        ) {
            "Geografija"
        } else {
            "Географија"
        }

    private fun scene(
        language: Language,
        onPick: (String) -> Unit = {},
    ): ImageComposeScene =
        ImageComposeScene(width = 360, height = 1200, density = Density(1f)) {
            GameTheme(
                language,
            ) { Stage(Modifier.fillMaxSize()) { ProfileScreen(HomeState(profile = PROFILE), TOPICS, onPick) } }
        }.also { it.renderSettled() }

    private companion object {
        val PROFILE =
            Profile(
                playerId = "p1",
                displayName = "Брзи Јеж",
                nameSource = NameSource.GENERATED,
                avatarId = "hedgehog",
                playGamesLinked = false,
                stats =
                    PlayerStats(
                        gamesPlayed = 12,
                        gamesWon = 3,
                        answersGiven = 120,
                        answersCorrect = 80,
                        bestTopicId = "GEOGRAPHY",
                        soloRuns = 4,
                        soloBests = mapOf(LobbyDifficulty.HARD to 410, LobbyDifficulty.MEDIUM to 940),
                    ),
            )
    }
}
