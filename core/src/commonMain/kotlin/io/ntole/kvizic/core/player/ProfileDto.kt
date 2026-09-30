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
    public val stats: PlayerStatsDto = PlayerStatsDto(),
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
    public val soloBestScore: Int? = null,
)
