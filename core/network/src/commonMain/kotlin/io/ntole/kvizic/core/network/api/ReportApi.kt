package io.ntole.kvizic.core.network.api

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.report.ReportQuestionRequest

/** A player's reports of the questions they were asked, with the session's bearer. */
public class ReportApi(
    private val client: HttpClient,
) {
    /** Reports a question, answered 204. */
    public suspend fun report(request: ReportQuestionRequest) {
        client.post(KvizicApi.Paths.REPORTS) { setBody(request) }
    }
}
