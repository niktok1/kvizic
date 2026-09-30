package io.ntole.kvizic.core.network.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.topic.TopicListDto

public class TopicApi(
    private val client: HttpClient,
) {
    /**
     * Every topic the server has, with how many approved questions each holds. Needs no session: the
     * server reads none, so the Auth plugin's bearer, when there is one, plays no part, and the route never
     * answers 401.
     */
    public suspend fun all(): TopicListDto = client.get(KvizicApi.Paths.TOPICS).body()
}
