package io.ntole.kvizic.room

import io.ntole.kvizic.core.domain.error.DomainError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.lobby.LobbyEvent
import io.ntole.kvizic.core.domain.lobby.LobbySession
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A lobby session a test drives: a seat taken waits for [seat] to be answered, with the room's state or a
 * refusal; what happens in the room is what the test sets in [state] and emits on [events]; every command
 * is written down in [commands], by name.
 */
class ScriptedLobbySession : LobbySession {
    override val state = MutableStateFlow<LobbySessionState>(LobbySessionState.Idle)
    override val events = MutableSharedFlow<LobbyEvent>(extraBufferCapacity = 16)

    val commands = mutableListOf<String>()

    /** The seat being taken, which the test answers. */
    var seat = CompletableDeferred<LobbySessionState?>()
        private set

    /** Lets the seat being taken in, into [into], or refuses it for [error]. */
    fun answerSeat(into: LobbySessionState.InLobby) {
        seat.complete(into)
    }

    fun refuseSeat(error: DomainError) {
        seat.completeExceptionally(KvizicException(error))
    }

    private suspend fun take(what: String) {
        commands += what
        seat = CompletableDeferred()
        state.value = LobbySessionState.Joining(null)
        try {
            seat.await()?.let { state.value = it }
        } catch (failure: KvizicException) {
            state.value = LobbySessionState.Idle
            throw failure
        }
    }

    override suspend fun create(settings: LobbySettings) = take("create ${settings.questionCount}")

    override suspend fun join(code: String) = take("join $code")

    override suspend fun quickPlay() = take("quick_play")

    override suspend fun solo() = take("solo")

    override fun answer(option: Int) {
        commands += "answer $option"
    }

    override fun start() {
        commands += "start"
    }

    override fun updateSettings(settings: LobbySettings) {
        commands += "settings ${settings.questionCount}"
    }

    override fun kick(playerId: String) {
        commands += "kick $playerId"
    }

    override fun transferHost(playerId: String) {
        commands += "host $playerId"
    }

    override fun backToLobby() {
        commands += "back"
    }

    override fun react(reaction: String) {
        commands += "react $reaction"
    }

    override fun leave() {
        commands += "leave"
        state.value = LobbySessionState.Idle
    }

    override fun wake() {
        commands += "wake"
    }

    /** What the room said, for a test to read the events it emitted. */
    val said: SharedFlow<LobbyEvent> get() = events

    val current: StateFlow<LobbySessionState> get() = state
}
