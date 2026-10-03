package io.ntole.kvizic.server.topic

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.topic.TopicListDto
import io.ntole.kvizic.server.runTestServer
import kotlin.test.Test
import kotlin.test.assertEquals

/** The topics' list names the groups a picker lists them under, in their order, and each topic's group. */
class TopicGroupsFlowTest {
    @Test
    fun `the topics come with their groups`() =
        runTestServer("topic-groups") { client, _ ->
            val list: TopicListDto = client.get(KvizicApi.Paths.TOPICS).body()

            assertEquals(listOf("KNOWLEDGE", "ENTERTAINMENT", "SPORT"), list.groups.map { it.id })
            assertEquals(listOf("Знање", "Забава", "Спорт"), list.groups.map { it.nameSr })
            // Наши простори went, a region rather than a subject, and its food has a topic of its own (V6).
            assertEquals(
                listOf(
                    "GEOGRAPHY" to "KNOWLEDGE",
                    "HISTORY" to "KNOWLEDGE",
                    "SPORT" to "SPORT",
                    "MUSIC" to "ENTERTAINMENT",
                    "FILM_TV" to "ENTERTAINMENT",
                    "SCIENCE" to "KNOWLEDGE",
                    "LANGUAGE" to "KNOWLEDGE",
                    "FOOD" to "ENTERTAINMENT",
                    // Seven more subjects (V7).
                    "NATURE" to "KNOWLEDGE",
                    "ART" to "KNOWLEDGE",
                    "MYTHOLOGY" to "KNOWLEDGE",
                    "BODY" to "KNOWLEDGE",
                    "VEHICLES" to "KNOWLEDGE",
                    "GAMES" to "ENTERTAINMENT",
                    "COMICS" to "ENTERTAINMENT",
                    // Nine more (V9).
                    "TECH" to "KNOWLEDGE",
                    "SPACE" to "KNOWLEDGE",
                    "MATH" to "KNOWLEDGE",
                    "CAPITALS" to "KNOWLEDGE",
                    "LANDMARKS" to "KNOWLEDGE",
                    "CUSTOMS" to "ENTERTAINMENT",
                    "CELEBRITIES" to "ENTERTAINMENT",
                    "FOOTBALL" to "SPORT",
                    "BASKETBALL" to "SPORT",
                ),
                list.topics.map { it.id to it.groupId },
            )
            assertEquals("Кошарка", list.topics.last().nameSr)
        }
}
