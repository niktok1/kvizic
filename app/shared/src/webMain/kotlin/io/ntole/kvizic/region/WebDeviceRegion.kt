package io.ntole.kvizic.region

import js.array.toList
import web.navigator.navigator

/**
 * A browser's [RegionHints]: the languages it is set to prefer, first first (`navigator.languages`), and the
 * region the first of them names as the locale's, which is all a page can know of where it is.
 */
internal object WebDeviceRegion : DeviceRegion {
    override fun hints(): RegionHints {
        val languages = navigator.languages.toList().map { it.toString() }
        return RegionHints(
            localeCountry = languages.firstOrNull()?.let { LanguageTag.parse(it)?.region },
            preferredLanguages = languages,
        )
    }
}
