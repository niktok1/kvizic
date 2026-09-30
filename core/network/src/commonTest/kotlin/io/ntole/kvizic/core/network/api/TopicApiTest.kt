package io.ntole.kvizic.core.network.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.network.BASE_URL
import io.ntole.kvizic.core.network.KvizicHttpClient
import io.ntole.kvizic.core.network.KvizicJson
import io.ntole.kvizic.core.network.jsonHeaders
import io.ntole.kvizic.core.network.storeHolding
import io.ntole.kvizic.core.topic.TopicDto
import io.ntole.kvizic.core.topic.TopicListDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TopicApiTest {
    @Test
    fun `the topics are read from their path with no session needed`() =
        runTest {
            val listed =
                TopicListDto(listOf(TopicDto(id = "MUSIC", nameSr = "Музика", nameEn = "Music", questionCount = 9)))
            val paths = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    paths += request.url.encodedPath
                    respond(KvizicJson.encodeToString(listed), HttpStatusCode.OK, jsonHeaders)
                }

            val topics = TopicApi(KvizicHttpClient.create(BASE_URL, storeHolding(null), engine)).all()

            assertEquals(listed, topics)
            assertEquals(listOf(KvizicApi.Paths.TOPICS), paths)
        }
}
