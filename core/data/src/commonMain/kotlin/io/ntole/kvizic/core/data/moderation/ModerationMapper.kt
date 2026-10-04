package io.ntole.kvizic.core.data.moderation

import io.ntole.kvizic.core.domain.moderation.AccountDetail
import io.ntole.kvizic.core.domain.moderation.AccountGame
import io.ntole.kvizic.core.domain.moderation.AccountOrder
import io.ntole.kvizic.core.domain.moderation.AccountPage
import io.ntole.kvizic.core.domain.moderation.AccountSummary
import io.ntole.kvizic.core.domain.moderation.AccountTopicStat
import io.ntole.kvizic.core.domain.moderation.BankOverview
import io.ntole.kvizic.core.domain.moderation.BankStatus
import io.ntole.kvizic.core.domain.moderation.ModeratedQuestion
import io.ntole.kvizic.core.domain.moderation.QuestionDifficulty
import io.ntole.kvizic.core.domain.moderation.QuestionEdit
import io.ntole.kvizic.core.domain.moderation.QuestionPlay
import io.ntole.kvizic.core.domain.moderation.ReportOutcome
import io.ntole.kvizic.core.domain.moderation.ReportedQuestion
import io.ntole.kvizic.core.domain.moderation.SoloBest
import io.ntole.kvizic.core.domain.report.QuestionReportReason
import io.ntole.kvizic.core.player.AccountSort
import io.ntole.kvizic.core.player.AdminAccountDetailDto
import io.ntole.kvizic.core.player.AdminAccountDto
import io.ntole.kvizic.core.player.AdminAccountPageDto
import io.ntole.kvizic.core.question.AdminOverviewDto
import io.ntole.kvizic.core.question.AdminQuestionDto
import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.EditQuestionRequest
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.core.report.AdminReportDto
import io.ntole.kvizic.core.report.ReportReason
import io.ntole.kvizic.core.report.ReportResolution

internal fun AdminQuestionDto.toDomain(): ModeratedQuestion =
    ModeratedQuestion(
        id = id,
        status = status.toDomain(),
        text = text,
        options = options,
        correct = correct,
        topics = topics,
        difficulty = difficulty.toDomain(),
        explanation = explanation,
        source = source,
        author = author,
        importBatch = importBatch,
        revision = revision,
        createdAt = createdAt,
        updatedAt = updatedAt,
        reviewedAt = reviewedAt,
        rejectionReason = rejectionReason,
        play =
            QuestionPlay(
                shown = stats.shown,
                answered = stats.answered,
                correct = stats.correct,
                averageCorrectMillis = stats.averageCorrectMs,
                unanswered = stats.unanswered,
                playsAs = stats.playsAs.toDomain(),
            ),
        openReports = openReports,
    )

internal fun QuestionStatus.toDomain(): BankStatus =
    when (this) {
        QuestionStatus.DRAFT -> BankStatus.DRAFT
        QuestionStatus.APPROVED -> BankStatus.APPROVED
        QuestionStatus.REJECTED -> BankStatus.REJECTED
        QuestionStatus.RETIRED -> BankStatus.RETIRED
        QuestionStatus.SUSPENDED -> BankStatus.SUSPENDED
        QuestionStatus.UNKNOWN -> BankStatus.OTHER
    }

/** The wire's status, or `null` for [BankStatus.OTHER], which nothing filters by. */
internal fun BankStatus.toWire(): QuestionStatus? =
    when (this) {
        BankStatus.DRAFT -> QuestionStatus.DRAFT
        BankStatus.APPROVED -> QuestionStatus.APPROVED
        BankStatus.REJECTED -> QuestionStatus.REJECTED
        BankStatus.RETIRED -> QuestionStatus.RETIRED
        BankStatus.SUSPENDED -> QuestionStatus.SUSPENDED
        BankStatus.OTHER -> null
    }

internal fun Difficulty.toDomain(): QuestionDifficulty =
    when (this) {
        Difficulty.EASY -> QuestionDifficulty.EASY
        Difficulty.MEDIUM -> QuestionDifficulty.MEDIUM
        Difficulty.HARD -> QuestionDifficulty.HARD
        Difficulty.UNKNOWN -> QuestionDifficulty.OTHER
    }

