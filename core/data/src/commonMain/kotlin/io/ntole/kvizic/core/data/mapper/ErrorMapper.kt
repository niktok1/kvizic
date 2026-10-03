package io.ntole.kvizic.core.data.mapper

import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.DomainError
import io.ntole.kvizic.core.domain.error.GameError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.network.ApiException
import io.ntole.kvizic.core.protocol.CloseReason
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/**
 * Runs a network call and turns every failure into a [KvizicException]. Wrapping every data-layer call in
 * this is what keeps `ApiException`, and with it the whole wire vocabulary, from leaking upward.
 */
internal suspend fun <T> runApi(block: suspend () -> T): T =
    try {
        block()
    } catch (cancellation: CancellationException) {
        // Never a domain error: a cancelled coroutine that looked like a failed request would break
        // structured concurrency.
        throw cancellation
    } catch (api: ApiException) {
        // The wait the server named goes up with it: nothing above may parse the diagnostic message.
        throw KvizicException(api.toDomainError(), api.message, api, retryAfter = api.retryAfter)
    } catch (other: Throwable) {
        // Nothing came back, or it did not arrive whole. A fault in the program or the VM is neither, and
        // must not be dressed up as one.
        if (!other.isRequestFailure()) throw other
        // An answer arrived and this build could not decode it: client and server disagree about the
        // contract. Like a rejected request body (VALIDATION_FAILED), that is a bug on one side, and
        // "check your connection" would send the player looking in the wrong place.
        if (other.isUndecodableBody()) throw KvizicException(CoreError.SERVER, other.message, other)
        throw KvizicException(CoreError.NETWORK, other.message, other)
    }

/**
 * Whether decoding a body failed. Ktor wraps the decoder's [SerializationException] in its own converter
 * exception, so the cause chain is searched (bounded, in case of a cycle). A body that was not JSON at
 * all, a captive portal's login page say, fails before decoding and stays NETWORK.
 */
private fun Throwable.isUndecodableBody(): Boolean =
    generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).any { it is SerializationException }

private const val MAX_CAUSE_DEPTH = 8

/**
 * Whether this is an ordinary failed exchange, as opposed to a bug or a VM fault.
 *
 * Everywhere but the browser that means an [Exception]. Ktor's browser engines (js and wasmJs) reject a
 * failed fetch with a bare `kotlin.Error("Fail to fetch")`, so an offline web player's request would
 * otherwise escape as an unhandled error. Only that exact class is let in: its subclasses
 * (`NotImplementedError`, `AssertionError`, and on the JVM `OutOfMemoryError`) are faults that should
 * crash, not read as offline.
 */
private fun Throwable.isRequestFailure(): Boolean = this is Exception || this::class == Error::class

/**
 * The server's own code when it sent one, otherwise the broad class of failure the status carries.
 *
 * The fallback is for error responses the server never wrote: a proxy's 502 or 503 page is HTML, yields
 * no `ErrorDto`, and would otherwise read as UNKNOWN when it is plainly a server failure.
 *
 * A bare 401 deliberately stays UNKNOWN. UNAUTHORIZED makes the data layer throw the session away and mint
 * a new guest, orphaning the old account; every 401 the server sends carries an `ErrorDto`, so one without
 * it did not come from the server and is no evidence the session is dead.
 */
internal fun ApiException.toDomainError(): DomainError =
    when {
        // One code, two causes the player is told apart.
        code == ErrorCode.LOBBY_BANNED && reason == CloseReason.VOTED_OUT -> GameError.LOBBY_VOTED_OUT

        code != ErrorCode.UNKNOWN -> code.toDomain()

        status == HTTP_TOO_MANY_REQUESTS -> CoreError.RATE_LIMITED

        status == HTTP_UPGRADE_REQUIRED -> CoreError.UPGRADE_REQUIRED

        status in HTTP_SERVER_ERRORS -> CoreError.SERVER

        else -> CoreError.UNKNOWN
    }

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_UPGRADE_REQUIRED = 426
private val HTTP_SERVER_ERRORS = 500..599

/** Every code the server sends, each once: a code added to [ErrorCode] does not compile until it is mapped. */
internal fun ErrorCode.toDomain(): DomainError =
    when (this) {
        // A rejected body means client and server disagree about the contract: a bug on one side, not
        // something a player did.
        ErrorCode.VALIDATION_FAILED -> CoreError.SERVER

        ErrorCode.INTERNAL -> CoreError.SERVER

        ErrorCode.UNAUTHORIZED, ErrorCode.INVALID_REFRESH_TOKEN -> CoreError.UNAUTHORIZED

        // Never UNAUTHORIZED: that would throw the player's session away over a moderator's token.
        ErrorCode.FORBIDDEN -> CoreError.FORBIDDEN

        ErrorCode.RATE_LIMITED -> CoreError.RATE_LIMITED

        // Only an update puts it right. Never UNAUTHORIZED: it comes with a 426, and the session is fine.
        ErrorCode.UPGRADE_REQUIRED -> CoreError.UPGRADE_REQUIRED

        // Answered only to a Play Games sign-in. Never UNAUTHORIZED, which would throw the session away: a
        // refused code comes with a 422, and Google not answering with a 502.
        ErrorCode.PLAY_GAMES_CODE_REFUSED -> CoreError.PLAY_GAMES_CODE_REFUSED

        ErrorCode.PLAY_GAMES_UNAVAILABLE -> CoreError.PLAY_GAMES_UNAVAILABLE

        ErrorCode.PLAYER_NOT_FOUND -> CoreError.PLAYER_NOT_FOUND

        ErrorCode.LOBBY_NOT_FOUND -> GameError.LOBBY_NOT_FOUND

        ErrorCode.LOBBY_FULL -> GameError.LOBBY_FULL

        // A kick's ban comes with a 403: never UNAUTHORIZED, which would throw the session away.
        ErrorCode.LOBBY_BANNED -> GameError.LOBBY_BANNED

        ErrorCode.TOO_MANY_LOBBIES -> GameError.TOO_MANY_LOBBIES

        // A 503 of the server's own: the game says to try again in a moment, never "the server failed".
        ErrorCode.SERVER_DRAINING -> GameError.SERVER_DRAINING

        ErrorCode.INVALID_SETTINGS -> GameError.INVALID_SETTINGS

        ErrorCode.INVALID_AVATAR -> GameError.INVALID_AVATAR

        ErrorCode.QUESTION_NOT_FOUND -> GameError.QUESTION_NOT_FOUND

        ErrorCode.INVALID_QUESTION -> GameError.INVALID_QUESTION

        ErrorCode.STALE_REVISION -> GameError.STALE_REVISION

        ErrorCode.WRONG_STATUS -> GameError.WRONG_STATUS

        ErrorCode.TOPIC_EXISTS -> GameError.TOPIC_EXISTS

        ErrorCode.TOPIC_NOT_FOUND -> GameError.TOPIC_NOT_FOUND

        ErrorCode.UNKNOWN -> CoreError.UNKNOWN
    }
