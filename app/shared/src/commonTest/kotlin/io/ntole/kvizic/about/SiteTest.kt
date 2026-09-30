package io.ntole.kvizic.about

import io.ntole.kvizic.language.Language
import kotlin.test.Test
import kotlin.test.assertEquals

/** Where the game's links to its site go. */
class SiteTest {
    @Test
    fun `each page is under the one base in Serbian and under en in English`() {
        assertEquals("https://ntole.com/kvizic/privacy.html", Site.url(SitePage.PRIVACY, Language.SERBIAN_CYRILLIC))
        assertEquals("https://ntole.com/kvizic/terms.html", Site.url(SitePage.TERMS, Language.SERBIAN_LATIN))
        assertEquals("https://ntole.com/kvizic/en/delete.html", Site.url(SitePage.DELETE_ACCOUNT, Language.ENGLISH))
        assertEquals("https://ntole.com/kvizic/en/contact.html", Site.url(SitePage.CONTACT, Language.ENGLISH))
    }

    @Test
    fun `the version shows its build number when there is one`() {
        assertEquals("0.1.0 (100)", versionText(AppVersion("0.1.0", 100)))
        assertEquals("0.1.0", versionText(AppVersion("0.1.0", null)))
    }
}
