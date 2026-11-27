package io.ntole.kvizic.core.data.mapper

import io.ntole.kvizic.core.domain.lobby.LobbyDifficulty
import io.ntole.kvizic.core.domain.player.NameSource
import io.ntole.kvizic.core.domain.player.PlayerLevel
import io.ntole.kvizic.core.domain.player.PlayerStats
import io.ntole.kvizic.core.domain.player.Profile
import io.ntole.kvizic.core.domain.player.ShopItem
import io.ntole.kvizic.core.domain.player.ShopItemKind
import io.ntole.kvizic.core.player.PlayerLevelDto
import io.ntole.kvizic.core.player.PlayerStatsDto
import io.ntole.kvizic.core.player.ProfileDto
import io.ntole.kvizic.core.player.ShopItemDto
import io.ntole.kvizic.core.player.NameSource as WireNameSource
import io.ntole.kvizic.core.player.ShopItemKind as WireShopItemKind

/** DTO to domain translation for a profile: the only place a `ProfileDto` and a `Profile` meet. */
internal fun ProfileDto.toDomain(): Profile =
    Profile(
        playerId = playerId,
        displayName = displayName,
        nameSource = nameSource.toDomain(),
        avatarId = avatarId,
        playGamesLinked = playGamesLinked,
        stats = stats.toDomain(),
        level = level.toDomain(),
        coins = coins,
        items = items,
        frameId = frameId,
        titleId = titleId,
        crownId = crownId,
    )

internal fun ShopItemDto.toDomain(): ShopItem =
    ShopItem(
        id = id,
        kind =
            when (kind) {
                WireShopItemKind.AVATAR -> ShopItemKind.AVATAR
                WireShopItemKind.FRAME -> ShopItemKind.FRAME
                WireShopItemKind.TITLE -> ShopItemKind.TITLE
                WireShopItemKind.CROWN -> ShopItemKind.CROWN
                WireShopItemKind.UNKNOWN -> ShopItemKind.OTHER
            },
        price = price,
        names = names,
    )

internal fun PlayerLevelDto.toDomain(): PlayerLevel =
    PlayerLevel(number = number, xpIntoLevel = xpIntoLevel, xpForLevel = xpForLevel)

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
        soloBests =
            listOfNotNull(
                soloBestVeryEasyScore?.let { LobbyDifficulty.VERY_EASY to it },
                soloBestEasyScore?.let { LobbyDifficulty.EASY to it },
                soloBestScore?.let { LobbyDifficulty.MEDIUM to it },
                soloBestHardScore?.let { LobbyDifficulty.HARD to it },
            ).toMap(),
    )
