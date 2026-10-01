package io.ntole.kvizic.core.protocol

import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.question.QuestionKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A lobby as its members see it. */
@Serializable
public data class LobbyView(
    public val id: String,
    public val code: String,
    public val kind: LobbyKind = LobbyKind.UNKNOWN,
    public val settings: LobbySettingsDto,
    public val host: String,
    public val members: List<MemberView>,
)

/**
 * A member of a lobby. [seat] tells two members with the same avatar apart by colour; [playing] is
 * whether they play the game under way, [onResults] whether they still look at the last one's results.
 * [kickVotes] is how many in the room vote them out now and [kickVotesNeeded] how many it takes, both 0
 * while nobody does; [kickVoted] whether the member this view is for is one of them.
 */
@Serializable
public data class MemberView(
    public val player: String,
    public val name: String,
    public val avatar: String,
    public val seat: Int = 0,
    public val connected: Boolean = true,
    public val onResults: Boolean = false,
    public val playing: Boolean = false,
    public val kickVotes: Int = 0,
    public val kickVotesNeeded: Int = 0,
    public val kickVoted: Boolean = false,
)

/** Where the lobby is, for a snapshot. */
@Serializable
public sealed interface PhaseView {
    /** Waiting for the host to start, with the last game's results for whoever still looks at them. */
    @Serializable
    @SerialName("waiting")
    public data class Waiting(
        public val lastResults: ResultsView? = null,
    ) : PhaseView

    /** A game about to start, with the last game's results for whoever still looks at them. */
    @Serializable
    @SerialName("countdown")
    public data class Countdown(
        public val remainingMs: Long,
        public val lastResults: ResultsView? = null,
    ) : PhaseView

    @Serializable
    @SerialName("reading")
    public data class Reading(
        public val gameId: String,
        public val players: List<String>,
        public val question: QuestionView,
        public val remainingMs: Long,
        public val standings: List<StandingView> = emptyList(),
    ) : PhaseView

    /** [picks] is filled only for a player who has answered: exactly what [ServerMessage.Picks] would say. */
    @Serializable
    @SerialName("answering")
    public data class Answering(
        public val gameId: String,
        public val players: List<String>,
        public val question: QuestionView,
        public val options: List<String>,
        public val remainingMs: Long,
        public val durationMs: Long,
        public val answered: List<String> = emptyList(),
        public val yourPick: Int? = null,
        public val picks: List<PickView> = emptyList(),
        public val standings: List<StandingView> = emptyList(),
    ) : PhaseView

    @Serializable
    @SerialName("revealing")
    public data class Revealing(
        public val gameId: String,
        public val players: List<String>,
        public val reveal: RevealView,
        public val remainingMs: Long,
    ) : PhaseView

    /** A phase this build does not know. */
    @Serializable
    @SerialName("unknown")
    public data object Unknown : PhaseView
}

/**
 * A question as asked: its [index] of [count], its text and topic, and how many answers it offers, so a
 * client lays the tiles out before the answers arrive. Never its id or its answer. [answerMs] is how long
 * its answers stay open, the game's own time, which a change of the room's settings during the game does
 * not touch; 0 from a server before 2026-10-01, which never changed them mid-game.
 */
@Serializable
public data class QuestionView(
    public val index: Int,
    public val count: Int,
    public val text: String,
    public val topic: String,
    public val optionCount: Int,
    public val kind: QuestionKind = QuestionKind.UNKNOWN,
    public val answerMs: Long = 0,
)

@Serializable
public data class PickView(
    public val player: String,
    public val option: Int,
)

/**
 * A question's reveal. [questionId] is what a report names. Each [results] entry is a player's pick,
 * or none, the points it made (negative for a wrong answer), their time, and [AnswerResultView.order]
 * among the right answers when they were one of the first.
 */
@Serializable
public data class RevealView(
    public val index: Int,
    public val count: Int,
    public val questionId: String,
    public val text: String,
    public val options: List<String>,
    public val correct: Int,
    public val results: List<AnswerResultView>,
    public val standings: List<StandingView>,
    public val explanation: String? = null,
    public val nextInMs: Long,
    public val last: Boolean = false,
)

@Serializable
public data class AnswerResultView(
    public val player: String,
    public val option: Int? = null,
    public val points: Int = 0,
    public val timeMs: Long? = null,
    public val order: Int? = null,
)

/** A player's standing so far: shared ranks for exact ties, 1, 2, 2, 4. */
@Serializable
public data class StandingView(
    public val player: String,
    public val score: Int,
    public val rank: Int,
    public val correct: Int = 0,
)

/**
 * A finished game. Each standing carries the name and avatar, since a player may have left by the time
 * someone looks at the results. [personalBest] only on a solo run.
 */
@Serializable
public data class ResultsView(
    public val gameId: String,
    public val questionCount: Int,
    public val standings: List<FinalStandingView>,
    public val endedEarly: Boolean = false,
    public val personalBest: PersonalBestView? = null,
)

@Serializable
public data class FinalStandingView(
    public val player: String,
    public val name: String,
    public val avatar: String,
    public val score: Int,
    public val correct: Int,
    public val rank: Int,
    /** False for a player who left before the end. */
    public val finished: Boolean = true,
)

@Serializable
public data class PersonalBestView(
    public val score: Int,
    public val previous: Int? = null,
    public val isNew: Boolean = false,
)
