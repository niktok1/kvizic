package io.ntole.kvizic.core.player

import kotlinx.serialization.Serializable

/**
 * The bearer's own profile: the name and avatar other players see, and their stats. [displayName] is
 * the Play Games name when they signed in with Play Games ([nameSource]), otherwise the nickname the
 * server generated for them.
 */
@Serializable
public data class ProfileDto(
    public val playerId: String,
    public val displayName: String,
    public val nameSource: NameSource = NameSource.UNKNOWN,
    public val avatarId: String,
    public val playGamesLinked: Boolean = false,
    public val level: PlayerLevelDto = PlayerLevelDto(),
    public val stats: PlayerStatsDto = PlayerStatsDto(),
)

/**
 * Where a player stands in the levels, as the server works it out: [number] is their level, [xpIntoLevel] the
 * experience they have earned in it and [xpForLevel] what it takes to finish it, so the next level is one
 * `xpIntoLevel / xpForLevel` of the way. Every level is for experience from finished games in a room.
 */
@Serializable
public data class PlayerLevelDto(
    public val number: Int = 1,
    public val xpIntoLevel: Int = 0,
    public val xpForLevel: Int = 10,
)

/** Where a player's shown name comes from. */
@Serializable
public enum class NameSource {
    /** An adjective and an animal the server picked when it minted the player. */
    GENERATED,

    /** The player's Play Games name, read from Google at each sign-in. */
    PLAY_GAMES,

    UNKNOWN,
}

/** What a player's finished games add up to. Every field has a default, so one added later decodes on old clients. */
@Serializable
public data class PlayerStatsDto(
    public val gamesPlayed: Int = 0,
    public val gamesWon: Int = 0,
    public val answersGiven: Int = 0,
    public val answersCorrect: Int = 0,
    /** The topic with the best correct rate among those with enough answers, or null before there is one. */
    public val bestTopicId: String? = null,
    public val soloRuns: Int = 0,
    /** The best solo run at medium: every run was medium once, and a build before levels shows it alone. */
    public val soloBestScore: Int? = null,
    public val soloBestEasyScore: Int? = null,
    public val soloBestHardScore: Int? = null,
)
