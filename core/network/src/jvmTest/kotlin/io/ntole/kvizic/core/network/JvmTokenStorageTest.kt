package io.ntole.kvizic.core.network

import kotlinx.coroutines.test.runTest
import java.util.prefs.AbstractPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The desktop's storage, one player per profile: several instances on one machine each play as their
 * own, which is how a lobby is tried out locally. Over preferences of the test's own, in memory, so no
 * test reads or writes this machine's.
 */
class JvmTokenStorageTest {
    private val root = MemoryPreferences(parent = null, name = "")

    @Test
    fun `no profile keeps everything in the game's own node`() =
        runTest {
            JvmTokenStorage.forProfile(null, root).write("kvizic.session.local", "the default player")

            assertEquals("the default player", root.node("io/ntole/kvizic").get("kvizic.session.local", null))
            assertEquals("the default player", JvmTokenStorage.forProfile("  ", root).read("kvizic.session.local"))
        }

    @Test
    fun `each profile is a player of its own and none sees another's`() =
        runTest {
            val ana = JvmTokenStorage.forProfile("ana", root)
            val boris = JvmTokenStorage.forProfile("boris", root)

            ana.write("kvizic.session.local", "ana's session")
            boris.write("kvizic.session.local", "boris's session")

            assertEquals("ana's session", JvmTokenStorage.forProfile(" ana ", root).read("kvizic.session.local"))
            assertEquals("boris's session", boris.read("kvizic.session.local"))
            assertNull(JvmTokenStorage.forProfile(null, root).read("kvizic.session.local"), "the default is apart")
            assertEquals("ana's session", root.node("io/ntole/kvizic/ana").get("kvizic.session.local", null))
        }

    @Test
    fun `a removal is the profile's own`() =
        runTest {
            val ana = JvmTokenStorage.forProfile("ana", root)
            val ceca = JvmTokenStorage.forProfile("ceca", root)
            ana.write("kvizic.language", "en")
            ceca.write("kvizic.language", "sr-Latn")

            ana.remove("kvizic.language")

            assertNull(ana.read("kvizic.language"))
            assertEquals("sr-Latn", ceca.read("kvizic.language"))
        }

    @Test
    fun `a profile that is no node's name stops the app and says what it was`() {
        listOf("ana/boris", "../escape", "has space", "x".repeat(JvmTokenStorage.MAX_PROFILE_LENGTH + 1)).forEach {
            val failure = assertFailsWith<IllegalArgumentException>(it) { JvmTokenStorage.forProfile(it, root) }
            assertTrue("\"$it\"" in failure.message.orEmpty(), failure.message)
        }
        assertTrue(root.childrenNames().isEmpty(), "no node was made for a profile refused")
    }

    @Test
    fun `a profile is checked and trimmed before any preference is touched`() {
        assertEquals("ana", JvmTokenStorage.checkedProfile(" ana\n"))
        assertEquals(null, JvmTokenStorage.checkedProfile(null))
        assertEquals(null, JvmTokenStorage.checkedProfile("  "))
        assertEquals("boris_2-x", JvmTokenStorage.checkedProfile("boris_2-x"))
    }

    /** Preferences held in memory: a node's values and children in maps, and nothing ever on disk. */
    private class MemoryPreferences(
        parent: AbstractPreferences?,
        name: String,
    ) : AbstractPreferences(parent, name) {
        private val values = mutableMapOf<String, String>()
        private val children = mutableMapOf<String, MemoryPreferences>()

        override fun putSpi(
            key: String,
            value: String,
        ) {
            values[key] = value
        }

        override fun getSpi(key: String): String? = values[key]

        override fun removeSpi(key: String) {
            values.remove(key)
        }

        override fun removeNodeSpi() = Unit

        override fun keysSpi(): Array<String> = values.keys.toTypedArray()

        override fun childrenNamesSpi(): Array<String> = children.keys.toTypedArray()

        override fun childSpi(name: String): AbstractPreferences =
            children.getOrPut(name) { MemoryPreferences(this, name) }

        override fun syncSpi() = Unit

        override fun flushSpi() = Unit
    }
}
