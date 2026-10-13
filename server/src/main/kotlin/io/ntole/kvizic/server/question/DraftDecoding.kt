package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.question.ImportQuestionsRequest
import io.ntole.kvizic.core.question.ImportRefusalDto
import io.ntole.kvizic.core.question.LocalizeQuestionsRequest
import io.ntole.kvizic.server.plugins.ServerJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * The server's `Json` coerces nothing, and a list's element could not be coerced anyway: one draft naming a
 * country this server does not know, or otherwise not of the draft's shape, would fail its whole request's
 * decoding. So a request comes in with its questions left as JSON, and each is read on its own: one that
 * cannot be is refused by itself, as the rules refuse one, and the rest go on.
 */

/** [ImportQuestionsRequest] as it comes, its drafts left for [decodedEach]; field for field the same. */
@Serializable
internal class ImportBody(
    val batch: String,
    val questions: List<JsonElement>,
)

/** [LocalizeQuestionsRequest] as it comes, its questions left for [decodedEach]; field for field the same. */
@Serializable
internal class LocalizeBody(
    val questions: List<JsonElement>,
)

/** What [decodedEach] read: the [items] it could, in order, and a refusal for each of the rest. */
internal class Decoded<T>(
    val items: List<T>,
    val refused: List<ImportRefusalDto>,
)

/**
 * Each of these as [serializer] reads it, one by one. One that it cannot read is refused by the `key` it
 * holds, empty when it holds none that is text, with the decoder's first line of why.
 */
internal fun <T> List<JsonElement>.decodedEach(serializer: KSerializer<T>): Decoded<T> {
    val items = mutableListOf<T>()
    val refused = mutableListOf<ImportRefusalDto>()
    forEach { element ->
        try {
            items += ServerJson.decodeFromJsonElement(serializer, element)
        } catch (unreadable: SerializationException) {
            val why =
                unreadable.message
                    ?.lineSequence()
                    ?.firstOrNull()
                    ?.take(MAX_SHOWN_WHY)
                    .orEmpty()
            refused += ImportRefusalDto(keyOf(element), "unreadable: $why")
        }
    }
    return Decoded(items, refused)
}

/** The `key` of [element], as long as an import key can be, or empty. */
private fun keyOf(element: JsonElement): String =
    ((element as? JsonObject)?.get(KEY) as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.content
        ?.take(MAX_SHOWN_KEY)
        .orEmpty()

private const val KEY = "key"

/** How long a refused key is shown back: an import key's length, whatever was sent. */
internal const val MAX_SHOWN_KEY = 64

private const val MAX_SHOWN_WHY = 200
