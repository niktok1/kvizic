package io.ntole.kvizic.core.domain.moderation

/** How the moderator's list of accounts is ordered, the most first. */
public enum class AccountOrder {
    LAST_SEEN,
    CREATED,
    LEVEL,
    GAMES,
}

/**
 * A player as the moderator reads them. [lastSeenAt] is null for one not seen since the server began noting
 * it; times are epoch milliseconds. [id] is the account id a deletion by email names.
 */
public data class AccountSummary(
    public val id: String,
    public val name: String,
    public val avatarId: String,
    public val playGamesLinked: Boolean,
    public val level: Int,
    public val xp: Int,
    public val gamesPlayed: Int,
    public val gamesWon: Int,
    public val answersGiven: Int,
    public val answersCorrect: Int,
    public val soloRuns: Int,
    public val soloBest: SoloBest,
    public val createdAt: Long,
    public val lastSeenAt: Long?,
) {
    /** When they were last about: the last seen, or the creation for one never seen. */
    public val lastActiveAt: Long get() = lastSeenAt ?: createdAt

    /** Right answers of those given, in whole percent, or null before they have answered. */
    public val accuracyPercent: Int?
        get() = if (answersGiven == 0) null else answersCorrect * PERCENT / answersGiven

    private companion object {
        const val PERCENT = 100
    }
}

/** The best solo score at each level, null for a level not played. */
public data class SoloBest(
    public val easy: Int? = null,
    public val medium: Int? = null,
    public val hard: Int? = null,
)

/** A page of accounts at an order and search; [total] counts all that match and [next] is null on the last page. */
public data class AccountPage(
    public val accounts: List<AccountSummary>,
    public val next: String?,
    public val total: Int,
)

/** How a player does in one topic, [answered] and [correct] answers. */
public data class AccountTopicStat(
    public val topicId: String,
    public val answered: Int,
    public val correct: Int,
)

/** One game of a player's: [standing] 1 for first, and [finished] false when they left before its end. */
public data class AccountGame(
    public val endedAt: Long,
    public val solo: Boolean,
    public val participants: Int,
    public val standing: Int,
    public val score: Int,
    public val correct: Int,
    public val answered: Int,
    public val finished: Boolean,
)

/** An account with its topics, most answered first, and its last games, newest first. */
public data class AccountDetail(
    public val account: AccountSummary,
    public val topics: List<AccountTopicStat>,
    public val recentGames: List<AccountGame>,
)
