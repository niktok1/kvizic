package io.ntole.kvizic.core.domain.player

/**
 * The player on this device as the server knows them: the name and avatar other players see, and their
 * stats. The client never works any of it out itself.
 *
 * Deliberately not the wire's `ProfileDto`: domain code never sees a DTO, and `:core:data` maps one into
 * the other.
 *
 * [displayName] is the Play Games name when the player signed in with Play Games ([nameSource]),
 * otherwise the nickname the server generated. [avatarId] names one of the game's avatars, which the
 * client draws; an id this build has no drawing for is still a player's avatar, shown as a silhouette.
 */
public data class Profile(
    public val playerId: String,
    public val displayName: String,
    public val nameSource: NameSource,
    public val avatarId: String,
    public val playGamesLinked: Boolean,
    public val stats: PlayerStats,
)

/** Where a player's shown name comes from. */
public enum class NameSource {
    /** An adjective and an animal the server picked when it minted the player. */
    GENERATED,

    /** The player's Play Games name, read from Google at each sign-in. */
    PLAY_GAMES,

    /** A source this build cannot name, which a newer server may send. */
    OTHER,
}

/** What a player's finished games add up to, as the server counts them. */
public data class PlayerStats(
    public val gamesPlayed: Int = 0,
    public val gamesWon: Int = 0,
    public val answersGiven: Int = 0,
    public val answersCorrect: Int = 0,
    /** The topic with the best correct rate among those with enough answers, or null before there is one. */
    public val bestTopicId: String? = null,
    public val soloRuns: Int = 0,
    public val soloBestScore: Int? = null,
)
