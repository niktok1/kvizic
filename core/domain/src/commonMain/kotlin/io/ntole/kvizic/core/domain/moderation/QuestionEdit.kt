package io.ntole.kvizic.core.domain.moderation

/**
 * The moderator's edit of question [id], made from its [revision]: refused as
 * [io.ntole.kvizic.core.domain.error.GameError.STALE_REVISION] when another edit got there first. Changing
 * the answers or which is right starts the question's play counts afresh.
 */
public data class QuestionEdit(
    public val id: String,
    public val revision: Int,
    public val text: String,
    public val options: List<String>,
    public val correct: Int,
    public val topics: List<String>,
    public val difficulty: QuestionDifficulty,
    public val explanation: String?,
    public val source: String?,
) {
    /** What [QuestionRules] find wrong with it, the first thing only; `null` when the server would take it. */
    public val problem: String?
        get() = QuestionRules.problemOf(text, options, correct, topics, difficulty, explanation, source)

    public companion object {
        /** [question] as it stands, to change. */
        public fun of(question: ModeratedQuestion): QuestionEdit =
            QuestionEdit(
                id = question.id,
                revision = question.revision,
                text = question.text,
                options = question.options,
                correct = question.correct,
                topics = question.topics,
                difficulty = question.difficulty,
                explanation = question.explanation,
                source = question.source,
            )
    }
}
