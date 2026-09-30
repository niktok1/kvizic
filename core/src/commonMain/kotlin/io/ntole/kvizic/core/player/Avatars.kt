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
}
