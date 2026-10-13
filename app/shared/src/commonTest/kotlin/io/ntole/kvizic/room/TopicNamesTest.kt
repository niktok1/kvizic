package io.ntole.kvizic.room

import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.core.domain.topic.TopicGroup
import io.ntole.kvizic.language.Language
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A topic's and a group's name in each language: its own where the server has it, else the nearest the
 * server has, the Serbian last, in the language's script; and the server's other words in that script too.
 */
class TopicNamesTest {
    @Test
    fun `an old server's names read as Serbian in each language's script`() {
        val old = Topic("FOOD", "Храна и пиће", "Food & drink", 10)
        assertEquals(
            mapOf(
                Language.SERBIAN_CYRILLIC to "Храна и пиће",
                Language.SERBIAN_LATIN to "Hrana i piće",
                Language.CROATIAN to "Hrana i piće",
                Language.BOSNIAN to "Hrana i piće",
                Language.SLOVENIAN to "Hrana i piće",
                Language.MACEDONIAN to "Храна и пиће",
                Language.ENGLISH to "Food & drink",
            ),
            Language.entries.associateWith { topicName(old, it) },
        )
    }

    @Test
    fun `each language reads its own name and else the nearest the server has`() {
        val topic =
            Topic(
                "FOOD",
                "Храна и пиће",
                "Food & drink",
                10,
                names = mapOf("hr" to "Hrana i piće HR", "sl" to "Hrana in pijača", "en" to "Food and drink"),
            )
        assertEquals(
            mapOf(
                Language.SERBIAN_CYRILLIC to "Храна и пиће",
                Language.SERBIAN_LATIN to "Hrana i piće",
                Language.CROATIAN to "Hrana i piće HR",
                // Bosnian looks in Croatian before Serbian.
                Language.BOSNIAN to "Hrana i piće HR",
                Language.SLOVENIAN to "Hrana in pijača",
                // Macedonian looks in Serbian next, and keeps it in Cyrillic.
                Language.MACEDONIAN to "Храна и пиће",
                Language.ENGLISH to "Food and drink",
            ),
            Language.entries.associateWith { topicName(topic, it) },
        )
    }

    @Test
    fun `a Serbian name the server sends by tag is the one Serbian reads`() {
        val names = mapOf("sr-Cyrl" to "Храна и пиће", "mk" to "Храна и пијалаци")
        val topic = Topic("FOOD", "Храна", "Food", 10, names = names)
        assertEquals("Храна и пиће", topicName(topic, Language.SERBIAN_CYRILLIC))
        assertEquals("Hrana i piće", topicName(topic, Language.SLOVENIAN))
        assertEquals("Храна и пијалаци", topicName(topic, Language.MACEDONIAN))
    }

    @Test
    fun `a group reads as a topic does`() {
        val group = TopicGroup("FUN", "Забава", "Fun", names = mapOf("sl" to "Zabava SL"))
        assertEquals("Забава", groupName(group, Language.SERBIAN_CYRILLIC))
        assertEquals("Zabava", groupName(group, Language.CROATIAN))
        assertEquals("Zabava SL", groupName(group, Language.SLOVENIAN))
        assertEquals("Забава", groupName(group, Language.MACEDONIAN))
        assertEquals("Fun", groupName(group, Language.ENGLISH))
    }

    @Test
    fun `the server's words are shown in Latin in every language written in Latin`() {
        Language.entries.forEach { language ->
            val expected = if (language.isCyrillic) "Ђорђе Јовић" else "Đorđe Jović"
            assertEquals(expected, shownIn("Ђорђе Јовић", language), "$language")
        }
        // Latin is left as it is; Macedonian's own letters take North Macedonia's Latin.
        assertEquals("Ana", shownIn("Ana", Language.SERBIAN_CYRILLIC))
        assertEquals("Ѓорѓи Ќосе Ѕвезда", shownIn("Ѓорѓи Ќосе Ѕвезда", Language.MACEDONIAN))
        assertEquals("Gjorgji Kjose Dzvezda", shownIn("Ѓорѓи Ќосе Ѕвезда", Language.CROATIAN))
        assertEquals("GJORGJI", shownIn("ЃОРЃИ", Language.SERBIAN_LATIN))
        assertEquals("Đorđe i Gjorgji", shownIn("Ђорђе и Ѓорѓи", Language.SLOVENIAN))
    }
}
