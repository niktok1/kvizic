package io.ntole.kvizic.core.domain.moderation

/**
 * The server's rules for a question, copied so the moderation app says what is wrong before it sends:
 * `:core:domain` cannot see the wire's `KvizicApi.Limits`, and `QuestionRulesLimitsTest` in `:core:data`
 * pins each number to it. The server checks again, and its refusal is the last word.
 */
public object QuestionRules {
    public const val MAX_TEXT_LENGTH: Int = 120
    public const val MAX_OPTION_LENGTH: Int = 60
    public const val MIN_OPTIONS: Int = 2
    public const val MAX_OPTIONS: Int = 4
    public const val MAX_TOPICS: Int = 3
    public const val MAX_EXPLANATION_LENGTH: Int = 300
    public const val MAX_SOURCE_LENGTH: Int = 500
    public const val MAX_REASON_LENGTH: Int = 200

    /** The first thing wrong with a question so made, in the moderator's words, or `null` when nothing is. */
    public fun problemOf(
        text: String,
        options: List<String>,
        correct: Int,
        topics: List<String>,
        difficulty: QuestionDifficulty,
        explanation: String?,
        source: String?,
    ): String? {
        line("The question", text, MAX_TEXT_LENGTH)?.let { return it }
        if (options.size !in MIN_OPTIONS..MAX_OPTIONS) return "A question has $MIN_OPTIONS to $MAX_OPTIONS answers."
        options.forEachIndexed { index, option ->
            line("Answer ${index + 1}", option, MAX_OPTION_LENGTH)?.let { return it }
        }
        if (options.map { it.trim().lowercase() }.toSet().size != options.size) return "Two answers are the same."
        if (correct !in options.indices) return "Pick the right answer."
        if (topics.size !in 1..MAX_TOPICS) return "A question has 1 to $MAX_TOPICS topics."
        if (difficulty == QuestionDifficulty.OTHER) return "Pick a difficulty."
        explanation?.takeIf { it.isNotBlank() }?.let { line("The explanation", it, MAX_EXPLANATION_LENGTH) }?.let {
            return it
        }
        source?.trim()?.takeIf { it.isNotEmpty() }?.let {
            if (it.length > MAX_SOURCE_LENGTH) return "The source is over $MAX_SOURCE_LENGTH characters."
            val web = it.startsWith("https://") || it.startsWith("http://")
            if (!web ||
                it.any { char -> char.isWhitespace() || char.isISOControl() }
            ) {
                return "The source is no web address."
            }
        }
        return null
    }

    /** What is wrong with a rejection's [reason], or `null` when it can be sent. */
    public fun reasonProblem(reason: String): String? = line("A reason", reason, MAX_REASON_LENGTH)

    private fun line(
        what: String,
        value: String,
        max: Int,
    ): String? {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() -> "$what is blank."
            trimmed.length > max -> "$what is over $max characters."
            !isOneLine(trimmed) -> "$what is not one line."
            else -> null
        }
    }

    private fun isOneLine(text: String): Boolean = text.none { it.isISOControl() || it == ' ' || it == ' ' }
}
