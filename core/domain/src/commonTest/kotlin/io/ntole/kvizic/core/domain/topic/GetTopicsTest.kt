package io.ntole.kvizic.core.domain.topic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The topics are read again every time they are asked for, and need no session. */
class GetTopicsTest {
    @Test
    fun `each read asks the server again and returns what it listed`() =
        runTest {
            val topics = FakeTopics()
            val getTopics = GetTopics(topics)

            assertEquals(listOf(GEOGRAPHY), getTopics())
            assertEquals(listOf(GEOGRAPHY), getTopics())

            assertEquals(2, topics.reads)
        }

    private class FakeTopics : TopicRepository {
        var reads = 0
        private val read = MutableStateFlow<List<Topic>>(emptyList())

        override val topics: StateFlow<List<Topic>> = read

        override suspend fun refresh(): List<Topic> {
            reads++
            read.value = listOf(GEOGRAPHY)
            return read.value
        }
    }

    private companion object {
        val GEOGRAPHY = Topic(id = "GEOGRAPHY", nameSr = "Географија", nameEn = "Geography", questionCount = 12)
    }
}
