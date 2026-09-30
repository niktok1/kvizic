package io.ntole.kvizic.core.auth

import kotlinx.serialization.Serializable

/**
 * A newly minted or refreshed session, one device's. The identity is server-issued, so [playerId] can't
 * be forged by a tampered client. [accessToken] is a short-lived JWT sent as `Authorization: Bearer`;
 * [refreshToken] is opaque and long-lived, and the server keeps only its hash.
 */
@Serializable
public data class SessionDto(
    public val playerId: String,
    public val accessToken: String,
    public val refreshToken: String,
    public val accessTokenExpiresInSeconds: Long,
)
