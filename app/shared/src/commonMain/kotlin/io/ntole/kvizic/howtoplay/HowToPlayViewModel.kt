package io.ntole.kvizic.howtoplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ntole.kvizic.core.network.SessionStore
import io.ntole.kvizic.core.network.TokenStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Whether the How to play cards open over Home at this launch, [due]: on a new install only, one that has
 * never shown them and has no player yet, so a player of an earlier build never sees them unasked.
 *
 * Kept in [storage] under [KEY], as the sound is: once shown, never shown by itself again.
 */
class HowToPlayViewModel(
    private val storage: TokenStorage,
    sessions: SessionStore,
) : ViewModel() {
    private var kept = storage.read(KEY) != null

    val due: Boolean = !kept && sessions.read() == null

    /**
     * Notes the cards shown, for every launch after this one. A write that fails shows them again next
     * launch, which is no reason to stop the game or to say anything.
     */
    fun shown() {
        if (kept) return
        kept = true
        viewModelScope.launch {
            try {
                storage.write(KEY, SHOWN)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (ignored: Exception) {
                kept = false
            }
        }
    }

    companion object {
        /** Beside the sound's key (`SoundViewModel.KEY`), and none of the sessions'. */
        const val KEY: String = "kvizic.how_to_play"

        private const val SHOWN = "shown"
    }
}
