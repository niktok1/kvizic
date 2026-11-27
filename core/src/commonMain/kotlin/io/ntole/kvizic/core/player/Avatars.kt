package io.ntole.kvizic.core.player

/**
 * The game's avatars, by id: sixteen Balkan animals, drawn by the client. An id is data on the wire, not
 * an enum, so an avatar a newer server adds only shows as a silhouette on an older client.
 */
public object Avatars {
    public val ALL: List<String> =
        listOf(
            "fox",
            "bear",
            "owl",
            "hedgehog",
            "wolf",
            "lynx",
            "deer",
            "stork",
            "squirrel",
            "hare",
            "badger",
            "otter",
            "tortoise",
            "frog",
            "bee",
            "cat",
        )

    /** The avatars the shop sells for coins, never handed out: worn only by a player who bought one. */
    public val SHOP: List<String> = listOf("eagle", "boar", "beaver", "jackal")
}
