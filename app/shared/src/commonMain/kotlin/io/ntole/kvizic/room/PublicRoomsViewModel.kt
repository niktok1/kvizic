package io.ntole.kvizic.room

import androidx.lifecycle.ViewModel
import io.ntole.kvizic.core.domain.error.DomainError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.lobby.PublicLobbies
import io.ntole.kvizic.core.domain.lobby.PublicLobbyRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The open public rooms, and how many play and look for a game, read again every [every] while a screen
 * that shows them is up ([poll], which that screen runs while it is started): Home for its counts, the
 * public rooms' list for the rooms. The last read stays shown through a read that fails, which says why.
 */
class PublicRoomsViewModel(
    private val repository: PublicLobbyRepository,
) : ViewModel() {
    private val mutableLobbies = MutableStateFlow<PublicLobbies?>(null)
    val lobbies: StateFlow<PublicLobbies?> = mutableLobbies.asStateFlow()

    private val mutableFailure = MutableStateFlow<DomainError?>(null)
    val failure: StateFlow<DomainError?> = mutableFailure.asStateFlow()

    /** Reads the rooms now and every [every] after, until the caller is cancelled. */
    suspend fun poll(every: Duration = HOME_EVERY) {
        while (true) {
            read()
            delay(every)
        }
    }

    private suspend fun read() {
        try {
            mutableLobbies.value = repository.list()
            mutableFailure.value = null
        } catch (failure: KvizicException) {
            mutableFailure.value = failure.error
        }
    }

    companion object {
        /** How often Home reads the counts again. */
        val HOME_EVERY: Duration = 10.seconds

        /** How often the public rooms' list reads the rooms again. */
        val LIST_EVERY: Duration = 5.seconds
    }
}