internal fun QuestionDifficulty.toWire(): Difficulty =
    when (this) {
        QuestionDifficulty.EASY -> Difficulty.EASY
        QuestionDifficulty.MEDIUM -> Difficulty.MEDIUM
        QuestionDifficulty.HARD -> Difficulty.HARD
        QuestionDifficulty.OTHER -> Difficulty.UNKNOWN
    }

/** The edit as the wire takes it: every text trimmed, a blank explanation or source none. */
internal fun QuestionEdit.toRequest(): EditQuestionRequest =
    EditQuestionRequest(
        id = id,
        revision = revision,
        text = text.trim(),
        options = options.map { it.trim() },
        correct = correct,
        topics = topics,
        difficulty = difficulty.toWire(),
        explanation = explanation?.trim()?.takeIf { it.isNotEmpty() },
        source = source?.trim()?.takeIf { it.isNotEmpty() },
    )

/** The reports counted by reason, most given first; a reason this build cannot name counts under `null`. */
internal fun AdminReportDto.toDomain(): ReportedQuestion =
    ReportedQuestion(
        question = question.toDomain(),
        open = open,
        reasons =
            reasons
                .groupBy { it.reason.toDomain() }
                .map { (reason, counts) -> reason to counts.sumOf { it.count } }
                .sortedByDescending { it.second },
        lastReportedAt = lastReportedAt,
    )

internal fun ReportReason.toDomain(): QuestionReportReason? =
    when (this) {
        ReportReason.WRONG_ANSWER -> QuestionReportReason.WRONG_ANSWER
        ReportReason.TYPO -> QuestionReportReason.TYPO
        ReportReason.AMBIGUOUS -> QuestionReportReason.AMBIGUOUS
        ReportReason.OFFENSIVE -> QuestionReportReason.OFFENSIVE
        ReportReason.OTHER -> QuestionReportReason.OTHER
        ReportReason.UNKNOWN -> null
    }

internal fun ReportOutcome.toWire(): ReportResolution =
    when (this) {
        ReportOutcome.FIXED -> ReportResolution.FIXED
        ReportOutcome.DISMISSED -> ReportResolution.DISMISSED
        ReportOutcome.RETIRED -> ReportResolution.RETIRED
    }

/** Statuses this build cannot name are counted together under [BankStatus.OTHER]. */
internal fun AdminOverviewDto.toDomain(): BankOverview =
    BankOverview(
        byStatus =
            byStatus
                .groupBy { it.status.toDomain() }
                .mapValues { (_, counts) -> counts.sumOf { it.count } },
        approvedByTopic = approvedByTopic.associate { it.topic to it.count },
        liveLobbies = liveLobbies,
        liveGames = liveGames,
        connectedPlayers = connectedPlayers,
        gamesToday = gamesToday,
    )

internal fun AccountOrder.toWire(): AccountSort =
    when (this) {
        AccountOrder.LAST_SEEN -> AccountSort.RECENT
        AccountOrder.CREATED -> AccountSort.CREATED
        AccountOrder.LEVEL -> AccountSort.LEVEL
        AccountOrder.GAMES -> AccountSort.GAMES
    }

internal fun AdminAccountDto.toDomain(): AccountSummary =
    AccountSummary(
        id = playerId,
        name = displayName,
        avatarId = avatarId,
        playGamesLinked = playGamesLinked,
        level = level,
        xp = xp,
        gamesPlayed = stats.gamesPlayed,
        gamesWon = stats.gamesWon,
        answersGiven = stats.answersGiven,
        answersCorrect = stats.answersCorrect,
        soloRuns = stats.soloRuns,
        soloBest =
            SoloBest(
                easy = stats.soloBestEasyScore,
                medium = stats.soloBestScore,
                hard = stats.soloBestHardScore,
            ),
        createdAt = createdAt,
        lastSeenAt = lastSeenAt,
    )

internal fun AdminAccountPageDto.toDomain(): AccountPage =
    AccountPage(accounts = accounts.map { it.toDomain() }, next = nextCursor, total = total)

internal fun AdminAccountDetailDto.toDomain(): AccountDetail =
    AccountDetail(
        account = account.toDomain(),
        topics = topics.map { AccountTopicStat(it.topicId, it.answered, it.correct) },
        recentGames =
            recentGames.map {
                AccountGame(
                    endedAt = it.endedAt,
                    solo = it.solo,
                    participants = it.participants,
                    standing = it.standing,
                    score = it.score,
                    correct = it.correct,
                    answered = it.answered,
                    finished = it.finished,
                )
            },
    )
