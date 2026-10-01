package io.ntole.kvizic.navigation

import androidx.compose.runtime.saveable.SaverScope
import io.ntole.kvizic.navigation.Screen.About
import io.ntole.kvizic.navigation.Screen.Home
import io.ntole.kvizic.navigation.Screen.Update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The game's back stack, driven as the screens' buttons drive it. */
class NavigatorTest {
    @Test
    fun `the app opens on Home with nothing to go back to`() {
        val navigator = Navigator()

        assertEquals(Home, navigator.current)
        assertEquals(listOf(Home), navigator.screens)
        assertFalse(navigator.canGoBack)
        assertNull(navigator.previous)
    }

    /** Android's back then does what it does without the app's own: it leaves the app. */
    @Test
    fun `back from Home does nothing and says so`() {
        val navigator = Navigator()

        assertFalse(navigator.back())
        assertEquals(listOf(Home), navigator.screens)
    }

    @Test
    fun `About opens over Home and back returns to Home`() {
        val navigator = Navigator()

        navigator.open(About)
        assertEquals(listOf(Home, About), navigator.screens)
        assertTrue(navigator.canGoBack)
        assertEquals(Home, navigator.previous)

        assertTrue(navigator.back())
        assertEquals(listOf(Home), navigator.screens)
    }

    /** No screen is on the stack twice, so no back ever shows the one it leaves. */
    @Test
    fun `a screen opened while it is on the stack is gone back to`() {
        val navigator = Navigator().apply { open(About) }

        navigator.open(Home)

        assertEquals(listOf(Home), navigator.screens)
    }

    @Test
    fun `opening the screen shown changes nothing`() {
        val navigator = Navigator().apply { open(About) }

        navigator.open(About)

        assertEquals(listOf(Home, About), navigator.screens)
    }

    @Test
    fun `a screen replacing the one shown takes its place`() {
        val navigator = Navigator()

        navigator.replace(About)
        assertEquals(listOf(Home, About), navigator.screens, "Home never leaves the stack")

        navigator.replace(Home)
        assertEquals(listOf(Home), navigator.screens, "back to Home, which is below")
    }

    /** The update screen takes the whole app's place, never a place on the stack. */
    @Test
    fun `the update screen is never on the back stack`() {
        val navigator = Navigator()

        assertFailsWith<IllegalArgumentException> { navigator.open(Update) }
        assertFailsWith<IllegalArgumentException> { Navigator(listOf(Home, Update)) }
        assertEquals(listOf(Home), navigator.screens)
    }

    /** An Android activity made anew, on a rotation say, shows the screen it showed. */
    @Test
    fun `the back stack comes back whole from saved state`() {
        listOf(listOf(Home), listOf(Home, About)).forEach { screens ->
            val saved = save(Navigator(screens))

            assertEquals(screens, restore(saved).screens)
        }
    }

    @Test
    fun `saved state this build cannot read whole starts again at Home`() {
        listOf(
            listOf("home", "lobby"),
            listOf("about"),
            emptyList(),
            listOf("home", "about", "about"),
            listOf("home", "update"),
        ).forEach { saved ->
            assertEquals(listOf(Home), restore(saved).screens, "$saved")
        }
    }

    @Test
    fun `each screen's key is its own`() {
        assertEquals(listOf("home", "about", "update"), listOf(Home, About, Update).map { it.key })
        assertEquals(listOf(Home, About, Update), listOf("home", "about", "update").map(Screen::ofKey))
    }

    private fun save(navigator: Navigator): Any {
        val saved = with(Navigator.Saver) { SaverScope { true }.save(navigator) }
        return checkNotNull(saved)
    }

    private fun restore(saved: Any): Navigator = checkNotNull(Navigator.Saver.restore(saved))

    @Test
    fun `a seat taken from any screen opens the room over Home`() {
        listOf(Screen.Home, Screen.Join, Screen.PublicRooms, Screen.NewRoom).forEach { from ->
            val navigator = Navigator()
            navigator.open(from)

            navigator.followRoom(inRoom = true)

            assertEquals(listOf(Home, Screen.Room), navigator.screens, "from $from")
        }
    }

    @Test
    fun `the room letting the player go goes back Home from its settings too`() {
        listOf(listOf(Screen.Room), listOf(Screen.Room, Screen.RoomSettings)).forEach { above ->
            val navigator = Navigator()
            above.forEach(navigator::open)

            navigator.followRoom(inRoom = false)

            assertEquals(listOf(Home), navigator.screens, "from $above")
        }
    }

    @Test
    fun `following the room changes nothing where the screen already agrees`() {
        val navigator = Navigator()
        navigator.open(Screen.Room)
        navigator.open(Screen.RoomSettings)
        navigator.followRoom(inRoom = true)
        assertEquals(listOf(Home, Screen.Room, Screen.RoomSettings), navigator.screens)

        val away = Navigator()
        away.open(About)
        away.followRoom(inRoom = false)
        assertEquals(listOf(Home, About), away.screens)
    }
}
