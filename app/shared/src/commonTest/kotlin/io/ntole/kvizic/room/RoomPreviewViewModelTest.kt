package io.ntole.kvizic.room

import io.ntole.kvizic.core.domain.error.GameError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.domain.lobby.PublicLobbies
import io.ntole.kvizic.core.domain.lobby.PublicLobby
import io.ntole.kvizic.core.domain.lobby.PublicLobbyRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The room a typed code names, read once its six digits are in. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomPreviewViewModelTest {
    private val main: TestDispatcher = StandardTestDispatcher()
    private val rooms = mutableMapOf<String, CompletableDeferred<PublicLobby>>()
    private val asked = mutableListOf<String>()

    private val repository =
        object : PublicLobbyRepository {
            override suspend fun list() = PublicLobbies(emptyList(), 0, 0)

            override suspend fun preview(code: String): PublicLobby {
                asked += code
                return rooms.getValue(code).await()
            }
        }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun lobby(code: String) = PublicLobby(code, "Нина", "fox", 3, 8, false, LobbySettings(name = "Петак"))

    @Test
    fun `nothing is read until all six digits are in and then the room is shown`() =
        runTest(main) {
            val room = RoomPreviewViewModel(repository)
            rooms["482915"] = CompletableDeferred()

            room.show("48291")
            testScheduler.advanceUntilIdle()
            assertEquals(RoomPreview.None, room.preview.value)
            assertEquals(emptyList(), asked)

            room.show("482915")
            testScheduler.runCurrent()
            assertEquals(RoomPreview.Looking, room.preview.value)
            rooms.getValue("482915").complete(lobby("482915"))
            testScheduler.advanceUntilIdle()
            assertEquals(RoomPreview.Found(lobby("482915")), room.preview.value)

            room.show("4829")
            assertEquals(RoomPreview.None, room.preview.value, "a code cut short shows no room")
        }

    @Test
    fun `a code changed while its room is read shows the new code's room and never the old one's`() =
        runTest(main) {
            val room = RoomPreviewViewModel(repository)
            rooms["111111"] = CompletableDeferred()
            rooms["222222"] = CompletableDeferred()

            room.show("111111")
            testScheduler.runCurrent()
            room.show("222222")
            testScheduler.runCurrent()
            rooms.getValue("111111").complete(lobby("111111"))
            rooms.getValue("222222").complete(lobby("222222"))
            testScheduler.advanceUntilIdle()

            assertEquals(RoomPreview.Found(lobby("222222")), room.preview.value)
        }

    @Test
    fun `a room that cannot be read says why`() =
        runTest(main) {
            val room = RoomPreviewViewModel(repository)
            rooms["333333"] =
                CompletableDeferred<PublicLobby>().also {
                    it.completeExceptionally(
                        KvizicException(GameError.LOBBY_NOT_FOUND),
                    )
                }

            room.show("333333")
            testScheduler.advanceUntilIdle()

            assertEquals(RoomPreview.Missing(GameError.LOBBY_NOT_FOUND), room.preview.value)
        }
}
