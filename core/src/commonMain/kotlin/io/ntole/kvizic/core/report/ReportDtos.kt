package io.ntole.kvizic.core.report

import io.ntole.kvizic.core.question.AdminQuestionDto
import kotlinx.serialization.Serializable

/** Why a player reports a question. */
@Serializable
public enum class ReportReason {
    WRONG_ANSWER,
    TYPO,
    AMBIGUOUS,
    OFFENSIVE,
    OTHER,
    UNKNOWN,
}

/** A player reporting question [questionId], named by the reveal. One per player per revision of it. */
@Serializable
public data class ReportQuestionRequest(
    public val questionId: String,
    public val reason: ReportReason = ReportReason.UNKNOWN,
)

/** A reported question with its open reports counted by reason. */
@Serializable
public data class AdminReportDto(
    public val question: AdminQuestionDto,
    public val open: Int,
    public val reasons: List<ReasonCountDto> = emptyList(),
    public val lastReportedAt: Long,
)

@Serializable
public data class ReasonCountDto(
    public val reason: ReportReason = ReportReason.UNKNOWN,
    public val count: Int,
)

@Serializable
public data class AdminReportListDto(
    public val reports: List<AdminReportDto> = emptyList(),
)

/** How the moderator dealt with a question's open reports. */
@Serializable
public enum class ReportResolution {
    /** The question was edited. */
    FIXED,

    /** Nothing was wrong with it. */
    DISMISSED,

    /** It was taken out of play. */
    RETIRED,
    UNKNOWN,
}

@Serializable
public data class ResolveReportsRequest(
    public val questionId: String,
    public val resolution: ReportResolution = ReportResolution.UNKNOWN,
)
