package io.ntole.kvizic.admin

import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.GameError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.moderation.AccountOrder
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

internal fun AccountOrder.word(): String =
    when (this) {
        AccountOrder.LAST_SEEN -> "Last seen"
        AccountOrder.CREATED -> "Newest"
        AccountOrder.LEVEL -> "Level"
        AccountOrder.GAMES -> "Games"
    }

/** How long before [now] [then] was, in its largest whole unit: "just now", "5 min ago", "3 d ago", "2 mo ago". */
internal fun ago(
    then: Long,
    now: Long,
): String {
    val minutes = ((now - then) / MINUTE).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < MINUTES_IN_HOUR -> "$minutes min ago"
        minutes < MINUTES_IN_DAY -> "${minutes / MINUTES_IN_HOUR} h ago"
        minutes < MINUTES_IN_MONTH -> "${minutes / MINUTES_IN_DAY} d ago"
        minutes < MINUTES_IN_YEAR -> "${minutes / MINUTES_IN_MONTH} mo ago"
        else -> "${minutes / MINUTES_IN_YEAR} y ago"
    }
}

/** [millis] as a UTC date and time, "2026-10-04 21:07", worked out by hand: no date library is carried for it. */
internal fun utc(millis: Long): String {
    val days = millis.floorDiv(MILLIS_IN_DAY)
    val minuteOfDay = millis.mod(MILLIS_IN_DAY) / MINUTE
    // Howard Hinnant's civil-from-days.
    val z = days + DAYS_TO_YEAR_ZERO
    val era = z.floorDiv(DAYS_IN_ERA)
    val dayOfEra = z - era * DAYS_IN_ERA
    val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val shifted = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * shifted + 2) / 5 + 1
    val month = if (shifted < 10) shifted + 3 else shifted - 9
    val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
    return "$year-${month.two()}-${day.two()} ${(minuteOfDay / 60).two()}:${(minuteOfDay % 60).two()}"
}

private fun Long.two(): String = toString().padStart(2, '0')

private const val MINUTE = 60_000L
private const val MILLIS_IN_DAY = 86_400_000L
private const val MINUTES_IN_HOUR = 60L
private const val MINUTES_IN_DAY = 1_440L
private const val MINUTES_IN_MONTH = 43_200L
private const val MINUTES_IN_YEAR = 525_600L
private const val DAYS_TO_YEAR_ZERO = 719_468L
private const val DAYS_IN_ERA = 146_097L
