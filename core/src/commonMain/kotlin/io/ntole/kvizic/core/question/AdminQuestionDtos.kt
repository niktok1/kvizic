package io.ntole.kvizic.core.question

import io.ntole.kvizic.core.api.KvizicApi
import kotlinx.serialization.Serializable

/**
 * One draft as the private content repo writes it and the moderation app imports it. [key] makes an
 * import idempotent: a key the bank has already is a duplicate, not a second question. [options] holds
 * 2 to [KvizicApi.Limits.MAX_OPTIONS] answers and [correct] is the right one's position. [source] is
 * where the answer was checked; it is kept for the moderator and never sent to players.
 */
@Serializable
public data class QuestionDraftDto(
    public val key: String,
    public val text: String,
    public val options: List<String>,
    public val correct: Int,
    public val topics: List<String>,
    public val difficulty: Difficulty = Difficulty.MEDIUM,
    public val kind: QuestionKind = QuestionKind.CHOICE,
    public val explanation: String? = null,
    public val source: String? = null,
    public val language: String = "sr-Cyrl",
    public val author: String = "",
)

/** Up to [KvizicApi.Limits.MAX_IMPORT_BATCH] drafts, all of one [batch]. */
@Serializable
public data class ImportQuestionsRequest(
    public val batch: String,
    public val questions: List<QuestionDraftDto>,
)

/** What an import did with each draft, by key. */
@Serializable
public data class ImportResultDto(
    public val created: List<String> = emptyList(),
    public val duplicates: List<String> = emptyList(),
    public val refused: List<ImportRefusalDto> = emptyList(),
)

@Serializable
public data class ImportRefusalDto(
    public val key: String,
    public val reason: String,
)

/** A question as the moderator sees it: everything, the answer and the source included. */
@Serializable
public data class AdminQuestionDto(
    public val id: String,
    public val status: QuestionStatus = QuestionStatus.UNKNOWN,
    public val kind: QuestionKind = QuestionKind.UNKNOWN,
    public val text: String,
    public val options: List<String>,
    public val correct: Int,
    public val topics: List<String>,
    public val difficulty: Difficulty = Difficulty.UNKNOWN,
    public val explanation: String? = null,
    public val source: String? = null,
    public val language: String = "sr-Cyrl",
    public val author: String = "",
    public val importBatch: String? = null,
    public val importKey: String? = null,
    public val revision: Int,
    public val createdAt: Long,
    public val updatedAt: Long,
    public val reviewedAt: Long? = null,
    public val rejectionReason: String? = null,
    public val stats: QuestionStatsDto = QuestionStatsDto(),
    public val openReports: Int = 0,
)

/**
 * How a question has played: how often asked, answered and answered right, and how fast when right;
 * how many it waited for gave no answer, and the level all that makes it play at, its author's until
 * enough players have been asked it.
 */
@Serializable
public data class QuestionStatsDto(
    public val shown: Int = 0,
    public val answered: Int = 0,
    public val correct: Int = 0,
    public val averageCorrectMs: Long? = null,
    public val unanswered: Int = 0,
    public val playsAs: Difficulty = Difficulty.UNKNOWN,
)

@Serializable
public data class AdminQuestionPageDto(
    public val questions: List<AdminQuestionDto> = emptyList(),
    public val nextCursor: String? = null,
)

/**
 * The moderator's edit, made from [revision]: refused as `STALE_REVISION` when another edit got there
 * first. Changing the answers or which is right resets the question's stats.
 */
@Serializable
public data class EditQuestionRequest(
    public val id: String,
    public val revision: Int,
    public val text: String,
    public val options: List<String>,
    public val correct: Int,
    public val topics: List<String>,
    public val difficulty: Difficulty = Difficulty.MEDIUM,
    public val explanation: String? = null,
    public val source: String? = null,
)

/** Approves, rejects (with a [reason]), retires or restores question [id]. */
@Serializable
public data class QuestionDecisionRequest(
    public val id: String,
    public val reason: String? = null,
)

/** The bank and the server at a glance. */
@Serializable
public data class AdminOverviewDto(
    public val byStatus: List<StatusCountDto> = emptyList(),
    public val approvedByTopic: List<TopicCountDto> = emptyList(),
    public val liveLobbies: Int = 0,
    public val liveGames: Int = 0,
    public val connectedPlayers: Int = 0,
    public val gamesToday: Int = 0,
)

@Serializable
public data class StatusCountDto(
    public val status: QuestionStatus = QuestionStatus.UNKNOWN,
    public val count: Int,
)

@Serializable
public data class TopicCountDto(
    public val topic: String,
    public val count: Int,
)
