package io.ntole.kvizic.region

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ntole.kvizic.core.domain.analytics.Analytics
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.region.Country
import io.ntole.kvizic.core.domain.topic.GetTopics
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The country the player plays from, as [region] keeps it for the device: detected at a first launch, then the
 * one picked on the Settings screen. A country picked is reported to [analytics], by its name, and the topics
 * are read again ([getTopics]), since a topic's count is of the questions a player of the country could be asked
 * and thin topics are greyed by it.
 *
 * A country picked is the next request's, so the next seat taken plays by it; Settings is reached from Home,
 * never from a room, so no game changes under the player.
 */
class CountryViewModel(
    private val region: RegionStore,
    private val analytics: Analytics,
    private val getTopics: GetTopics,
) : ViewModel() {
    val country: StateFlow<Country> = region.country

    /** Plays from [country] from now on, and keeps it for the next launch. */
    fun select(country: Country) {
        if (country == region.country.value) return
        region.selectCountry(country)
        analytics.track(AnalyticsEvent.COUNTRY_CHANGED, mapOf(AnalyticsProperty.COUNTRY to country.name))
        viewModelScope.launch {
            try {
                getTopics()
            } catch (ignored: KvizicException) {
                // The counts read before stay until a screen that shows topics reads them again, as each does.
            }
        }
    }
}
