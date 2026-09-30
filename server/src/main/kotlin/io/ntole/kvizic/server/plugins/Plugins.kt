package io.ntole.kvizic.server.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.ChannelOverflow
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.error.ErrorDto
import io.ntole.kvizic.server.auth.JWT_AUTH
import io.ntole.kvizic.server.auth.TokenService
import io.ntole.kvizic.server.auth.playerId
import io.ntole.kvizic.server.config.ServerConfig
import kotlinx.serialization.json.Json

/**
 * The REST side's `Json`. `encodeDefaults` is the counterpart of the client's `coerceInputValues`: a
 * client can coerce only into a default that is present in the payload. The socket has its own,
 * `ProtocolJson`, shared with the client.
 */
internal val ServerJson: Json =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

fun Application.installPlugins(
    config: ServerConfig,
    tokens: TokenService,
) {
    install(ContentNegotiation) {
        json(ServerJson)
    }

    install(CallLogging)

    install(CORS) {
        // A browser will not call the API cross-origin without this. Hosts come from config, so nothing
        // is wide open by default.
        config.allowedWebOrigins.forEach { origin ->
            val scheme = origin.scheme
            if (scheme == null) allowHost(origin.host) else allowHost(origin.host, schemes = listOf(scheme))
        }
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(KvizicApi.Headers.ADMIN_TOKEN)
        allowHeader(KvizicApi.Headers.CLIENT_PLATFORM)
        allowHeader(KvizicApi.Headers.CLIENT_VERSION)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        // Without it a browser page could never read how long a 429 asks it to wait.
        exposeHeader(HttpHeaders.RetryAfter)
    }

    // After CORS, so a browser can read their refusals too, and before anything else of a request.
    install(ClientVersionCheck) { minimums = config.minClientVersions }
    install(RequestBodyCap)

    install(WebSockets) {
        // The game pings at the application level instead (`Heartbeat`), which also measures the trip.
        pingPeriodMillis = 0
        timeoutMillis = config.game.timings.silentAfter.inWholeMilliseconds
        maxFrameSize =
            config.game.limits.maxClientFrameBytes
                .toLong()
        masking = false
        // Both channels are unlimited by default, so a slow reader's frames would pile up in memory. The
        // outgoing one is bounded, and a socket that fills it is closed (`SocketConnection.send`).
        channels {
            outgoing = bounded(config.game.limits.outgoingQueue, ChannelOverflow.SUSPEND)
            incoming = bounded(INCOMING_QUEUE, ChannelOverflow.SUSPEND)
        }
    }

    install(Authentication) {
        jwt(JWT_AUTH) {
            realm = "kvizic"
            // A header Ktor cannot parse ("Bearer a b") throws out of the default authHeader, a 500. Read
            // it as no credentials instead, which the challenge answers with the usual 401.
            authHeader { call ->
                try {
                    call.request.parseAuthorizationHeader()
                } catch (_: BadRequestException) {
                    null
                }
            }
            verifier(tokens.verifier)
            validate { credential -> credential.payload.playerId()?.let { JWTPrincipal(credential.payload) } }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorDto(code = ErrorCode.UNAUTHORIZED, message = "authentication required"),
                )
            }
        }
    }

    install(StatusPages) {
        exception<ApiFailure> { call, failure ->
            call.respond(failure.status, ErrorDto(message = failure.message, code = failure.code))
        }
        // Ktor's rate limiter refuses with a bare 429, dressed here as the contract's error. Its
        // Retry-After, already on the response, is kept.
        status(HttpStatusCode.TooManyRequests) { call, status ->
            val retry = call.response.headers[HttpHeaders.RetryAfter]?.let { seconds -> "; retry in $seconds s" }
            call.respond(
                status,
                ErrorDto(code = ErrorCode.RATE_LIMITED, message = "too many requests${retry.orEmpty()}"),
            )
        }
        exception<Throwable> { call, cause ->
            // Log the detail, return none of it.
            call.application.log.error("unhandled failure on ${call.request.local.uri}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorDto(code = ErrorCode.INTERNAL, message = "internal error"),
            )
        }
    }
}

/** How many frames a socket may have waiting to be read before it stops reading more from the network. */
private const val INCOMING_QUEUE = 32
