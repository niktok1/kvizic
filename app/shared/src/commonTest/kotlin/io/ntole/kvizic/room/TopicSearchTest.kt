package io.ntole.kvizic.room

import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.language.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The picker's search finds a name whatever script and accents it is typed in, and a summary names a few. */
class TopicSearchTest {
    @Test
    fun `a query in either script and with no accents finds a name`() {
        listOf("spo", "Спо", "SPO").forEach { assertTrue(searchKey(it) in searchKey("Спорт"), it) }
        assertTrue(searchKey("nauka") in searchKey("Наука и технологија"))
        assertTrue(searchKey("djak") in searchKey("Ђак"))
        assertTrue(searchKey("đak") in searchKey("Ђак"))
        assertTrue(searchKey("knjizev") in searchKey("Језик и књижевност"))
        assertTrue(searchKey("cevap") in searchKey("Ћевапи"))
    }

    @Test
    fun `a summary names two topics and counts the rest`() {
        val topics =
            listOf(
                Topic("SPORT", "Спорт", "Sport", 10),
                Topic("MUSIC", "Музика", "Music", 10),
                Topic("FILM_TV", "Филм и серије", "Film & TV", 10),
                Topic("LOCAL", "Наши простори", "Our region", 10),
            )
        assertEquals("Све", topicsSummary(emptyList(), topics, Language.SERBIAN_CYRILLIC, "Све"))
        assertEquals("Спорт, Музика", topicsSummary(listOf("SPORT", "MUSIC"), topics, Language.SERBIAN_CYRILLIC, "Све"))
        assertEquals(
            "Sport, Muzika +2",
            topicsSummary(listOf("SPORT", "MUSIC", "FILM_TV", "LOCAL"), topics, Language.SERBIAN_LATIN, "Sve"),
        )
    }
}
