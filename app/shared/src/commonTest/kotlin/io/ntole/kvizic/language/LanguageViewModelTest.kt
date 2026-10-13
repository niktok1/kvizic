package io.ntole.kvizic.language

import io.ntole.kvizic.analytics.RecordingAnalytics
import io.ntole.kvizic.analytics.RecordingAnalytics.Recorded
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.core.network.InMemoryTokenStorage
import io.ntole.kvizic.core.network.TokenStorage
import io.ntole.kvizic.region.DeviceRegion
import io.ntole.kvizic.region.RegionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The language the game is shown in, as the device keeps it. Each store here is an install from before, which
 * keeps the language it showed: what a first launch picks is `RegionStoreTest`'s.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanguageViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val analytics = RecordingAnalytics()

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
    fun `a device that never picked a language shows Serbian Cyrillic`() =
        runTest(dispatcher) {
            assertEquals(Language.SERBIAN_CYRILLIC, viewModel(InMemoryTokenStorage()).language.value)
        }

    @Test
    fun `a language picked is kept on the device under its own key`() =
        runTest(dispatcher) {
            val storage = InMemoryTokenStorage()
            val viewModel = viewModel(storage)

            viewModel.select(Language.SERBIAN_LATIN)
            testScheduler.runCurrent()

            assertEquals("sr-Latn", storage.read("kvizic.language"))
        }

    @Test
    fun `the next launch opens in the language picked last`() =
        runTest(dispatcher) {
            Language.entries.forEach { language ->
                val storage = InMemoryTokenStorage()
                // English first, so each language is a change, Serbian Cyrillic included.
                viewModel(storage).apply {
                    select(Language.ENGLISH)
                    select(language)
                }
                testScheduler.runCurrent()

                assertEquals(language, viewModel(storage).language.value)
            }
        }

    /** The write may take a while, a phone's disk being slow, and the screen does not wait for it. */
    @Test
    fun `a language picked shows at once before it is kept`() =
        runTest(dispatcher) {
            val storage = StuckStorage()
            val viewModel = viewModel(storage)

            viewModel.select(Language.ENGLISH)

            assertEquals(Language.ENGLISH, viewModel.language.value)
            testScheduler.runCurrent()
            assertEquals(Language.ENGLISH, viewModel.language.value)
            assertEquals<List<String>?>(listOf("en"), storage.writing[RegionStore.LANGUAGE_KEY])
        }

    @Test
    fun `a language that cannot be kept is still this run's`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FailingStorage())

            viewModel.select(Language.ENGLISH)
            testScheduler.runCurrent()

            assertEquals(Language.ENGLISH, viewModel.language.value)
        }

    @Test
    fun `picking the language shown writes nothing`() =
        runTest(dispatcher) {
            val storage = InMemoryTokenStorage()
            viewModel(storage).select(Language.SERBIAN_CYRILLIC)
            testScheduler.runCurrent()

            assertNull(storage.read("kvizic.language"))
        }

    @Test
    fun `a kept language this build does not know shows Serbian Cyrillic`() =
        runTest(dispatcher) {
            val storage = InMemoryTokenStorage()
            storage.write("kvizic.language", "de")

            assertEquals(Language.SERBIAN_CYRILLIC, viewModel(storage).language.value)
        }

    /** A language picked, by its tag, for the analytics; the one shown already is none. */
    @Test
    fun `a language picked is reported by its tag`() =
        runTest(dispatcher) {
            val viewModel = viewModel(InMemoryTokenStorage())

            viewModel.select(Language.SERBIAN_CYRILLIC)
            viewModel.select(Language.ENGLISH)

            assertEquals(
                listOf(Recorded(AnalyticsEvent.LANGUAGE_CHANGED, mapOf(AnalyticsProperty.LANGUAGE to "en"))),
                analytics.events,
            )
        }

    /** The language picked is the one the server is told of, in the next socket's hello. */
    @Test
    fun `a language picked is the one the next hello names`() =
        runTest(dispatcher) {
            val region = region(InMemoryTokenStorage())

            LanguageViewModel(region, analytics).select(Language.MACEDONIAN)

            assertEquals("mk", region.currentLanguageTag())
        }

    private fun TestScope.viewModel(storage: TokenStorage) = LanguageViewModel(region(storage), analytics)

    private fun TestScope.region(storage: TokenStorage) =
        RegionStore(storage, DeviceRegion.None, installedBefore = { true }, scope = backgroundScope)

    /** A storage whose every write waits for good, as one stuck on a slow disk would, by key. */
    private class StuckStorage : TokenStorage {
        val writing = mutableMapOf<String, MutableList<String>>()

        override fun read(key: String): String? = null

        override suspend fun write(
            key: String,
            value: String,
        ) {
            writing.getOrPut(key) { mutableListOf() } += value
            CompletableDeferred<Unit>().await()
        }

        override suspend fun remove(key: String) = Unit
    }

    /** A storage whose every write fails, as a full disk's does. */
    private class FailingStorage : TokenStorage {
        override fun read(key: String): String? = null

        override suspend fun write(
            key: String,
            value: String,
        ): Unit = throw IllegalStateException("the disk is full")

        override suspend fun remove(key: String) = Unit
    }
}
