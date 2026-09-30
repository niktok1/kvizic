package io.ntole.kvizic.core.network

import kotlinx.serialization.json.Json

/**
 * The client's `Json` for the REST API. The socket has its own, `ProtocolJson` in `:core`.
 *
 * **Do not change these flags casually.** `coerceInputValues` is the client's half of the wire enum rule:
 * without it, the `UNKNOWN` defaults on `ErrorCode`, `NameSource` and every other growable enum do nothing,
 * and the first value a server adds breaks every installed client. `ignoreUnknownKeys` is the same bargain
 * for added fields. Topics lean on neither: they are server data, plain ids on the wire, so a new one is
 * only an id this build has no name for.
 */
public val KvizicJson: Json =
    Json {
        // The server added an enum value this build has never heard of: the property's default, not a throw.
        coerceInputValues = true

        // The server added a field this build has never heard of: skipped, not a throw.
        ignoreUnknownKeys = true

        // Keeps payloads small; every DTO default is a value the server may leave out.
        explicitNulls = false
    }
