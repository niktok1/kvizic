package io.ntole.kvizic.server.plugins

import io.ktor.http.Parameters
import io.ntole.kvizic.core.api.KvizicApi

/**
 * How many items a request asks for with [KvizicApi.Query.LIMIT], held between 1 and
 * [KvizicApi.Limits.MAX_PAGE_SIZE], or [KvizicApi.Limits.DEFAULT_PAGE_SIZE] when it asks for no number.
 * A value that is not a number is the client's mistake. Every route that takes a limit reads it here,
 * so they all hold it to the same bounds.
 */
fun Parameters.pageLimit(): Int =
    this[KvizicApi.Query.LIMIT]
        ?.let { raw -> raw.toIntOrNull() ?: throw ApiFailure.validation("limit must be a number: $raw") }
        ?.coerceIn(1, KvizicApi.Limits.MAX_PAGE_SIZE)
        ?: KvizicApi.Limits.DEFAULT_PAGE_SIZE
