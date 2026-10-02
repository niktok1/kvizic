package io.ntole.kvizic.sound

import io.ntole.kvizic.analytics.RecordingAnalytics
import io.ntole.kvizic.analytics.RecordingAnalytics.Recorded
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.core.network.InMemoryTokenStorage
import io.ntole.kvizic.core.network.TokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Whether the game makes sound, as the device keeps it. */
@OptIn(ExperimentalCoroutinesApi::class)
class SoundViewModelTest {
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
    fun `a device that never chose has sound on`() {
        assertTrue(SoundViewModel(InMemoryTokenStorage(), analytics).enabled.value)
    }

    @Test
    fun `sound turned off is kept and the next launch is silent`() =
        runTest(dispatcher) {
            val storage = InMemoryTokenStorage()
            SoundViewModel(storage, analytics).setEnabled(false)
            testScheduler.advanceUntilIdle()

            assertEquals("off", storage.read("kvizic.sound"))
            assertFalse(SoundViewModel(storage, analytics).enabled.value)
        }

    @Test
    fun `sound turned on again is kept too`() =
        runTest(dispatcher) {
            val storage = InMemoryTokenStorage()
            SoundViewModel(storage, analytics).apply {
                setEnabled(false)
                setEnabled(true)
            }
            testScheduler.advanceUntilIdle()

            assertTrue(SoundViewModel(storage, analytics).enabled.value)
        }

    @Test
    fun `a choice shows at once and is this run's when it cannot be kept`() =
        runTest(dispatcher) {
            val viewModel = SoundViewModel(FailingStorage(), analytics)

            viewModel.setEnabled(false)
            assertFalse(viewModel.enabled.value)
            testScheduler.advanceUntilIdle()

            assertFalse(viewModel.enabled.value)
        }

    @Test
    fun `choosing what is already chosen writes and reports nothing`() =
        runTest(dispatcher) {
            val storage = InMemoryTokenStorage()
            SoundViewModel(storage, analytics).setEnabled(true)
            testScheduler.advanceUntilIdle()

            assertNull(storage.read("kvizic.sound"))
            assertTrue(analytics.events.isEmpty())
        }

    @Test
    fun `a choice is reported and no cue ever is`() =
        runTest(dispatcher) {
            SoundViewModel(InMemoryTokenStorage(), analytics).setEnabled(false)

            assertEquals(
                listOf(Recorded(AnalyticsEvent.SOUND_CHANGED, mapOf(AnalyticsProperty.ENABLED to false))),
                analytics.events,
            )
        }

    private class FailingStorage : TokenStorage {
        override fun read(key: String): String? = null

        override suspend fun write(
            key: String,
            value: String,
        ): Unit = error("the disk is full")

        override suspend fun remove(key: String): Unit = error("the disk is full")
    }
}
