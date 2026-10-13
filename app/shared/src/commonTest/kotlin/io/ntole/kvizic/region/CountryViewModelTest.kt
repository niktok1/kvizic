package io.ntole.kvizic.region

import io.ntole.kvizic.analytics.RecordingAnalytics
import io.ntole.kvizic.analytics.RecordingAnalytics.Recorded
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.region.Country
import io.ntole.kvizic.core.domain.topic.GetTopics
import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.core.domain.topic.TopicGroup
import io.ntole.kvizic.core.domain.topic.TopicRepository
import io.ntole.kvizic.core.network.InMemoryTokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The country picked on the Settings screen: kept, reported, and the topics' counts read again for it. */
@OptIn(ExperimentalCoroutinesApi::class)
class CountryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val analytics = RecordingAnalytics()
    private val topics = CountingTopics()

    @BeforeTest
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main, which has no implementation under test.
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a country picked is the player's and is reported by its name and the topics are read again`() =
        runTest(dispatcher) {
            val region = region()
            val viewModel = CountryViewModel(region, analytics, GetTopics(topics))

            viewModel.select(Country.MK)
            testScheduler.advanceUntilIdle()

            assertEquals(Country.MK, viewModel.country.value)
            assertEquals(Country.MK, region.currentCountry())
            assertEquals(
                listOf(Recorded(AnalyticsEvent.COUNTRY_CHANGED, mapOf(AnalyticsProperty.COUNTRY to "MK"))),
                analytics.events,
            )
            assertEquals(1, topics.reads)
        }

    @Test
    fun `picking the country already played from does nothing`() =
        runTest(dispatcher) {
            val viewModel = CountryViewModel(region(), analytics, GetTopics(topics))

            viewModel.select(Country.RS)
            testScheduler.advanceUntilIdle()

            assertEquals(emptyList<Recorded>(), analytics.events)
            assertEquals(0, topics.reads)
        }

    /** The counts read before stay: a screen that shows topics reads them again as it opens. */
    @Test
    fun `a read of the topics that fails changes nothing`() =
        runTest(dispatcher) {
            topics.failing = true
            val viewModel = CountryViewModel(region(), analytics, GetTopics(topics))

            viewModel.select(Country.HR)
            testScheduler.advanceUntilIdle()

            assertEquals(Country.HR, viewModel.country.value)
            assertEquals(1, topics.reads)
        }

    private fun TestScope.region() =
        RegionStore(
            InMemoryTokenStorage(),
            DeviceRegion { RegionHints("rs") },
            installedBefore = { true },
            scope = backgroundScope,
        )

    private class CountingTopics : TopicRepository {
        var reads = 0
        var failing = false

        override val topics: StateFlow<List<Topic>> = MutableStateFlow(emptyList())
        override val groups: StateFlow<List<TopicGroup>> = MutableStateFlow(emptyList())

        override suspend fun refresh(): List<Topic> {
            reads++
            if (failing) throw KvizicException(CoreError.NETWORK)
            return emptyList()
        }
    }
}
