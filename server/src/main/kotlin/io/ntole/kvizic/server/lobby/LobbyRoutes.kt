package io.ntole.kvizic.server.lobby

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.lobby.CreateLobbyRequest
import io.ntole.kvizic.core.lobby.JoinLobbyRequest
import io.ntole.kvizic.core.lobby.LobbyListDto
import io.ntole.kvizic.core.lobby.PublicLobbyDto
import io.ntole.kvizic.core.lobby.TicketDto
import io.ntole.kvizic.server.auth.JWT_AUTH
import io.ntole.kvizic.server.auth.authenticatedPlayerId
import io.ntole.kvizic.server.auth.authenticatedSessionId
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.player.PlayerStore
import io.ntole.kvizic.server.plugins.ApiFailure
import io.ntole.kvizic.server.plugins.RouteLimit
import io.ntole.kvizic.server.plugins.clientAddress
import io.ntole.kvizic.server.plugins.rateLimit
import io.ntole.kvizic.server.plugins.receiveOrReject
import io.ntole.kvizic.server.plugins.retryAfterSeconds

/**
 * Everything about lobbies outside the socket: opening one, joining one by code, Quick play, a solo run
 * and the public list. Each seat comes back as a [TicketDto] for the socket. A rejoin is a join by the
 * same code, which keeps the seat. A code naming no lobby spends the address's guessing budget
 * ([CodeGuessGuard]).
 */
fun Route.lobbyRoutes(
    db: Db,
    registry: LobbyRegistry,
    guard: CodeGuessGuard,
    topics: () -> Set<String>,
    ticketTtlMs: Long,
    clientIpHeader: String?,
) {
    suspend fun RoutingCall.seat(): Seat {
        val playerId = authenticatedPlayerId()
        val sessionId = authenticatedSessionId()
        // A validly signed token can outlive its player.
        val player = db.query { PlayerStore.find(playerId) } ?: throw ApiFailure.unauthorized("unknown player")
        return Seat(playerId, sessionId, player.displayName, player.avatarId)
    }

    fun Joined.ticket() =
        TicketDto(lobbyId = lobby.id, code = lobby.code, ticket = ticket, ticketExpiresInMs = ticketTtlMs)

    authenticate(JWT_AUTH) {
        rateLimit(RouteLimit.LOBBY_LIST) {
            get(KvizicApi.Paths.LOBBIES) {
                val (online, searching) = registry.presence()
                call.respond(LobbyListDto(registry.publicList().map { it.toDto() }, online, searching))
            }

            get(KvizicApi.Paths.LOBBY_PREVIEWS) {
                val address = call.request.clientAddress(clientIpHeader)
                guard.lockedOut(address)?.let { wait ->
                    call.response.header(HttpHeaders.RetryAfter, wait.retryAfterSeconds())
                    call.respond(HttpStatusCode.TooManyRequests)
                    return@get
                }
                val summary =
                    LobbyCode.parse(call.parameters["code"].orEmpty())?.let { registry.preview(it) } ?: run {
                        guard.missed(address)
                        throw ApiFailure.lobbyNotFound()
                    }
                call.respond(summary.toDto())
            }
        }

        rateLimit(RouteLimit.LOBBY_CREATES) {
            post(KvizicApi.Paths.LOBBIES) {
                val settings = call.receiveOrReject<CreateLobbyRequest>("lobby").settings.tidied()
                val kind = kindOf(settings)
                settingsProblem(settings, kind, topics(), members = 1)?.let { throw ApiFailure.invalidSettings(it) }
                call.respond(HttpStatusCode.Created, registry.create(call.seat(), settings, kind).ticket())
            }
        }

        rateLimit(RouteLimit.LOBBY_JOINS) {
            post(KvizicApi.Paths.LOBBY_JOINS) {
                val address = call.request.clientAddress(clientIpHeader)
                guard.lockedOut(address)?.let { wait ->
                    call.response.header(HttpHeaders.RetryAfter, wait.retryAfterSeconds())
                    call.respond(HttpStatusCode.TooManyRequests)
                    return@post
                }
                val raw = call.receiveOrReject<JoinLobbyRequest>("join").code
                val code =
                    LobbyCode.parse(raw) ?: run {
                        guard.missed(address)
                        throw ApiFailure.lobbyNotFound()
                    }
                val seat = call.seat()
                val joined =
                    try {
                        registry.join(seat, code)
                    } catch (failure: ApiFailure) {
                        if (failure.code == ErrorCode.LOBBY_NOT_FOUND) guard.missed(address)
                        throw failure
                    }
                call.respond(joined.ticket())
            }
        }

        rateLimit(RouteLimit.QUICK_PLAY) {
            post(KvizicApi.Paths.QUICK_PLAY) {
                call.respond(registry.quickPlay(call.seat()).ticket())
            }
        }

        rateLimit(RouteLimit.SOLO_RUNS) {
            post(KvizicApi.Paths.SOLO_RUNS) {
                call.respond(registry.solo(call.seat()).ticket())
            }
        }
    }
}

private fun LobbySummary.toDto() =
    PublicLobbyDto(
        code = code,
        hostName = hostName,
        hostAvatar = hostAvatar,
        players = seatsTaken,
        maxPlayers = settings.maxPlayers,
        inGame = inGame,
        settings = settings,
    )
