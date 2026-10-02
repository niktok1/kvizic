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
    /** The avatar picked last, shown as the player's until the server has it: settling, then asked. */
    val changingAvatar: String? = null,
    /** Why the last change of avatar failed. */
    val avatarFailure: HomeFailure? = null,
    /** Whether this build has Play Games, so a guest may be offered it. */
    val playGamesAvailable: Boolean = false,
    /** Whether a Play Games sign-in the player asked for is in flight. */
    val linkingPlayGames: Boolean = false,
    /** Why the last Play Games sign-in the player asked for failed, until it is taken down. */
    val playGamesFailure: HomeFailure? = null,
) {
    /** Whether Home offers the player to sign in with Play Games: a guest's profile read, in a build that has it. */
    val offersPlayGames: Boolean get() = playGamesAvailable && profile?.playGamesLinked == false
}

/** Why the profile could not be read, [error], with the wait a rate limit named, [retryAfter]. */
data class HomeFailure(
    val error: DomainError,
    val retryAfter: Duration? = null,
)
