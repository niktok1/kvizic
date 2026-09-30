package io.ntole.kvizic.server.topic

import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.server.plugins.ApiFailure

/** A topic the rules take: an id of `A`-`Z`, `0`-`9` and `_`, and two names of one line each, trimmed. */
data class CheckedTopic(
    val id: String,
    val nameSr: String,
    val nameEn: String,
)

private val TOPIC_ID = Regex("^[A-Z0-9_]{1,${KvizicApi.Limits.MAX_ID_LENGTH}}$")

/** [id], [nameSr] and [nameEn] checked, or [ApiFailure.validation]: the moderation app checks first. */
internal fun checkedTopic(
    id: String,
    nameSr: String,
    nameEn: String,
): CheckedTopic {
    if (!TOPIC_ID.matches(
            id,
        )
    ) {
        throw ApiFailure.validation("a topic's id is 1 to ${KvizicApi.Limits.MAX_ID_LENGTH} of A-Z, 0-9 and _")
    }
    return CheckedTopic(id, checkedName("nameSr", nameSr), checkedName("nameEn", nameEn))
}

private fun checkedName(
    field: String,
    raw: String,
): String {
    val name = raw.trim()
    if (name.isEmpty()) throw ApiFailure.validation("$field is blank")
    if (name.length > KvizicApi.Limits.MAX_TOPIC_NAME_LENGTH) {
        throw ApiFailure.validation("$field is over ${KvizicApi.Limits.MAX_TOPIC_NAME_LENGTH} characters")
    }
    if (!isOneLine(name)) throw ApiFailure.validation("$field is not one line")
    return name
}

/** No control character, nor the line and paragraph separators. */
internal fun isOneLine(text: String): Boolean = text.none { it.isISOControl() || it == ' ' || it == ' ' }
