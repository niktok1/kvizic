package io.ntole.kvizic.core.region

import io.ntole.kvizic.core.api.KvizicApi
import kotlinx.serialization.Serializable

/**
 * Where a player plays from, which decides the questions they are asked; their language decides only how
 * those read (`ContentLanguages`). A question scoped to countries is asked only when every player's country
 * is one of them, so a room of mixed countries gets the common questions alone. A client names it by name in
 * [KvizicApi.Headers.COUNTRY] on every REST call.
 *
 * On the server, no header at all (a 1.0.x client) counts as [RS]. [UNKNOWN], a newer client's country this
 * build has no entry for, is a country no question is scoped to: its player gets the common questions only.
 */
@Serializable
public enum class Country {
    RS,
    HR,
    BA,
    SI,
    MK,
    ME,
    UNKNOWN,
    ;

    public companion object {
        /**
         * The country [value] names, in any case, trimmed: null for no value or a blank one, which is no
         * country said at all, and [UNKNOWN] for a name this build does not know.
         */
        public fun parse(value: String?): Country? {
            val trimmed = value?.trim()
            if (trimmed.isNullOrEmpty()) return null
            return entries.firstOrNull { it.name.equals(trimmed, ignoreCase = true) } ?: UNKNOWN
        }
    }
}
