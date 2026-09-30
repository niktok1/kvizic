package io.ntole.kvizic.core.data.topic

import io.ktor.http.HttpStatusCode
import io.ntole.kvizic.core.data.BASE_URL
import io.ntole.kvizic.core.data.FakeServer
import io.ntole.kvizic.core.data.storeHolding
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.network.KvizicHttpClient
import io.ntole.kvizic.core.network.api.TopicApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The topics through the real client, against [FakeServer]: no session needed, none minted. */
class DefaultTopicRepositoryTest {
    private val server = FakeServer()
    private val topics =
        DefaultTopicRepository(TopicApi(KvizicHttpClient.create(BASE_URL, storeHolding(null), server.engine)))

    @Test
    fun `the topics are read in the server's order and kept`() =
        runTest {
            val read = topics.refresh()

            assertEquals(
                listOf(
                    Topic(id = "GEOGRAPHY", nameSr = "Географија", nameEn = "Geography", questionCount = 12),
                    Topic(id = "HISTORY", nameSr = "Историја", nameEn = "History", questionCount = 9),
                    Topic(id = "MUSIC", nameSr = "Музика", nameEn = "Music", questionCount = 0),
                ),
                read,
            )
            assertEquals(read, topics.topics.value)
            assertEquals(listOf<String?>(null), server.topicsSentAs, "no bearer, and no guest minted for one")
            assertEquals(0, server.guestsMinted)
        }

    @Test
    fun `a read that fails leaves the topics read before`() =
        runTest {
            val before = topics.refresh()
            server.refuseTopicsWith = HttpStatusCode.ServiceUnavailable to ErrorCode.INTERNAL

            val failure = assertFailsWith<KvizicException> { topics.refresh() }

            assertEquals(CoreError.SERVER, failure.error)
            assertEquals(before, topics.topics.value)
        }
}
