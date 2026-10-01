package io.ntole.kvizic.core.domain.lobby

/** How a question asks: pick one of its answers, or true or false. */
public enum class QuestionKind { CHOICE, TRUE_FALSE, UNKNOWN }

/** A question as it is asked: its text, and how many answers are to come. */
public data class AskedQuestion(
    val index: Int,
    val count: Int,
    val text: String,
    val topic: String,
    val optionCount: Int,
    val kind: QuestionKind,
)

/** Where a player stands in the game under way. */
public data class Standing(
    val playerId: String,
    val score: Int,
    val rank: Int,
    val correct: Int,
)

/** One player's answer to a revealed question, and what it scored. */
public data class AnswerResult(
    val playerId: String,
    val option: Int?,
    val points: Int,
    val timeMs: Long?,
    /** Among the right answers, 1 for the fastest; only the first few are named. */
    val order: Int?,
)

/** A question once its time is up: the right answer, everyone's, and the standings after it. */
public data class Reveal(
    val index: Int,
    val count: Int,
    val questionId: String,
    val text: String,
    val options: List<String>,
    val correct: Int,
    val results: List<AnswerResult>,
    val standings: List<Standing>,
    val explanation: String?,
    /** The last question of the game: what comes after the reveal is the results. */
    val last: Boolean,
)

/** One player's end of a game. */
public data class FinalStanding(
    val playerId: String,
    val name: String,
    val avatar: String,
    val score: Int,
    val correct: Int,
    val rank: Int,
    /** False for one who left before the end. */
    val finished: Boolean,
)

/** A solo run's score against the player's best before it. */
public data class PersonalBest(
    val score: Int,
    val previous: Int?,
    val isNew: Boolean,
)

/** How a game ended. */
public data class GameResults(
    val gameId: String,
    val questionCount: Int,
    val standings: List<FinalStanding>,
    val endedEarly: Boolean,
    val personalBest: PersonalBest?,
)

/** What a lobby is doing, as its members see it. */
public sealed interface GamePhase {
    /** Between games: [lastResults] is the one just played, while members look at it. */
    public data class Waiting(
        val lastResults: GameResults?,
    ) : GamePhase

    /** The host started a game, which begins at [deadline]. */
    public data class Countdown(
        val deadline: Deadline,
        val lastResults: GameResults?,
    ) : GamePhase

    /** The question shows alone; its answers come at [deadline]. */
    public data class Reading(
        val gameId: String,
        val players: List<String>,
        val question: AskedQuestion,
        val deadline: Deadline,
        val standings: List<Standing>,
    ) : GamePhase

    /**
     * The answers are open until [deadline]. [myPick] is this player's locked answer, [picks] what the
     * others picked, seen only once this player has answered, and [answered] who has, which everyone sees.
     */
    public data class Answering(
        val gameId: String,
        val players: List<String>,
        val question: AskedQuestion,
        val options: List<String>,
        val deadline: Deadline,
        val answered: Set<String>,
        val myPick: Int?,
        val picks: Map<String, Int>,
        val standings: List<Standing>,
    ) : GamePhase

    /** The right answer and the points, until the next question, or the results, at [next]. */
    public data class Revealing(
        val gameId: String,
        val players: List<String>,
        val reveal: Reveal,
        val next: Deadline,
    ) : GamePhase
}
