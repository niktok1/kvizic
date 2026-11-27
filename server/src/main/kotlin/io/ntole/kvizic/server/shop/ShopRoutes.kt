package io.ntole.kvizic.server.shop

import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.player.BuyItemRequest
import io.ntole.kvizic.core.player.SetWornRequest
import io.ntole.kvizic.core.player.ShopDto
import io.ntole.kvizic.server.auth.JWT_AUTH
import io.ntole.kvizic.server.auth.authenticatedPlayerId
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.player.ProfileStore
import io.ntole.kvizic.server.plugins.ApiFailure
import io.ntole.kvizic.server.plugins.RouteLimit
import io.ntole.kvizic.server.plugins.rateLimit
import io.ntole.kvizic.server.plugins.receiveOrReject

/** The shop: what it sells, buying with coins, and wearing what was bought. Each change answers the profile. */
fun Route.shopRoutes(
    db: Db,
    clock: () -> Long = System::currentTimeMillis,
) {
    authenticate(JWT_AUTH) {
        rateLimit(RouteLimit.ME) {
            get(KvizicApi.Paths.SHOP) { call.respond(ShopDto(Shop.ITEMS)) }
        }

        rateLimit(RouteLimit.AVATARS) {
            post(KvizicApi.Paths.SHOP_ITEMS) {
                val playerId = call.authenticatedPlayerId()
                val id = call.receiveOrReject<BuyItemRequest>("item").itemId
                val item = Shop.item(id) ?: throw ApiFailure.itemNotFound(id)
                val profile =
                    db.query {
                        ShopStore.buy(playerId, item, clock())
                        ProfileStore.of(playerId)
                    } ?: throw ApiFailure.unauthorized("unknown player")
                call.respond(profile)
            }

            post(KvizicApi.Paths.MY_WORN) {
                val playerId = call.authenticatedPlayerId()
                val body = call.receiveOrReject<SetWornRequest>("worn")
                val profile =
                    db.query {
                        if (!ShopStore.wear(playerId, body.frameId, body.titleId)) {
                            throw ApiFailure.unauthorized("unknown player")
                        }
                        ProfileStore.of(playerId)
                    } ?: throw ApiFailure.unauthorized("unknown player")
                call.respond(profile)
            }
        }
    }
}
