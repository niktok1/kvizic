package io.ntole.kvizic.core.error

import kotlinx.serialization.Serializable

/**
 * Body of any non-2xx response. [message] is diagnostic only: never shown to a player, never branched
 * on. [code] keeps its default so an unknown code coerces to [ErrorCode.UNKNOWN].
 */
@Serializable
public data class ErrorDto(
    public val message: String? = null,
    public val code: ErrorCode = ErrorCode.UNKNOWN,
)
