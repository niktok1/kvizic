package io.ntole.kvizic.core.domain.lobby

/**
 * What a room's settings may be, as the server holds them: copies of the wire's limits, which the domain
 * cannot see, so a screen offers only what the server takes. `LobbyRulesTest` pins each copy.
 */
public object LobbyRules {
    /** The numbers of questions a game may have. */
    public val QUESTION_COUNTS: List<Int> = listOf(5, 10, 15, 20)

    /** The seconds a question may give to answer. */
    public val ANSWER_SECONDS: List<Int> = listOf(10, 15, 20, 30)

    /** The fewest and most seats a room may hold. */
    public const val MIN_PLAYERS: Int = 2
    public const val MAX_PLAYERS: Int = 8
}
