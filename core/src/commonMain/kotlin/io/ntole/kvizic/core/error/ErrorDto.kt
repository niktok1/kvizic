package io.ntole.kvizic.core.error

import io.ntole.kvizic.core.protocol.CloseReason
import kotlinx.serialization.Serializable

/**
 * Body of any non-2xx response. [message] is diagnostic only: never shown to a player, never branched
 * on. [code] keeps its default so an unknown code coerces to [ErrorCode.UNKNOWN].
 *
 * [reason] tells apart the causes one code covers, where a client says them differently: a
 * [ErrorCode.LOBBY_BANNED] is [CloseReason.KICKED] by the host or [CloseReason.VOTED_OUT] by the others.
 * An optional field rather than a code of its own, which an older client would read as UNKNOWN and retry.
 */
@Serializable
public data class ErrorDto(
    public val message: String? = null,
    public val code: ErrorCode = ErrorCode.UNKNOWN,
    public val reason: CloseReason? = null,
)
