package io.ntole.kvizic.core.domain.player

/**
 * One thing the shop sells, for [price] coins. An avatar or a frame is drawn by the client by its [id]; a title is
 * words, its [names] by content language, `en` too.
 */
public data class ShopItem(
    public val id: String,
    public val kind: ShopItemKind,
    public val price: Int,
    public val names: Map<String, String> = emptyMap(),
)

/** What a shop item is, and so where it is worn. */
public enum class ShopItemKind {
    AVATAR,
    FRAME,
    TITLE,

    /** A kind this build cannot show, which a newer server may sell. */
    OTHER,
}
