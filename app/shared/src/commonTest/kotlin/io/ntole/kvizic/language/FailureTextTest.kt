package io.ntole.kvizic.language

import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.GameError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** What a screen says of a failure, in a few words. */
class FailureTextTest {
    private val strings = SerbianCyrillicStrings

    @Test
    fun `offline says so`() {
        assertEquals("Нема интернет везе.", strings.failureText(CoreError.NETWORK))
    }

    @Test
    fun `a rate limit says how long to wait when the server said`() {
        assertEquals("Превише покушаја. Сачекај 42 сек.", strings.failureText(CoreError.RATE_LIMITED, 42.seconds))
        assertEquals("Превише покушаја. Сачекај мало.", strings.failureText(CoreError.RATE_LIMITED))
    }

    @Test
    fun `anything else asks to try again`() {
        listOf(CoreError.SERVER, CoreError.UNKNOWN, GameError.LOBBY_FULL).forEach { error ->
            assertEquals(strings.somethingWrong, strings.failureText(error), "$error")
        }
    }
}
