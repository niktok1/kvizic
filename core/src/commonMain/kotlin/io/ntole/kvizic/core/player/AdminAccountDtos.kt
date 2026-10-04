package io.ntole.kvizic.core.player

import kotlinx.serialization.Serializable

/** How the moderator's list of accounts is ordered, most first. */
@Serializable
public enum class AccountSort {
    /** Last seen. */
    RECENT,

    /** Created. */
    CREATED,

    /** Experience, so level. */
    LEVEL,

    /** Games played in a room. */
    GAMES,
}

/**
 * A player as the moderator sees them: what the platform and the profile hold, the account's id included, which
 * is what a deletion by email names. Times are epoch milliseconds; [lastSeenAt] is null for a player not seen
 * since the server began noting it.
 */
@Serializable
public data class AdminAccountDto(
    public val playerId: String,
    public val displayName: String,
    public val nameSource: NameSource = NameSource.UNKNOWN,
    public val avatarId: String,
    public val playGamesLinked: Boolean = false,
    public val level: Int = 1,
    public val xp: Int = 0,
    public val stats: PlayerStatsDto = PlayerStatsDto(),
    public val createdAt: Long,
    public val lastSeenAt: Long? = null,
)

/** A page of accounts; [total] counts all that match, [nextCursor] is null on the last page. */
@Serializable
public data class AdminAccountPageDto(
    public val accounts: List<AdminAccountDto> = emptyList(),
    public val nextCursor: String? = null,
    public val total: Int = 0,
)

/** How one player does in one topic. */
@Serializable
public data class AdminTopicStatDto(
    public val topicId: String,
    public val answered: Int,
    public val correct: Int,
)

/** One finished game of a player's: [standing] 1 for first, [finished] false when they left before the end. */
@Serializable
public data class AdminGameDto(
    public val endedAt: Long,
    public val solo: Boolean = false,
    public val participants: Int = 1,
    public val standing: Int = 1,
    public val score: Int = 0,
    public val correct: Int = 0,
    public val answered: Int = 0,
    public val finished: Boolean = true,
)

/** An account and what it has played: topics, most answered first, and the last games, newest first. */
@Serializable
public data class AdminAccountDetailDto(
    public val account: AdminAccountDto,
    public val topics: List<AdminTopicStatDto> = emptyList(),
    public val recentGames: List<AdminGameDto> = emptyList(),
)
