package io.ntole.kvizic.core.data.lobby

import io.ntole.kvizic.core.domain.region.ContentLanguages
import kotlin.test.Test
import kotlin.test.assertEquals
import io.ntole.kvizic.core.region.ContentLanguages as WireLanguages

/** The domain's copy of the wire's content languages, which the client reads the server's names in, is the wire's. */
class ContentLanguagesTest {
    @Test
    fun `the content languages are the wire's in its order`() {
        assertEquals(WireLanguages.ALL, ContentLanguages.ALL)
        assertEquals(
            listOf(
                WireLanguages.SERBIAN,
                WireLanguages.CROATIAN,
                WireLanguages.BOSNIAN,
                WireLanguages.SLOVENIAN,
                WireLanguages.MACEDONIAN,
            ),
            listOf(
                ContentLanguages.SERBIAN,
                ContentLanguages.CROATIAN,
                ContentLanguages.BOSNIAN,
                ContentLanguages.SLOVENIAN,
                ContentLanguages.MACEDONIAN,
            ),
        )
    }

    @Test
    fun `each language looks for a word in the wire's order`() {
        (WireLanguages.ALL + listOf("sr-Latn", "en", "")).forEach { lang ->
            assertEquals(WireLanguages.chain(lang), ContentLanguages.chain(lang), lang)
        }
    }
}
