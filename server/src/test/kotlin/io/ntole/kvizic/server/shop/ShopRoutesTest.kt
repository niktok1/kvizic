package io.ntole.kvizic.server.shop

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.auth.SessionDto
import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.error.ErrorDto
import io.ntole.kvizic.core.player.BuyItemRequest
import io.ntole.kvizic.core.player.ProfileDto
import io.ntole.kvizic.core.player.SetAvatarRequest
import io.ntole.kvizic.core.player.SetWornRequest
import io.ntole.kvizic.core.player.ShopDto
import io.ntole.kvizic.server.db.Profiles
import io.ntole.kvizic.server.realtime.GameServer
import io.ntole.kvizic.server.realtime.bodyOrFail
import io.ntole.kvizic.server.realtime.runGameServer
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.test.Test
import kotlin.test.assertEquals

/** The shop over HTTP: what it sells, what each refusal says, and a bought avatar worn. */
class ShopRoutesTest {
    private suspend fun GameServer.post(
        who: SessionDto,
        path: String,
        body: Any,
    ) = client.post(path) {
        bearerAuth(who.accessToken)
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun GameServer.refused(
        who: SessionDto,
        path: String,
        body: Any,
    ): ErrorCode = post(who, path, body).body<ErrorDto>().code

    @Test
    fun `the shop sells its catalogue, refuses what it must, and a bought avatar can be worn`() =
        runGameServer("shop-routes") { server ->
            val ana = server.guest()
            val shop: ShopDto = server.client.get(KvizicApi.Paths.SHOP) { bearerAuth(ana.accessToken) }.bodyOrFail()
            assertEquals(Shop.ITEMS, shop.items)

            assertEquals(
                ErrorCode.NOT_ENOUGH_COINS,
                server.refused(ana, KvizicApi.Paths.SHOP_ITEMS, BuyItemRequest("eagle")),
            )
            assertEquals(
                ErrorCode.ITEM_NOT_FOUND,
                server.refused(ana, KvizicApi.Paths.SHOP_ITEMS, BuyItemRequest("nope")),
            )
            assertEquals(
                ErrorCode.INVALID_AVATAR,
                server.refused(ana, KvizicApi.Paths.MY_AVATAR, SetAvatarRequest("eagle")),
            )
            assertEquals(
                ErrorCode.VALIDATION_FAILED,
                server.refused(ana, KvizicApi.Paths.MY_WORN, SetWornRequest(frameId = "frame.double")),
            )

            val settings = server.database
            transaction(
                Database.connect(
                    settings.jdbcUrl,
                    user = settings.user.orEmpty(),
                    password = settings.password.orEmpty(),
                ),
            ) {
                Profiles.update({ Profiles.playerId eq ana.playerId }) { it[coins] = 1_000 }
            }
            val bought: ProfileDto = server.post(ana, KvizicApi.Paths.SHOP_ITEMS, BuyItemRequest("eagle")).bodyOrFail()
            assertEquals(700 to listOf("eagle"), bought.coins to bought.items)
            val wearing: ProfileDto =
                server
                    .post(
                        ana,
                        KvizicApi.Paths.MY_AVATAR,
                        SetAvatarRequest("eagle"),
                    ).bodyOrFail()
            assertEquals("eagle", wearing.avatarId)
        }
}
