package io.ntole.kvizic.core.network

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ntole.kvizic.core.auth.SessionDto
import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.error.ErrorDto
import io.ntole.kvizic.core.network.environment.KvizicEnvironment
import io.ntole.kvizic.core.player.NameSource
import io.ntole.kvizic.core.player.ProfileDto
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

internal const val BASE_URL = "https://kvizic.test"

/** A session whose every credential names [playerId], so a request's header says who sent it. */
internal fun session(playerId: String): SessionDto =
    SessionDto(
        playerId = playerId,
        accessToken = "access-$playerId",
        refreshToken = "refresh-$playerId",
        accessTokenExpiresInSeconds = 900,
    )

/**
 * A store already holding [session]. Not suspending, so the plain helpers that build a client can call
 * it: [InMemoryTokenStorage] never suspends, so the write is done when `startCoroutine` returns.
 */
internal fun storeHolding(session: SessionDto?): SessionStore =
    SessionStore(InMemoryTokenStorage(), KvizicEnvironment.LOCAL).also { store ->
        if (session == null) return@also
        val write: suspend () -> Unit = { store.write(session) }
        write.startCoroutine(Continuation(EmptyCoroutineContext) { result -> result.getOrThrow() })
        check(store.read() == session) { "the in-memory write suspended" }
    }

internal val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

/** A profile as the server answers `GET /v1/me`: the ordinary authenticated call these tests make. */
internal val PROFILE =
    ProfileDto(playerId = "a", displayName = "Брзи Јеж", nameSource = NameSource.GENERATED, avatarId = "hedgehog")

internal fun MockRequestHandleScope.respondProfile(): HttpResponseData =
    respond(KvizicJson.encodeToString(PROFILE), HttpStatusCode.OK, jsonHeaders)

internal fun MockRequestHandleScope.respondSession(session: SessionDto): HttpResponseData =
    respond(KvizicJson.encodeToString(session), HttpStatusCode.OK, jsonHeaders)

/** An error exactly as the server's StatusPages renders one. */
internal fun MockRequestHandleScope.respondErrorDto(
    status: HttpStatusCode,
    code: ErrorCode,
): HttpResponseData = respond(KvizicJson.encodeToString(ErrorDto(message = "test", code = code)), status, jsonHeaders)
