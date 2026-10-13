package io.ntole.kvizic.core.data.mapper

import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.core.domain.topic.TopicGroup
import io.ntole.kvizic.core.topic.TopicDto
import io.ntole.kvizic.core.topic.TopicGroupDto
import kotlin.test.Test
import kotlin.test.assertEquals

/** A topic and a group come over with their names in every language the server sends, and none from an old one. */
class TopicMapperTest {
    @Test
    fun `a topic keeps its names by tag`() {
        val names = mapOf("hr" to "Zemljopis", "mk" to "Географија", "en" to "Geography")
        assertEquals(
            Topic("GEO", "Географија", "Geography", 4, groupId = "KNOWLEDGE", names = names),
            TopicDto("GEO", "Географија", "Geography", 4, groupId = "KNOWLEDGE", names = names).toDomain(),
        )
    }

    @Test
    fun `a group keeps its names by tag`() {
        val names = mapOf("sl" to "Znanje")
        assertEquals(
            TopicGroup("KNOWLEDGE", "Знање", "Knowledge", names = names),
            TopicGroupDto("KNOWLEDGE", "Знање", "Knowledge", names = names).toDomain(),
        )
    }

    @Test
    fun `an old server's topic has no names but its Serbian and English`() {
        assertEquals(emptyMap<String, String>(), TopicDto("GEO", "Географија", "Geography").toDomain().names)
        assertEquals(emptyMap<String, String>(), TopicGroupDto("KNOWLEDGE", "Знање", "Knowledge").toDomain().names)
    }
}
