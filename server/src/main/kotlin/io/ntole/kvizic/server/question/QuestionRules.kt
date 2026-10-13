package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.QuestionKind
import io.ntole.kvizic.core.question.QuestionTextDto
import io.ntole.kvizic.core.region.ContentLanguages
import io.ntole.kvizic.core.region.Country
import io.ntole.kvizic.server.topic.isOneLine

/**
 * A question the rules take, every text trimmed and every topic once; its [countries] in [Country]'s order,
 * none for a common question, and its [translations] by content tag, in `ContentLanguages.ALL`'s order.
 */
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
    val countries: Set<Country> = emptySet(),
    val translations: Map<String, CheckedText> = emptyMap(),
)

/** A translation the rules take: a question's words in a language other than its source's, trimmed. */
data class CheckedText(
    val text: String,
    val options: List<String>,
    val explanation: String?,
)

/** Why the rules refuse a question, in the moderator's words. */
class QuestionRefused(
    override val message: String,
) : RuntimeException(message)

private val IMPORT_KEY = Regex("^[\\x21-\\x7E]{1,${KvizicApi.Limits.MAX_IMPORT_KEY_LENGTH}}$")
private const val MAX_AUTHOR_LENGTH = 64

/** How long a language tag is shown back in a refusal: as long as any language column holds. */
private const val MAX_SHOWN_TAG = 16

/**
 * The question these fields make, or [QuestionRefused] naming the first rule it breaks: its text and
 * each answer one line, within their lengths; 2 to [KvizicApi.Limits.MAX_OPTIONS] answers, no two the
 * same whatever the case, one of them right; 1 to [KvizicApi.Limits.MAX_TOPICS_PER_QUESTION] topics the
 * bank has; a true or false question with exactly two answers; a source, if any, a web address; and a
 * [language] of `ContentLanguages.ALL`. Its [countries] are checked as [checkedCountries] checks them, and
 * its [translations] as [checkedTranslations] does, against the source these fields make.
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
    countries: List<Country> = emptyList(),
    translations: Map<String, QuestionTextDto> = emptyMap(),
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
    if (checkedLanguage !in ContentLanguages.ALL) refuse("no language ${checkedLanguage.take(MAX_SHOWN_TAG)}")
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
        countries = checkedCountries(countries),
        translations =
            checkedTranslations(translations, checkedLanguage, checkedOptions.size, checkedExplanation != null),
    )
}

/**
 * The countries a question is asked in alone, in [Country]'s order, none for a common one; or
 * [QuestionRefused]: no country twice, and never [Country.UNKNOWN], which nothing can be scoped to.
 */
internal fun checkedCountries(countries: List<Country>): Set<Country> {
    if (Country.UNKNOWN in countries) refuse("countries: ${Country.UNKNOWN} is no country")
    countries
        .groupingBy { it }
        .eachCount()
        .entries
        .firstOrNull { it.value > 1 }
        ?.let { refuse("countries: ${it.key} twice") }
    return Country.entries.filter { it in countries }.toSet()
}

/**
 * A question's [translations], checked against its source: in [sourceLanguage], with [optionCount] answers,
 * and with an explanation or not ([explained]). Or [QuestionRefused], its reason naming the language: each
 * in a content language other than the source's; its text, answers and explanation as the source's are
 * checked (one line each, within the same lengths, no two answers the same whatever the case); exactly as
 * many answers, which stand for the source's slots in their order, so the right one is right in every
 * language; and an explanation where the source has one and none where it has none, so a reveal shows and
 * lasts the same for every reader.
 */
internal fun checkedTranslations(
    translations: Map<String, QuestionTextDto>,
    sourceLanguage: String,
    optionCount: Int,
    explained: Boolean,
): Map<String, CheckedText> {
    translations.keys.firstOrNull { it !in ContentLanguages.ALL }?.let {
        refuse("translation ${it.take(MAX_SHOWN_TAG)}: no such language")
    }
    if (sourceLanguage in translations) refuse("translation $sourceLanguage: the source's own language")
    return ContentLanguages.ALL
        .mapNotNull { tag -> translations[tag]?.let { tag to checkedText(tag, it, optionCount, explained) } }
        .toMap()
}

private fun checkedText(
    tag: String,
    words: QuestionTextDto,
    optionCount: Int,
    explained: Boolean,
): CheckedText {
    val what = "translation $tag:"
    val text = line("$what the question", words.text, KvizicApi.Limits.MAX_QUESTION_TEXT_LENGTH)
    if (words.options.size != optionCount) {
        refuse("$what ${words.options.size} answers, not the source's $optionCount")
    }
    val options =
        words.options.mapIndexed { index, option ->
            line("$what answer ${index + 1}", option, KvizicApi.Limits.MAX_OPTION_LENGTH)
        }
    if (options.map { it.lowercase() }.toSet().size != options.size) refuse("$what two answers are the same")
    val explanation =
        words.explanation
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { line("$what the explanation", it, KvizicApi.Limits.MAX_EXPLANATION_LENGTH) }
    if (explained && explanation == null) refuse("$what no explanation, where the source has one")
    if (!explained && explanation != null) refuse("$what an explanation, where the source has none")
    return CheckedText(text, options, explanation)
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
