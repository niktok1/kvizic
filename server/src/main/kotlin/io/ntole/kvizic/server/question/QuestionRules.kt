package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.QuestionKind
import io.ntole.kvizic.server.topic.isOneLine

/** A question the rules take, every text trimmed and every topic once. */
data class CheckedQuestion(
    val text: String,
    val options: List<String>,
    val correct: Int,
    val topics: List<String>,
    val difficulty: Difficulty,
    val kind: QuestionKind,
    val explanation: String?,
    val source: String?,
    val language: String,
    val author: String,
)

/** Why the rules refuse a question, in the moderator's words. */
class QuestionRefused(
    override val message: String,
) : RuntimeException(message)

private val LANGUAGE_TAG = Regex("^[a-z]{2,3}(-[A-Za-z0-9]{2,8}){0,2}$")
private val IMPORT_KEY = Regex("^[\\x21-\\x7E]{1,${KvizicApi.Limits.MAX_IMPORT_KEY_LENGTH}}$")
private const val MAX_AUTHOR_LENGTH = 64

/**
 * The question these fields make, or [QuestionRefused] naming the first rule it breaks: its text and
 * each answer one line, within their lengths; 2 to [KvizicApi.Limits.MAX_OPTIONS] answers, no two the
 * same whatever the case, one of them right; 1 to [KvizicApi.Limits.MAX_TOPICS_PER_QUESTION] topics the
 * bank has; a true or false question with exactly two answers; and a source, if any, a web address.
 */
internal fun checkedQuestion(
    text: String,
    options: List<String>,
    correct: Int,
    topics: List<String>,
    difficulty: Difficulty,
    kind: QuestionKind,
    explanation: String?,
    source: String?,
    language: String,
    author: String,
    knownTopics: Set<String>,
): CheckedQuestion {
    val checkedText = line("the question", text, KvizicApi.Limits.MAX_QUESTION_TEXT_LENGTH)
    if (options.size !in KvizicApi.Limits.MIN_OPTIONS..KvizicApi.Limits.MAX_OPTIONS) {
        refuse(
            "a question has ${KvizicApi.Limits.MIN_OPTIONS} to ${KvizicApi.Limits.MAX_OPTIONS} answers, not ${options.size}",
        )
    }
    val checkedOptions =
        options.mapIndexed {
            index,
            option,
            ->
            line("answer ${index + 1}", option, KvizicApi.Limits.MAX_OPTION_LENGTH)
        }
    if (checkedOptions.map { it.lowercase() }.toSet().size != checkedOptions.size) refuse("two answers are the same")
    if (correct !in checkedOptions.indices) refuse("the right answer is not one of the answers")
    val checkedTopics = topics.map { it.trim() }.distinct()
    if (checkedTopics.size !in 1..KvizicApi.Limits.MAX_TOPICS_PER_QUESTION) {
        refuse("a question has 1 to ${KvizicApi.Limits.MAX_TOPICS_PER_QUESTION} topics")
    }
    checkedTopics.firstOrNull { it !in knownTopics }?.let { refuse("no topic $it") }
    if (difficulty == Difficulty.UNKNOWN) refuse("no difficulty")
    when (kind) {
        QuestionKind.CHOICE -> Unit
        QuestionKind.TRUE_FALSE -> if (checkedOptions.size != 2) refuse("a true or false question has two answers")
        QuestionKind.UNKNOWN -> refuse("no kind")
    }
    val checkedExplanation =
        explanation
            ?.trim()
            ?.takeIf {
                it.isNotEmpty()
            }?.let { line("the explanation", it, KvizicApi.Limits.MAX_EXPLANATION_LENGTH) }
    val checkedSource =
        source?.trim()?.takeIf { it.isNotEmpty() }?.also {
            if (it.length >
                KvizicApi.Limits.MAX_SOURCE_URL_LENGTH
            ) {
                refuse("the source is over ${KvizicApi.Limits.MAX_SOURCE_URL_LENGTH} characters")
            }
            if (!it.startsWith("https://") && !it.startsWith("http://")) refuse("the source is no web address")
            if (it.any { char -> char.isWhitespace() || char.isISOControl() }) refuse("the source is no web address")
        }
    val checkedLanguage = language.trim()
    if (!LANGUAGE_TAG.matches(checkedLanguage)) refuse("no language $checkedLanguage")
    val checkedAuthor = author.trim()
    if (checkedAuthor.length > MAX_AUTHOR_LENGTH || !isOneLine(checkedAuthor)) refuse("the author is no short name")
    return CheckedQuestion(
        text = checkedText,
        options = checkedOptions,
        correct = correct,
        topics = checkedTopics,
        difficulty = difficulty,
        kind = kind,
        explanation = checkedExplanation,
        source = checkedSource,
        language = checkedLanguage,
        author = checkedAuthor,
    )
}

/** Whether [key] can be an import key: 1 to [KvizicApi.Limits.MAX_IMPORT_KEY_LENGTH] visible ASCII. */
internal fun isImportKey(key: String): Boolean = IMPORT_KEY.matches(key)

private fun line(
    what: String,
    raw: String,
    max: Int,
): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) refuse("$what is blank")
    if (trimmed.length > max) refuse("$what is over $max characters")
    if (!isOneLine(trimmed)) refuse("$what is not one line")
    return trimmed
}

private fun refuse(reason: String): Nothing = throw QuestionRefused(reason)
