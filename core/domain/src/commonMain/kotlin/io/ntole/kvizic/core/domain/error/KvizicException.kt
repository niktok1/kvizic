package io.ntole.kvizic.core.domain.error

import kotlin.time.Duration

/**
 * The one exception that leaves the data layer. [error] is what callers branch on; [message] is
 * diagnostic only and never shown to a player.
 *
 * [retryAfter] is how long the server asked the caller to wait before trying again, which it names with
 * every [CoreError.RATE_LIMITED], or null when it named no wait: carried here so a screen can say how
 * long without parsing the message.
 */
public class KvizicException(
    public val error: DomainError,
    message: String? = null,
    cause: Throwable? = null,
    public val retryAfter: Duration? = null,
) : Exception(message ?: error.name, cause)
