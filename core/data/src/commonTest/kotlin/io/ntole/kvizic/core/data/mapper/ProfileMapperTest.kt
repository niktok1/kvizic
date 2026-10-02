package io.ntole.kvizic.core.data.mapper

import io.ntole.kvizic.core.domain.lobby.LobbyDifficulty
import io.ntole.kvizic.core.domain.player.NameSource
import io.ntole.kvizic.core.domain.player.PlayerLevel
import io.ntole.kvizic.core.domain.player.PlayerStats
import io.ntole.kvizic.core.domain.player.Profile
import io.ntole.kvizic.core.player.PlayerLevelDto
import io.ntole.kvizic.core.player.PlayerStatsDto
import io.ntole.kvizic.core.player.ProfileDto
import kotlin.test.Test
import kotlin.test.assertEquals
import io.ntole.kvizic.core.player.NameSource as WireNameSource

class ProfileMapperTest {
    @Test
    fun `a profile is mapped whole`() {
        val dto =
            ProfileDto(
                playerId = "p1",
                displayName = "Ана",
                nameSource = WireNameSource.PLAY_GAMES,
                avatarId = "owl",
                playGamesLinked = true,
                level = PlayerLevelDto(number = 7, xpIntoLevel = 30, xpForLevel = 130),
                stats =
                    PlayerStatsDto(
                        gamesPlayed = 10,
                        gamesWon = 3,
                        answersGiven = 100,
                        answersCorrect = 64,
                        bestTopicId = "MUSIC",
                        soloRuns = 2,
                        soloBestScore = 740,
                        soloBestHardScore = 410,
                    ),
            )

        assertEquals(
            Profile(
                playerId = "p1",
                displayName = "Ана",
                nameSource = NameSource.PLAY_GAMES,
                avatarId = "owl",
                playGamesLinked = true,
                level = PlayerLevel(number = 7, xpIntoLevel = 30, xpForLevel = 130),
                stats =
                    PlayerStats(
                        gamesPlayed = 10,
                        gamesWon = 3,
                        answersGiven = 100,
                        answersCorrect = 64,
                        bestTopicId = "MUSIC",
                        soloRuns = 2,
                        soloBests = mapOf(LobbyDifficulty.MEDIUM to 740, LobbyDifficulty.HARD to 410),
                    ),
            ),
            dto.toDomain(),
        )
    }

    @Test
    fun `every name source on the wire has one in the domain`() {
        assertEquals(
            mapOf(
                WireNameSource.GENERATED to NameSource.GENERATED,
                WireNameSource.PLAY_GAMES to NameSource.PLAY_GAMES,
                WireNameSource.UNKNOWN to NameSource.OTHER,
            ),
            WireNameSource.entries.associateWith { it.toDomain() },
        )
    }
}
