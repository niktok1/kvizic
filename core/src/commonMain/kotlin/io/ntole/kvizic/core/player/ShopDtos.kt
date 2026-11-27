package io.ntole.kvizic.core.player

import kotlinx.serialization.Serializable

/** What the shop sells: [items] in the order it shows them. Prices and items are the server's to change. */
@Serializable
public data class ShopDto(
    public val items: List<ShopItemDto> = emptyList(),
)

/**
 * One thing the shop sells, for [price] coins. An avatar or a frame is drawn by the client, which hides one it
 * has no drawing for; a title is words, its [names] by content language (`ContentLanguages`), `en` too.
 */
@Serializable
public data class ShopItemDto(
    public val id: String,
    public val kind: ShopItemKind = ShopItemKind.UNKNOWN,
    public val price: Int,
    public val names: Map<String, String> = emptyMap(),
)

/** What a shop item is, and so where it is worn. */
@Serializable
public enum class ShopItemKind {
    AVATAR,
    FRAME,
    TITLE,
    CROWN,
    UNKNOWN,
}

/** Buys [itemId] for coins. */
@Serializable
public data class BuyItemRequest(
    public val itemId: String,
)

/** The frame, the title and the host crown the player wears, each one they own, or none. */
@Serializable
public data class SetWornRequest(
    public val frameId: String? = null,
    public val titleId: String? = null,
    public val crownId: String? = null,
)
