package io.ntole.kvizic.server.player

import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.player.Avatars
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NamesTest {
    @Test
    fun `every nickname is Cyrillic, short, and its adjective agrees with its animal`() {
        val all = Nicknames.all()
        assertEquals(16 * 16, all.size)
        all.forEach { name ->
            val (adjective, animal) = name.split(" ")
            assertTrue(name.all { it == ' ' || it in 'Ѐ'..'ӿ' }, name)
            assertTrue(name.length <= KvizicApi.Limits.MAX_DISPLAY_NAME_LENGTH, name)
            val feminineNoun = animal.endsWith("а")
            assertEquals(feminineNoun, adjective.endsWith("а"), name)
        }
    }

    @Test
    fun `a guest's nickname names their avatar's animal`() {
        assertTrue(Nicknames.forAvatar("hedgehog", Random(1)).endsWith(" Јеж"))
        assertTrue(Nicknames.forAvatar("fox", Random(1)).endsWith(" Лисица"))
        assertEquals(
            Avatars.ALL.size,
            Avatars.ALL
                .map { Nicknames.forAvatar(it, Random(3)).split(" ")[1] }
                .toSet()
                .size,
        )
    }

    @Test
    fun `a service's name is cleaned of what could disguise it and held to its length`() {
        assertEquals("Marko Petrović", DisplayNames.clean("  Marko \t\n Petrović  "))
        assertEquals("admin", DisplayNames.clean("ad​min"), "zero-width characters go")
        assertEquals("Ana", DisplayNames.clean("‮Ana‬"), "bidi overrides go")
        assertEquals("Ана", DisplayNames.clean("Ана\u0000"), "control characters go")
        assertEquals(KvizicApi.Limits.MAX_DISPLAY_NAME_LENGTH, DisplayNames.clean("я".repeat(40))?.length)
        assertEquals("Ćevapčić 🔥", DisplayNames.clean("Ćevapčić 🔥"))
        assertEquals("é", DisplayNames.clean("é"), "composed")
        assertNull(DisplayNames.clean("   "))
        assertNull(DisplayNames.clean(null))
    }

    @Test
    fun `a name with a blocked word keeps the nickname, in either script or disguised`() {
        listOf("Kurac123", "КУРАЦ", "fuck_you", "sh1t", "Ј3бем те", "Pička", "Hitler88").forEach { name ->
            assertNull(DisplayNames.clean(name), name)
        }
        listOf("Kura", "Bitola", "Jelena", "Шампион", "Pikachu").forEach { name ->
            assertEquals(name, DisplayNames.clean(name), name)
        }
    }
}
