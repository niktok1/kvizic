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
        assertTrue(searchKey("pice") in searchKey("Храна и пиће"))
    }

    @Test
    fun `the region's letters fold as Serbian's do`() {
        listOf("cevapi", "čevapi", "ćevapi").forEach { assertTrue(searchKey(it) in searchKey("Ćevapi"), it) }
        listOf("zaba", "žaba", "ŽABA").forEach { assertTrue(searchKey(it) in searchKey("Žaba"), it) }
        listOf("sport", "šport").forEach { assertTrue(searchKey(it) in searchKey("Šport"), it) }
        // Macedonian's own letters, in Cyrillic or in its Latin, with or without the accents.
        listOf("gjorgji", "ѓорѓи", "Ѓорѓи", "ǵorǵi").forEach { assertTrue(searchKey(it) in searchKey("Ѓорѓи"), it) }
        listOf("kjosk", "ќоск", "ḱosk").forEach { assertTrue(searchKey(it) in searchKey("Ќоск"), it) }
        listOf("dzvezda", "ѕвезда").forEach { assertTrue(searchKey(it) in searchKey("Ѕвезда"), it) }
    }

    @Test
    fun `a topic is found by the name shown and by its Serbian in either script`() {
        val topic = Topic("FOOD", "Храна и пиће", "Food & drink", 10, names = mapOf("sl" to "Hrana in pijača"))
        val names = searchedNames(topic, Language.SLOVENIAN)
        listOf("pijač", "pijac", "piće", "пиће", "pice", "drink").forEach { query ->
            assertTrue(names.any { searchKey(query) in searchKey(it) }, query)
        }
    }

    @Test
    fun `a summary names two topics and counts the rest`() {
        val topics =
            listOf(
                Topic("SPORT", "Спорт", "Sport", 10),
                Topic("MUSIC", "Музика", "Music", 10),
                Topic("FILM_TV", "Филм и серије", "Film & TV", 10),
                Topic("FOOD", "Храна и пиће", "Food & drink", 10),
            )
        assertEquals("Све", topicsSummary(emptyList(), topics, Language.SERBIAN_CYRILLIC, "Све"))
        assertEquals("Спорт, Музика", topicsSummary(listOf("SPORT", "MUSIC"), topics, Language.SERBIAN_CYRILLIC, "Све"))
        assertEquals(
            "Sport, Muzika +2",
            topicsSummary(listOf("SPORT", "MUSIC", "FILM_TV", "FOOD"), topics, Language.SERBIAN_LATIN, "Sve"),
        )
        // An old server sends no Croatian names: Croatian reads the Serbian, in Latin.
        assertEquals("Sport, Muzika", topicsSummary(listOf("SPORT", "MUSIC"), topics, Language.CROATIAN, "Sve"))
    }
}
