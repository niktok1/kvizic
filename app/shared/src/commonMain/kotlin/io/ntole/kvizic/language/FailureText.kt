package io.ntole.kvizic.language

import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.DomainError
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * What a screen says of a failure, [error], in a few words: offline, a rate limit with the wait the server
 * named, [retryAfter], or anything else, which asks to try again. A screen with failures of its own to
 * name says those first, and this for the rest.
 */
fun Strings.failureText(
    error: DomainError,
    retryAfter: Duration? = null,
): String =
    when (error) {
        CoreError.NETWORK -> offline
        CoreError.RATE_LIMITED -> rateLimited(retryAfter)
        else -> somethingWrong
    }

/**
 * Too many tries, and how long to wait when the server named it: in seconds under a minute, and in minutes
 * rounded up from one, so an hour's budget spent never reads as thousands of seconds.
 */
private fun Strings.rateLimited(retryAfter: Duration?): String =
    when {
        retryAfter == null -> tooManyTriesNoWait
        retryAfter < 1.minutes -> tooManyTries.fill(retryAfter.inWholeSeconds)
        else -> tooManyTriesMinutes.fill(ceil(retryAfter / 1.minutes).toLong())
    }
