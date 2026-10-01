package io.ntole.kvizic.admin

import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.GameError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.moderation.BankStatus
import io.ntole.kvizic.core.domain.moderation.QuestionDifficulty
import io.ntole.kvizic.core.domain.report.QuestionReportReason

/**
 * The moderation app's words, in English, as its moderator works in: the questions themselves stay as
 * written, in Serbian, and topics are named in Serbian.
 */
internal fun messageOf(failure: KvizicException): String =
    when (failure.error) {
        CoreError.FORBIDDEN -> {
            "Wrong admin token. Type it again."
        }

        CoreError.RATE_LIMITED -> {
            failure.retryAfter?.let { "Too many requests. Wait ${it.inWholeSeconds} s." }
                ?: "Too many requests. Wait a bit."
        }

        CoreError.NETWORK -> {
            "Can't reach the server."
        }

        CoreError.SERVER -> {
            "The server failed or refused the request."
        }

        GameError.QUESTION_NOT_FOUND -> {
            "No such question any more. Load again."
        }

        GameError.WRONG_STATUS -> {
            "It moved meanwhile. Load again."
        }

        GameError.STALE_REVISION -> {
            "Edited meanwhile. Close, load again and edit that."
        }

        GameError.INVALID_QUESTION -> {
            "The server refuses the question as it stands."
        }

        CoreError.PLAYER_NOT_FOUND -> {
            "No such account: a mistyped id, or one deleted already."
        }

        else -> {
            "Something went wrong (${failure.error}). A bare 404 means moderation is off on that server."
        }
    }

/** Ready-made rejection reasons a tap puts in the reason's field, to send as they are or edit first. */
internal val READY_REASONS =
    listOf(
        "Wrong answer",
        "Two answers could be right",
        "Unclear wording",
        "Too obscure",
        "Too easy",
        "Fact may change",
        "Duplicate",
        "Breaks the style rules",
    )

internal fun BankStatus.word(): String =
    when (this) {
        BankStatus.DRAFT -> "Draft"
        BankStatus.APPROVED -> "Approved"
        BankStatus.REJECTED -> "Rejected"
        BankStatus.RETIRED -> "Retired"
        BankStatus.SUSPENDED -> "Suspended"
        BankStatus.OTHER -> "Unknown status"
    }

internal fun QuestionDifficulty.word(): String =
    when (this) {
        QuestionDifficulty.EASY -> "Easy"
        QuestionDifficulty.MEDIUM -> "Medium"
        QuestionDifficulty.HARD -> "Hard"
        QuestionDifficulty.OTHER -> "No difficulty"
    }

internal fun QuestionReportReason?.word(): String =
    when (this) {
        QuestionReportReason.WRONG_ANSWER -> "Wrong answer"
        QuestionReportReason.TYPO -> "Typo"
        QuestionReportReason.AMBIGUOUS -> "Ambiguous"
        QuestionReportReason.OFFENSIVE -> "Offensive"
        QuestionReportReason.OTHER -> "Other"
        null -> "Unknown reason"
    }

internal fun AdminTab.word(): String =
    when (this) {
        AdminTab.REVIEW -> "Review"
        AdminTab.BANK -> "Bank"
        AdminTab.REPORTS -> "Reports"
        AdminTab.OVERVIEW -> "Overview"
        AdminTab.ACCOUNTS -> "Accounts"
    }

/** The letters a question's answers are named by, as the game names them in Cyrillic. */
internal val LETTERS = listOf("А", "Б", "В", "Г")
