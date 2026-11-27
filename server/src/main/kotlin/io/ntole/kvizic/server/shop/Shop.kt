package io.ntole.kvizic.server.shop

import io.ntole.kvizic.core.player.Avatars
import io.ntole.kvizic.core.player.ShopItemDto
import io.ntole.kvizic.core.player.ShopItemKind

/**
 * What the shop sells, in the order it shows them: frames and host crowns, each a ladder from iron to diamond,
 * and the predators, all drawn by the client, which hides an item it has no drawing for, so a new one goes on
 * sale once a release draws it. `SHOP_PRICES` sets any price on a server ([priced]).
 */
object Shop {
    private val TIERS = listOf("iron", "bronze", "silver", "gold", "platinum", "diamond")

    val ITEMS: List<ShopItemDto> =
        TIERS.zip(listOf(100, 250, 500, 1_000, 2_000, 4_000)) { tier, price ->
            ShopItemDto("frame.$tier", ShopItemKind.FRAME, price)
        } +
            TIERS.zip(listOf(80, 200, 400, 800, 1_600, 3_200)) { tier, price ->
                ShopItemDto("crown.$tier", ShopItemKind.CROWN, price)
            } +
            Avatars.SHOP.zip(listOf(300, 400, 500, 600, 700, 800)) { id, price ->
                ShopItemDto(id, ShopItemKind.AVATAR, price)
            }

    private val byId = ITEMS.associateBy { it.id }

    /** The item [id], or null for one the shop does not sell. */
    fun item(id: String): ShopItemDto? = byId[id]

    /** [ITEMS] with [prices] in place of their own, by id. An id the shop does not sell fails at boot. */
    fun priced(prices: Map<String, Int>): List<ShopItemDto> {
        val unknown = prices.keys - ITEMS.map { it.id }.toSet()
        require(unknown.isEmpty()) { "SHOP_PRICES names what the shop does not sell: $unknown" }
        return ITEMS.map { item -> prices[item.id]?.let { item.copy(price = it) } ?: item }
    }
}
