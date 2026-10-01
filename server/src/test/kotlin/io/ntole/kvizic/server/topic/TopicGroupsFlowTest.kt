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

            assertEquals(listOf("KNOWLEDGE", "ENTERTAINMENT", "SPORT", "REGION"), list.groups.map { it.id })
            assertEquals(listOf("Знање", "Забава", "Спорт", "Наши простори"), list.groups.map { it.nameSr })
            assertEquals(
                mapOf(
                    "GEOGRAPHY" to "KNOWLEDGE",
                    "HISTORY" to "KNOWLEDGE",
                    "SPORT" to "SPORT",
                    "MUSIC" to "ENTERTAINMENT",
                    "FILM_TV" to "ENTERTAINMENT",
                    "SCIENCE" to "KNOWLEDGE",
                    "LANGUAGE" to "KNOWLEDGE",
                    "LOCAL" to "REGION",
                ),
                list.topics.associate { it.id to it.groupId },
            )
        }
}
