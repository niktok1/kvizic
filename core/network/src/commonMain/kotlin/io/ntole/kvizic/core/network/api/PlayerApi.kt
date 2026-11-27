package io.ntole.kvizic.core.network.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.player.BuyItemRequest
import io.ntole.kvizic.core.player.ProfileDto
import io.ntole.kvizic.core.player.SetAvatarRequest
import io.ntole.kvizic.core.player.SetWornRequest
import io.ntole.kvizic.core.player.ShopDto

/** The session player's own endpoints. Each goes with the session's bearer, which the Auth plugin attaches. */
public class PlayerApi(
    private val client: HttpClient,
) {
    /** The session player's profile and stats. */
    public suspend fun me(): ProfileDto = client.get(KvizicApi.Paths.ME).body()

    /** Picks [avatarId], one of the game's avatars, answered with the profile as it then stands. */
    public suspend fun setAvatar(avatarId: String): ProfileDto =
        client.post(KvizicApi.Paths.MY_AVATAR) { setBody(SetAvatarRequest(avatarId)) }.body()

    /** What the shop sells. */
    public suspend fun shop(): ShopDto = client.get(KvizicApi.Paths.SHOP).body()

    /** Buys [itemId] with coins, answered with the profile as it then stands. */
    public suspend fun buy(itemId: String): ProfileDto =
        client.post(KvizicApi.Paths.SHOP_ITEMS) { setBody(BuyItemRequest(itemId)) }.body()

    /** Puts on [frameId], [titleId] and [crownId], answered with the profile as it then stands. */
    public suspend fun wear(
        frameId: String?,
        titleId: String?,
        crownId: String?,
    ): ProfileDto = client.post(KvizicApi.Paths.MY_WORN) { setBody(SetWornRequest(frameId, titleId, crownId)) }.body()

    /**
     * Deletes the account of the player the bearer names, for good, answered 204. An expired access token
     * is refreshed first, as for any call, and a player deleted already is 401, their refresh refused.
     */
    public suspend fun deleteAccount() {
        client.post(KvizicApi.Paths.ME_DELETION)
    }
}
