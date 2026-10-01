package io.ntole.kvizic.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

/**
 * The game's back stack, made by hand, with no navigation library: the screens opened, [Screen.Home] at
 * the bottom and the one shown, [current], on top. Snapshot state, so a composition reading it shows each
 * change.
 *
 * No screen is on the stack twice: [open] goes back to one already there instead, so the stack is at most
 * every screen once, and a way home is simply `open(Screen.Home)`.
 */
class Navigator internal constructor(
    screens: List<Screen>,
) {
    constructor() : this(listOf(Screen.Home))

    init {
        require(screens.firstOrNull() == Screen.Home) { "a back stack starts at Home: $screens" }
        require(screens.distinct() == screens) { "a screen is on the back stack twice: $screens" }
        require(screens.all { it.onBackStack }) { "a screen that is never on the back stack is: $screens" }
    }

    /** The back stack, Home first and [current] last. */
    var screens: List<Screen> by mutableStateOf(screens)
        private set

    /** The screen shown. */
    val current: Screen get() = screens.last()

    /** Whether there is a screen to go back to: everywhere but Home. */
    val canGoBack: Boolean get() = screens.size > 1

    /** The screen [current] was opened from, the one under it, which [back] goes to; none at Home. */
    val previous: Screen? get() = screens.getOrNull(screens.size - 2)

    /** Shows [screen] over the one shown, or goes back to it, dropping all above, if it is on the stack. */
    fun open(screen: Screen) {
        require(screen.onBackStack) { "$screen is never on the back stack" }
        val at = screens.indexOf(screen)
        screens = if (at < 0) screens + screen else screens.subList(0, at + 1).toList()
    }

    /**
     * Shows [screen] in place of the one shown, which leaves the stack: back from [screen] then goes to the
     * screen the one shown was opened from. As [open] does, it goes back to [screen] instead when that is on
     * the stack below. At Home, which never leaves the stack, it is [open].
     */
    fun replace(screen: Screen) {
        if (canGoBack) screens = screens.dropLast(1)
        open(screen)
    }

    /**
     * Goes back to the screen before, and says whether there was one. At Home there is none, so it does
     * nothing and answers false: Android's back then leaves the app.
     */
    fun back(): Boolean {
        if (!canGoBack) return false
        screens = screens.dropLast(1)
        return true
    }

    /**
     * Follows the room: once the player is in one, [inRoom], the room over Home, wherever the seat was taken
     * from; once they are not, Home, from the room or its settings. Any other change leaves the stack be.
     */
    fun followRoom(inRoom: Boolean) {
        val shown = shownFor(inRoom)
        if (shown == current) return
        open(Screen.Home)
        if (shown == Screen.Room) open(Screen.Room)
    }

    /**
     * The screen to show while the player is [inRoom] or not: [current], or the one [followRoom] is to make
     * it, so a screen shows the room the very frame the player is in one, and never what it would say a frame
     * before the stack follows.
     */
    fun shownFor(inRoom: Boolean): Screen {
        val showingRoom = current == Screen.Room || current == Screen.RoomSettings
        return when {
            inRoom && !showingRoom -> Screen.Room
            !inRoom && showingRoom -> Screen.Home
            else -> current
        }
    }

    companion object {
        /**
         * Keeps the back stack in saved state, as each screen's key, so an Android activity made anew, on a
         * rotation say, shows the screen it showed. A saved stack this build cannot read whole starts again
         * at Home.
         */
        val Saver: Saver<Navigator, Any> =
            listSaver(
                save = { navigator -> navigator.screens.map(Screen::key) },
                restore = { keys ->
                    val screens = keys.map(Screen::ofKey)
                    val readable = screens.filterNotNull()
                    val whole =
                        readable.size == screens.size &&
                            readable.firstOrNull() == Screen.Home &&
                            readable.distinct() == readable &&
                            readable.all { it.onBackStack }
                    if (whole) Navigator(readable) else Navigator()
                },
            )
    }
}
