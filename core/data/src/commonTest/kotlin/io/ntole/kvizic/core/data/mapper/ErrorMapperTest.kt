package io.ntole.kvizic.core.data.mapper

import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.DomainError
import io.ntole.kvizic.core.domain.error.GameError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.network.ApiException
import io.ntole.kvizic.core.protocol.CloseReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

class ErrorMapperTest {
    /**
     * Every code the server sends, with the status it sends it with, and the domain error it becomes. A
     * code added to [ErrorCode] fails the test below until it is listed here, so none is mapped unseen.
     */
    private val everyCode: Map<ErrorCode, Pair<Int, DomainError>> =
        mapOf(
            // A rejected body means client and server disagree about the contract: a bug, not the player's.
            ErrorCode.VALIDATION_FAILED to (400 to CoreError.SERVER),
            ErrorCode.UNAUTHORIZED to (401 to CoreError.UNAUTHORIZED),
            // A moderator's token: never UNAUTHORIZED, which would drop the player's session.
            ErrorCode.FORBIDDEN to (403 to CoreError.FORBIDDEN),
            ErrorCode.INVALID_REFRESH_TOKEN to (401 to CoreError.UNAUTHORIZED),
            ErrorCode.RATE_LIMITED to (429 to CoreError.RATE_LIMITED),
            // Never SERVER, which would say to try again: only an update puts it right.
            ErrorCode.UPGRADE_REQUIRED to (426 to CoreError.UPGRADE_REQUIRED),
            // A Play Games sign-in's, never UNAUTHORIZED, which would drop the session.
            ErrorCode.PLAY_GAMES_CODE_REFUSED to (422 to CoreError.PLAY_GAMES_CODE_REFUSED),
            ErrorCode.PLAY_GAMES_UNAVAILABLE to (502 to CoreError.PLAY_GAMES_UNAVAILABLE),
            ErrorCode.PLAYER_NOT_FOUND to (404 to CoreError.PLAYER_NOT_FOUND),
            ErrorCode.INTERNAL to (500 to CoreError.SERVER),
            ErrorCode.LOBBY_NOT_FOUND to (404 to GameError.LOBBY_NOT_FOUND),
            ErrorCode.LOBBY_FULL to (409 to GameError.LOBBY_FULL),
            // A kick's ban, with a 403: never UNAUTHORIZED, which would drop the session.
            ErrorCode.LOBBY_BANNED to (403 to GameError.LOBBY_BANNED),
            ErrorCode.TOO_MANY_LOBBIES to (503 to GameError.TOO_MANY_LOBBIES),
            // A 503 of the server's own: try again in a moment, never "the server failed".
            ErrorCode.SERVER_DRAINING to (503 to GameError.SERVER_DRAINING),
            ErrorCode.INVALID_SETTINGS to (400 to GameError.INVALID_SETTINGS),
            ErrorCode.INVALID_AVATAR to (400 to GameError.INVALID_AVATAR),
            ErrorCode.QUESTION_NOT_FOUND to (404 to GameError.QUESTION_NOT_FOUND),
            ErrorCode.INVALID_QUESTION to (422 to GameError.INVALID_QUESTION),
            ErrorCode.STALE_REVISION to (409 to GameError.STALE_REVISION),
            ErrorCode.WRONG_STATUS to (409 to GameError.WRONG_STATUS),
            ErrorCode.TOPIC_EXISTS to (409 to GameError.TOPIC_EXISTS),
            ErrorCode.TOPIC_NOT_FOUND to (404 to GameError.TOPIC_NOT_FOUND),
            ErrorCode.UNKNOWN to (418 to CoreError.UNKNOWN),
        )

    @Test
    fun `every code the server sends is listed here`() {
        assertEquals(ErrorCode.entries.toSet(), everyCode.keys)
    }

    @Test
    fun `the server's own code decides the domain error`() =
        runTest {
            everyCode.forEach { (code, expected) ->
                val (status, error) = expected
                assertMapsTo(error, ApiException(code, status = status))
            }
        }

