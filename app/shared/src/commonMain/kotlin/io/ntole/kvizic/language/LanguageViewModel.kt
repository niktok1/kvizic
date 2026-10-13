package io.ntole.kvizic.language

import androidx.lifecycle.ViewModel
import io.ntole.kvizic.core.domain.analytics.Analytics
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.region.RegionStore
import kotlinx.coroutines.flow.StateFlow

/**
 * The language the game is shown in, as [region] keeps it for the device: a first launch's, the device's own
 * language when the game offers it and else its country's (Serbian standing in for one not offered yet, until a
 * build offers it), or the one the player picked since; an install from before keeps the one it showed. A
 * language picked is reported to [analytics], by its tag.
 *
 * The one [region] holds is also the one every socket's hello names, so the server sends the questions in it.
 */
class LanguageViewModel(
    private val region: RegionStore,
    private val analytics: Analytics,
) : ViewModel() {
    val language: StateFlow<Language> = region.language

    /** Shows the game in [language] at once, then keeps it for the next launch, as [RegionStore] does. */
    fun select(language: Language) {
        if (language == region.language.value) return
        region.selectLanguage(language)
        analytics.track(AnalyticsEvent.LANGUAGE_CHANGED, mapOf(AnalyticsProperty.LANGUAGE to language.tag))
    }

    companion object {
        /** The key the language is kept under, [RegionStore.LANGUAGE_KEY]. */
        const val KEY: String = RegionStore.LANGUAGE_KEY
    }
}
