package io.ntole.kvizic.about

import io.ntole.kvizic.language.Language

/**
 * The game's website, where its legal pages are: the privacy policy, the terms, deleting an account, and
 * contact, in Serbian at the site's root and in every other language under its tag, `/en/`, `/hr/`, `/bs/`,
 * `/sl/` and `/mk/`. The site is the game's own, on a subdomain of the developer's domain (`kvizic-site`,
 * published from `site/`), since the domain's root is published from WYR's repository, which this one never
 * changes.
 */
object Site {
    /** The one base the game's every link to the site is made from. */
    const val BASE_URL: String = "https://kvizic.ntole.com"

    /** Where [page] is, in the language the game is shown in: Serbian in either script is the Serbian page. */
    fun url(
        page: SitePage,
        language: Language,
    ): String =
        when (language) {
            Language.SERBIAN_CYRILLIC, Language.SERBIAN_LATIN -> {
                "$BASE_URL/${page.path}"
            }

            Language.CROATIAN, Language.BOSNIAN, Language.SLOVENIAN, Language.MACEDONIAN, Language.ENGLISH -> {
                "$BASE_URL/${language.tag}/${page.path}"
            }
        }
}

/** The site's pages the game links to, each its file under the site's root and under each language's tag. */
enum class SitePage(
    val path: String,
) {
    PRIVACY("privacy.html"),
    TERMS("terms.html"),
    DELETE_ACCOUNT("delete.html"),
    CONTACT("contact.html"),
}