    @Test
    fun `a ban the others voted is told from the host's kick`() =
        runTest {
            val banned = { reason: CloseReason? -> ApiException(ErrorCode.LOBBY_BANNED, 403, reason = reason) }
            assertMapsTo(GameError.LOBBY_VOTED_OUT, banned(CloseReason.VOTED_OUT))
            assertMapsTo(GameError.LOBBY_BANNED, banned(CloseReason.KICKED))
            assertMapsTo(GameError.LOBBY_BANNED, banned(null))
        }

    @Test
    fun `the code wins over the status whenever there is one`() =
        runTest {
            assertMapsTo(GameError.LOBBY_FULL, ApiException(ErrorCode.LOBBY_FULL, status = 503))
            assertMapsTo(GameError.SERVER_DRAINING, ApiException(ErrorCode.SERVER_DRAINING, status = 500))
        }

    @Test
    fun `without a code a server-error status still reads as SERVER`() =
        runTest {
            // What a proxy's HTML 502 or 503 page, or any body that is not an ErrorDto, arrives as.
            listOf(500, 502, 503, 599).forEach { status ->
                assertMapsTo(CoreError.SERVER, ApiException(ErrorCode.UNKNOWN, status = status))
            }
        }

    @Test
    fun `without a code a 429 still reads as RATE_LIMITED`() =
        runTest {
            assertMapsTo(CoreError.RATE_LIMITED, ApiException(ErrorCode.UNKNOWN, status = 429))
        }

    @Test
    fun `without a code a 426 still reads as UPGRADE_REQUIRED`() =
        runTest {
            assertMapsTo(CoreError.UPGRADE_REQUIRED, ApiException(ErrorCode.UNKNOWN, status = 426))
        }

    @Test
    fun `a bare 401 is not taken as proof the session is dead`() =
        runTest {
            // UNAUTHORIZED would reset the session and orphan the guest account.
            assertMapsTo(CoreError.UNKNOWN, ApiException(ErrorCode.UNKNOWN, status = 401))
        }

    @Test
    fun `without a code any other status stays UNKNOWN`() =
        runTest {
            listOf(400, 403, 404, 409, 499, 600).forEach { status ->
                assertMapsTo(CoreError.UNKNOWN, ApiException(ErrorCode.UNKNOWN, status = status))
            }
        }

    @Test
    fun `the wait the server named reaches the caller`() =
        runTest {
            val limited = ApiException(ErrorCode.RATE_LIMITED, status = 429, retryAfter = 42.seconds)

            val mapped = assertFailsWith<KvizicException> { runApi { throw limited } }

            assertEquals(42.seconds, mapped.retryAfter)
        }

    @Test
    fun `a failure with no response at all is NETWORK`() =
        runTest {
            val failure =
                assertFailsWith<KvizicException> { runApi { throw IllegalStateException("connection reset") } }

            assertEquals(CoreError.NETWORK, failure.error)
        }

    @Test
    fun `the browser engine's failed fetch is NETWORK`() =
        runTest {
            // Ktor's js and wasmJs engines reject a failed fetch with exactly this: an Error, not an Exception.
            val failure = assertFailsWith<KvizicException> { runApi { throw Error("Fail to fetch") } }

            assertEquals(CoreError.NETWORK, failure.error)
        }

    @Test
    fun `a fault in the program is not dressed up as NETWORK`() =
        runTest {
            val bug = NotImplementedError()

            val thrown = assertFailsWith<NotImplementedError> { runApi { throw bug } }

            assertSame(bug, thrown)
        }

    @Test
    fun `cancellation is rethrown and never mapped`() =
        runTest {
            val cancellation = CancellationException("caller went away")

            val thrown = assertFailsWith<CancellationException> { runApi { throw cancellation } }

            assertSame(cancellation, thrown)
        }

    private suspend fun assertMapsTo(
        expected: DomainError,
        failure: ApiException,
    ) {
        val mapped = assertFailsWith<KvizicException> { runApi { throw failure } }
        assertEquals(expected, mapped.error, "${failure.code} with status ${failure.status}")
        assertSame(failure, mapped.cause)
    }
}
