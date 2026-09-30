package io.ntole.kvizic.core.data.topic

import io.ntole.kvizic.core.data.mapper.runApi
import io.ntole.kvizic.core.data.mapper.toDomain
import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.core.domain.topic.TopicRepository
import io.ntole.kvizic.core.network.api.TopicApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reads the topics through [runApi] alone, never `withSessionRecovery`: the list needs no session, so
 * there is none to ensure or recover, and a read can never mint a guest. Kept in memory, in the order the
 * server sent them, until the next read.
 */
public class DefaultTopicRepository(
    private val api: TopicApi,
) : TopicRepository {
    private val read = MutableStateFlow<List<Topic>>(emptyList())

    override val topics: StateFlow<List<Topic>> = read.asStateFlow()

    override suspend fun refresh(): List<Topic> {
        val listed = runApi { api.all() }.topics.map { it.toDomain() }
        read.value = listed
        return listed
    }
}
