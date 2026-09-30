package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.api.KvizicApi
import java.security.SecureRandom

/**
 * A lobby's code: [KvizicApi.Limits.LOBBY_CODE_LENGTH] digits, which read the same on a Cyrillic and a
 * Latin keyboard, where letters would not (А, В, Е, К, М, Н, О, Р, С, Т and Х all look Latin). Made at
 * random, so guessing one takes about a million tries, which the per-address lockout bounds.
 */
object LobbyCode {
    private val random = SecureRandom()

    /** A fresh code that [taken] says is free. */
    fun generate(taken: (String) -> Boolean): String {
        repeat(MAX_TRIES) {
            val code = buildString { repeat(KvizicApi.Limits.LOBBY_CODE_LENGTH) { append('0' + random.nextInt(10)) } }
            if (!taken(code)) return code
        }
        error("no free lobby code after $MAX_TRIES tries")
    }

    /** [raw] as a code, spaces and dashes dropped, or null when it cannot be one. */
    fun parse(raw: String): String? {
        val digits = raw.filterNot { it == ' ' || it == '-' || it == ' ' }
        return digits.takeIf { it.length == KvizicApi.Limits.LOBBY_CODE_LENGTH && it.all(Char::isDigit) }
    }

    private const val MAX_TRIES = 64
}
