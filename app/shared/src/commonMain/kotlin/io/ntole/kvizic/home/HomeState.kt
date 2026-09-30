package io.ntole.kvizic.home

import io.ntole.kvizic.core.domain.error.DomainError
import io.ntole.kvizic.core.domain.player.Profile
import kotlin.time.Duration

/**
 * What the placeholder Home screen shows: the [profile] read last, the player's who plays now, or none
 * before a read works; whether a read is in flight, [loading]; and why the last one failed, [failure].
 */
data class HomeState(
    val profile: Profile? = null,
    val loading: Boolean = false,
    val failure: HomeFailure? = null,
)

/** Why the profile could not be read, [error], with the wait a rate limit named, [retryAfter]. */
data class HomeFailure(
    val error: DomainError,
    val retryAfter: Duration? = null,
)
