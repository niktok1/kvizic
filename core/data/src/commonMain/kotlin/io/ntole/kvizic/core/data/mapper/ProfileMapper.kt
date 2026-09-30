package io.ntole.kvizic.core.data.mapper

import io.ntole.kvizic.core.domain.player.NameSource
import io.ntole.kvizic.core.domain.player.PlayerStats
import io.ntole.kvizic.core.domain.player.Profile
import io.ntole.kvizic.core.player.PlayerStatsDto
import io.ntole.kvizic.core.player.ProfileDto
import io.ntole.kvizic.core.player.NameSource as WireNameSource

/** DTO to domain translation for a profile: the only place a `ProfileDto` and a `Profile` meet. */
internal fun ProfileDto.toDomain(): Profile =
    Profile(
        playerId = playerId,
        displayName = displayName,
        nameSource = nameSource.toDomain(),
        avatarId = avatarId,
        playGamesLinked = playGamesLinked,
        stats = stats.toDomain(),
    )

internal fun WireNameSource.toDomain(): NameSource =
    when (this) {
        WireNameSource.GENERATED -> NameSource.GENERATED
        WireNameSource.PLAY_GAMES -> NameSource.PLAY_GAMES
        WireNameSource.UNKNOWN -> NameSource.OTHER
    }

internal fun PlayerStatsDto.toDomain(): PlayerStats =
    PlayerStats(
        gamesPlayed = gamesPlayed,
        gamesWon = gamesWon,
        answersGiven = answersGiven,
        answersCorrect = answersCorrect,
        bestTopicId = bestTopicId,
        soloRuns = soloRuns,
        soloBestScore = soloBestScore,
    )
