package io.ntole.kvizic.server.question

import io.ntole.kvizic.server.plugins.ApiFailure

/** Where a page of the bank ended: its last question's creation time, then its id. Opaque to clients. */
data class QuestionCursor(
    val createdAt: Long,
    val id: String,
) {
    fun encode(): String = "$createdAt.$id"

    companion object {
        /** [raw] as a cursor, or [ApiFailure.validation]: only a cursor this server sent is one. */
        fun parse(raw: String): QuestionCursor {
            val dot = raw.indexOf('.')
            val createdAt = raw.take(dot.coerceAtLeast(0)).toLongOrNull()
            val id = raw.substring(dot + 1)
            if (dot <= 0 || createdAt == null || id.isEmpty() ||
                id.length > MAX_ID
            ) {
                throw ApiFailure.validation("no such cursor")
            }
            return QuestionCursor(createdAt, id)
        }

        private const val MAX_ID = 36
    }
}
