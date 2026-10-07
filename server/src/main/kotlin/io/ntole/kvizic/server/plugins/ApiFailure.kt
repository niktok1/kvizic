package io.ntole.kvizic.server.plugins

import io.ktor.http.HttpStatusCode
import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.protocol.CloseReason

/**
 * The only exception route code should throw: an HTTP status with the contract's own [ErrorCode], which
 * `StatusPages` renders as an `ErrorDto`.
 */
class ApiFailure(
    val status: HttpStatusCode,
    val code: ErrorCode,
    override val message: String? = null,
    cause: Throwable? = null,
    /** What lay behind [code] when it has more than one cause, as `ErrorDto.reason`. */
    val reason: CloseReason? = null,
) : RuntimeException(message, cause) {
    companion object {
        fun validation(
            message: String,
            cause: Throwable? = null,
        ) = ApiFailure(HttpStatusCode.BadRequest, ErrorCode.VALIDATION_FAILED, message, cause)

        /** A body over [MAX_REQUEST_BODY_BYTES]: no correct client sends one, so a bug, as a malformed body is. */
        fun bodyTooLarge() =
            ApiFailure(
                HttpStatusCode.PayloadTooLarge,
                ErrorCode.VALIDATION_FAILED,
                "request body over $MAX_REQUEST_BODY_BYTES bytes",
            )

        fun unauthorized(message: String = "missing or invalid credentials") =
            ApiFailure(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED, message)

        /** An admin route without the server's admin token: never [unauthorized] (see `requireAdmin`). */
        fun forbidden() = ApiFailure(HttpStatusCode.Forbidden, ErrorCode.FORBIDDEN, "admin token missing or wrong")

        fun invalidRefreshToken() =
            ApiFailure(
                HttpStatusCode.Unauthorized,
                ErrorCode.INVALID_REFRESH_TOKEN,
                "refresh token unknown, expired, or already rotated",
            )

        /** A build older than its platform's [minimum] ([ClientVersionCheck]). */
        fun upgradeRequired(
            platform: String,
            version: Int,
            minimum: Int,
        ) = ApiFailure(
            HttpStatusCode.UpgradeRequired,
            ErrorCode.UPGRADE_REQUIRED,
            "$platform build $version is older than $minimum, the oldest this server serves",
        )

        fun playGamesCodeRefused() =
            ApiFailure(
                HttpStatusCode.UnprocessableEntity,
                ErrorCode.PLAY_GAMES_CODE_REFUSED,
                "Google refused the Play Games code; ask Play Games for a new one",
            )

        fun playGamesUnavailable() =
            ApiFailure(HttpStatusCode.BadGateway, ErrorCode.PLAY_GAMES_UNAVAILABLE, "Play Games could not be asked")

        /** An account a moderator asked to delete that no player has. The message never names the id. */
        fun playerNotFound() = ApiFailure(HttpStatusCode.NotFound, ErrorCode.PLAYER_NOT_FOUND, "no such account")

        fun lobbyNotFound() =
            ApiFailure(HttpStatusCode.NotFound, ErrorCode.LOBBY_NOT_FOUND, "no open lobby has that code")

        fun lobbyFull() = ApiFailure(HttpStatusCode.Conflict, ErrorCode.LOBBY_FULL, "the lobby has no free seat")

        /** A player put out of the lobby, by the host's kick or the others' vote ([reason]). */
        fun lobbyBanned(reason: CloseReason) =
            ApiFailure(HttpStatusCode.Forbidden, ErrorCode.LOBBY_BANNED, "this player was put out", reason = reason)

        fun tooManyLobbies() =
            ApiFailure(HttpStatusCode.ServiceUnavailable, ErrorCode.TOO_MANY_LOBBIES, "the server holds all it can")

        fun draining() =
            ApiFailure(HttpStatusCode.ServiceUnavailable, ErrorCode.SERVER_DRAINING, "the server is restarting")

        fun invalidSettings(message: String) =
            ApiFailure(HttpStatusCode.BadRequest, ErrorCode.INVALID_SETTINGS, message)

        fun invalidAvatar() = ApiFailure(HttpStatusCode.BadRequest, ErrorCode.INVALID_AVATAR, "no such avatar")

        fun questionNotFound(id: String) =
            ApiFailure(HttpStatusCode.NotFound, ErrorCode.QUESTION_NOT_FOUND, "no question $id")

        /** A draft or an edit the moderator can put right. */
        fun invalidQuestion(message: String) =
            ApiFailure(HttpStatusCode.UnprocessableEntity, ErrorCode.INVALID_QUESTION, message)

        fun staleRevision(id: String) =
            ApiFailure(HttpStatusCode.Conflict, ErrorCode.STALE_REVISION, "question $id changed since it was read")

        /** A bulk deletion the moderator counted as one size that is another now. */
        fun staleCount() =
            ApiFailure(
                HttpStatusCode.Conflict,
                ErrorCode.STALE_REVISION,
                "the number of accounts changed since it was counted",
            )

        fun wrongStatus(
            id: String,
            expected: String,
        ) = ApiFailure(HttpStatusCode.Conflict, ErrorCode.WRONG_STATUS, "question $id is not $expected")

        fun topicExists(id: String) =
            ApiFailure(HttpStatusCode.Conflict, ErrorCode.TOPIC_EXISTS, "a topic is $id already")

        fun topicNotFound(id: String) = ApiFailure(HttpStatusCode.NotFound, ErrorCode.TOPIC_NOT_FOUND, "no topic $id")
    }
}
