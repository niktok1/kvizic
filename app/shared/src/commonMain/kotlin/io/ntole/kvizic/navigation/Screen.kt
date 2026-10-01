package io.ntole.kvizic.navigation

/**
 * The game's screens, each one place in the [Navigator]'s back stack but [Update]. [key] is what saved
 * state keeps for it and what the analytics name it by, which never changes with the objects' names.
 */
sealed class Screen(
    internal val key: String,
    /** Whether the screen is ever on the back stack: all but [Update], which takes the whole app's place. */
    internal val onBackStack: Boolean = true,
) {
    /** Where the app opens, and the bottom of every back stack. */
    data object Home : Screen("home")

    /** The game's version, its legal pages, the player's account id and statistics, and deleting the account. */
    data object About : Screen("about")

    /** A room's code typed, to join it. */
    data object Join : Screen("join")

    /** The open public rooms, to join one. */
    data object PublicRooms : Screen("public_rooms")

    /** A room of the player's own, its settings picked first. */
    data object NewRoom : Screen("new_room")

    /** The room the player is in, whatever it does: shown while they are in one, and never otherwise. */
    data object Room : Screen("room")

    /** The settings of the room the player hosts, changed. */
    data object RoomSettings : Screen("room_settings")

    /**
     * That a new version is available, once the server refuses this build: shown in place of every other
     * screen for the app's life, never on the back stack, since every call would be refused again.
     */
    data object Update : Screen("update", onBackStack = false)

    internal companion object {
        // Listed on each call, not kept in a property: the companion's properties are set up with the class,
        // before the objects are when one of them is used first, so a kept list could hold nulls.
        fun ofKey(key: String): Screen? =
            listOf(Home, About, Join, PublicRooms, NewRoom, Room, RoomSettings, Update).firstOrNull { it.key == key }
    }
}
