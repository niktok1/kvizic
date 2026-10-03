package io.ntole.kvizic.core.network

import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.protocol.CloseReason
import kotlin.time.Duration

/**
 * An HTTP error response, carrying the server's own [ErrorCode] and the response's [status].
 *
 * Only a response that came back is one of these. A request that never got an answer fails with
 * whatever the engine threw, so the data layer can tell "offline" apart from "the server said no".
 *
 * [code] is [ErrorCode.UNKNOWN] when the body was not an `ErrorDto` (a proxy's error page, say), which is
 * when [status] is the only evidence of what went wrong.
 *
 * [reason] is the `ErrorDto`'s, telling apart the causes one [code] covers (a ban's kick or vote).
 *
 * [retryAfter] is how long the response's `Retry-After` header asks the caller to wait, which the server
 * sends with every 429, or null when it sent none or named a date instead of whole seconds.
 *
 * A network-layer type on purpose: `:core:data` translates it into the domain's `KvizicException`, so
 * nothing above the data layer imports a wire type.
 */
public class ApiException(
    public val code: ErrorCode,
    public val status: Int,
    message: String? = null,
    cause: Throwable? = null,
    public val retryAfter: Duration? = null,
    public val reason: CloseReason? = null,
) : Exception(message ?: code.name, cause)
