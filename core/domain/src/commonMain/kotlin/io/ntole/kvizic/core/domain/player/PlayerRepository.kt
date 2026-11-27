package io.ntole.kvizic.core.domain.player

/** The session player, as the server knows them. Implemented in `:core:data`. */
public interface PlayerRepository {
    /**
     * The session player's profile, read from the server every time.
     *
     * @throws io.ntole.kvizic.core.domain.error.KvizicException on any failure.
     */
    public suspend fun profile(): Profile

    /**
     * Picks [avatarId], one of the game's avatars, for the session player, and returns the profile as it
     * then stands.
     *
     * @throws io.ntole.kvizic.core.domain.error.KvizicException on any failure: `INVALID_AVATAR` for an id
     *   the server does not have.
     */
    public suspend fun setAvatar(avatarId: String): Profile

    /** What the shop sells, in its order. @throws io.ntole.kvizic.core.domain.error.KvizicException on any failure. */
    public suspend fun shop(): List<ShopItem>

    /**
     * Buys [itemId] with the session player's coins, and returns the profile as it then stands; one they own
     * already costs nothing.
     *
     * @throws io.ntole.kvizic.core.domain.error.KvizicException on any failure: `NOT_ENOUGH_COINS`, or
     *   `ITEM_NOT_FOUND` for an item the shop no longer sells.
     */
    public suspend fun buy(itemId: String): Profile

    /**
     * Puts on [frameId] and [titleId], each one the player owns or none, and returns the profile as it then
     * stands. @throws io.ntole.kvizic.core.domain.error.KvizicException on any failure.
     */
    public suspend fun wear(
        frameId: String?,
        titleId: String?,
    ): Profile
}
