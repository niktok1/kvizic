package io.ntole.kvizic.core.auth

import kotlinx.serialization.Serializable

/** Exchanges a refresh token for a fresh [SessionDto]. Sent unauthenticated: the token is the credential. */
@Serializable
public data class RefreshRequest(
    public val refreshToken: String,
) {
    public override fun toString(): String = "RefreshRequest(refreshToken=***)"
}
