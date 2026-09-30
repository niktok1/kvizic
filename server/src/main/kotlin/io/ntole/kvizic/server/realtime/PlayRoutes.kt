package io.ntole.kvizic.server.realtime

import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.server.plugins.RouteLimit
import io.ntole.kvizic.server.plugins.clientAddress
import io.ntole.kvizic.server.plugins.rateLimit

/**
 * The realtime socket. No bearer token: the upgrade carries nothing secret, and the socket's first
 * frame redeems a ticket REST issued ([PlaySockets]). Upgrades are limited per address.
 */
fun Route.playRoutes(
    sockets: PlaySockets,
    clientIpHeader: String?,
) {
    rateLimit(RouteLimit.SOCKET_UPGRADES) {
        webSocket(KvizicApi.Paths.PLAY) {
            sockets.serve(this, call.request.clientAddress(clientIpHeader))
        }
    }
}
