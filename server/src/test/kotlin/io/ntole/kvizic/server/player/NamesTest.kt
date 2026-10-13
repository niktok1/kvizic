package io.ntole.kvizic.server.player

import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.player.Avatars
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
