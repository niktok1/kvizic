package io.ntole.kvizic.language

import io.ntole.kvizic.core.region.ContentLanguages
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Each language reads the content language the wire maps its tag to, so the text the server picks for the
 * tag a client says in `hello` is the one the client looks for first.
 */
class ContentTagTest {
    @Test
    fun `each language's content tag is the wire's for its tag`() {
        Language.entries.forEach { language ->
            assertEquals(ContentLanguages.of(language.tag), language.contentTag, "$language")
        }
    }
}
