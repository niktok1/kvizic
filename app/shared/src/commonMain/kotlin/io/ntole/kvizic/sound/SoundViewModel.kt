package io.ntole.kvizic.sound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ntole.kvizic.core.domain.analytics.Analytics
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.core.network.TokenStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Whether the game makes sound, as the device keeps it: on until the player turns it off.
 *
 * Kept in [storage] under [KEY], the one key for the device whatever server the build talks to, as the
 * language is: the player's choice follows them, not the environment. A choice made is reported to
 * [analytics]; the cues played never are.
 */
class SoundViewModel(
    private val storage: TokenStorage,
    private val analytics: Analytics,
) : ViewModel() {
    private val on = MutableStateFlow(storage.read(KEY) != OFF)

    val enabled: StateFlow<Boolean> = on.asStateFlow()

    /**
     * Turns sound on or off at once, then keeps it for the next launch. A write that fails leaves it as
     * chosen for this run only, which is no reason to stop the game or to say anything.
     */
    fun setEnabled(enabled: Boolean) {
        if (enabled == on.value) return
        on.value = enabled
        analytics.track(AnalyticsEvent.SOUND_CHANGED, mapOf(AnalyticsProperty.ENABLED to enabled))
        viewModelScope.launch {
            try {
                storage.write(KEY, if (enabled) ON else OFF)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (ignored: Exception) {
                // Kept for this run only, as the KDoc says.
            }
        }
    }

    companion object {
        /** Beside the language's key (`LanguageViewModel.KEY`), and none of the sessions'. */
        const val KEY: String = "kvizic.sound"

        private const val ON = "on"
        private const val OFF = "off"
    }
}
