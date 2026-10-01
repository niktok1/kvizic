package io.ntole.kvizic.core.domain.moderation

/**
 * The server's admin token, as the moderator typed it: what every moderation call carries, and only that
 * call. Nothing stores it, and [toString] shows none of it, so a log line or a state's text never does.
 */
public class AdminToken private constructor(
    public val value: String,
) {
    override fun equals(other: Any?): Boolean = other is AdminToken && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "AdminToken(…)"

    public companion object {
        /** Longer than any token a server takes, which `openssl rand -hex 32` makes 64 long. */
        public const val MAX_LENGTH: Int = 256

        /**
         * [text], trimmed, when it can be a token: 1 to [MAX_LENGTH] of visible ASCII, which is all a request
         * header can carry; `null` otherwise, so nothing is sent for it.
         */
        public fun of(text: String): AdminToken? {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return null
            if (trimmed.any { it.code !in VISIBLE_ASCII }) return null
            return AdminToken(trimmed)
        }

        private val VISIBLE_ASCII = 0x21..0x7E
    }
}
