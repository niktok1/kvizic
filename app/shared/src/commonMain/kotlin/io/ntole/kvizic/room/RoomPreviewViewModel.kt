package io.ntole.kvizic.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ntole.kvizic.core.domain.error.DomainError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.lobby.PublicLobby
import io.ntole.kvizic.core.domain.lobby.PublicLobbyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the join screen shows of the room whose code is typed, before a seat is taken in it. */
sealed interface RoomPreview {
    /** Nothing yet: fewer than all six digits. */
    data object None : RoomPreview

    /** The room is being read. */
    data object Looking : RoomPreview

    data class Found(
        val lobby: PublicLobby,
    ) : RoomPreview

    /** The room could not be read, for [error]: no room has that code, or the network failed. */
    data class Missing(
        val error: DomainError,
    ) : RoomPreview
}

/**
 * The room a typed code names, read once all six digits are in, so a player sees what a room is set to before
 * they join it: a code changed, or cut short, starts over. A read that fails says why and takes nothing from
 * joining, which asks the server again by itself.
 */
class RoomPreviewViewModel(
    private val repository: PublicLobbyRepository,
) : ViewModel() {
    private val mutablePreview = MutableStateFlow<RoomPreview>(RoomPreview.None)
    val preview: StateFlow<RoomPreview> = mutablePreview.asStateFlow()

    private var reading: Job? = null

    /** Shows the room [code] names, once it has all its digits, and none before. */
    fun show(code: String) {
        reading?.cancel()
        if (code.length != CODE_LENGTH) {
            mutablePreview.value = RoomPreview.None
            return
        }
        mutablePreview.value = RoomPreview.Looking
        reading =
            viewModelScope.launch {
                mutablePreview.value =
                    try {
                        RoomPreview.Found(repository.preview(code))
                    } catch (failure: KvizicException) {
                        RoomPreview.Missing(failure.error)
                    }
            }
    }
}
