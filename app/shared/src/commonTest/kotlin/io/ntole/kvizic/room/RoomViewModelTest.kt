package io.ntole.kvizic.room

import io.ntole.kvizic.analytics.RecordingAnalytics
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.core.domain.error.GameError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.LobbyCommandKind
import io.ntole.kvizic.core.domain.lobby.LobbyEvent
import io.ntole.kvizic.core.domain.lobby.LobbyExit
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.domain.lobby.NoticeKind
import io.ntole.kvizic.core.domain.lobby.RefusalReason
import io.ntole.kvizic.core.domain.report.QuestionReportReason
import io.ntole.kvizic.core.domain.report.ReportQuestion
import io.ntole.kvizic.core.domain.report.ReportRepository
import io.ntole.kvizic.core.domain.session.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The room over a scripted session: taking a seat, what the room says meanwhile, and what is reported. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomViewModelTest {
    private val main: TestDispatcher = StandardTestDispatcher()
    private val session = ScriptedLobbySession()
    private val analytics = RecordingAnalytics()
    private val reports = RecordingReports()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a seat taken is under way until the room lets the player in and reported once in`() =
        runTest(main) {
            val room = room()

            room.quickPlay()
            testScheduler.advanceUntilIdle()
            assertEquals(Entry.Taking(EntryWay.QUICK_PLAY), room.entry.value)

            session.answerSeat(inLobby(GamePhase.Waiting(null)))
            testScheduler.advanceUntilIdle()

            assertEquals(Entry.None, room.entry.value)
            assertTrue(room.state.value is LobbySessionState.InLobby)
            assertEquals(
                listOf(mapOf(AnalyticsProperty.WAY to "quick_play")),
                analytics.named(AnalyticsEvent.ROOM_ENTERED).map { it.properties },
            )
        }

    @Test
    fun `a second tap while a seat is being taken takes none more`() =
        runTest(main) {
            val room = room()

            room.join("482915")
            room.join("482915")
            room.quickPlay()
            testScheduler.advanceUntilIdle()

            assertEquals(listOf("join 482915"), session.commands)
        }

    @Test
    fun `a seat refused says why where it was asked until taken down`() =
        runTest(main) {
            val room = room()

            room.join("111111")
            testScheduler.advanceUntilIdle()
            session.refuseSeat(GameError.LOBBY_NOT_FOUND)
            testScheduler.advanceUntilIdle()

            assertEquals(Entry.Failed(EntryWay.JOIN, GameError.LOBBY_NOT_FOUND), room.entry.value)
            assertEquals(
                listOf(mapOf(AnalyticsProperty.CODE to "LOBBY_NOT_FOUND", AnalyticsProperty.ACTION to "join")),
                analytics.named(AnalyticsEvent.ERROR_SHOWN).map { it.properties },
            )
            room.dismissEntryFailure()
            assertEquals(Entry.None, room.entry.value)
        }

    @Test
    fun `a new room is made with the settings picked`() =
        runTest(main) {
            val room = room()

            room.create(LobbySettings(questionCount = 15))
            testScheduler.advanceUntilIdle()

            assertEquals(listOf("create 15"), session.commands)
        }

    @Test
    fun `each member's last reaction bursts the same one again with a new key`() =
        runTest(main) {
            val room = room()
            testScheduler.advanceUntilIdle()

            session.events.emit(LobbyEvent.Reacted("nina", "fire"))
            testScheduler.advanceUntilIdle()
            val first = room.bursts.value.getValue("nina")
            session.events.emit(LobbyEvent.Reacted("nina", "fire"))
            testScheduler.advanceUntilIdle()
            val second = room.bursts.value.getValue("nina")

            assertEquals("fire", second.reaction)
            assertTrue(second.key != first.key)
        }

    @Test
    fun `a notice or a refusal shows a moment and the room's counts stay`() =
        runTest(main) {
            val room = room()
            testScheduler.advanceUntilIdle()

            session.events.emit(LobbyEvent.Refused(LobbyCommandKind.START, RefusalReason.NOT_HOST))
            session.events.emit(LobbyEvent.Presence(online = 128, searching = 7))
            testScheduler.runCurrent()
            assertEquals(RoomNote.ONLY_HOST, room.note.value)
            assertEquals(Presence(128, 7), room.presence.value)

            session.events.emit(LobbyEvent.Notice(NoticeKind.GAME_SHORTENED))
            testScheduler.runCurrent()
            assertEquals(RoomNote.GAME_SHORTENED, room.note.value)

            advanceTimeBy(5_000)
            assertNull(room.note.value)
        }

    @Test
    fun `a game played to its end is reported once by place and size alone`() =
        runTest(main) {
            val room = room()
            testScheduler.advanceUntilIdle()

            session.state.value = inLobby(GamePhase.Waiting(RESULTS))
            testScheduler.advanceUntilIdle()
            session.state.value = inLobby(GamePhase.Countdown(deadline(kotlin.time.Duration.parse("3s")), RESULTS))
            testScheduler.advanceUntilIdle()

            assertEquals(
                listOf(
                    mapOf(
                        AnalyticsProperty.RANK to 2,
                        AnalyticsProperty.PLAYERS to 5,
                        AnalyticsProperty.QUESTIONS to 10,
                        AnalyticsProperty.SOLO to false,
                    ),
                ),
                analytics.named(AnalyticsEvent.GAME_FINISHED).map { it.properties },
            )
        }

    @Test
    fun `a room that lets the player go is reported by why`() =
        runTest(main) {
            val room = room()
            testScheduler.advanceUntilIdle()

            session.state.value = inLobby(GamePhase.Waiting(null))
            testScheduler.advanceUntilIdle()
            session.state.value = LobbySessionState.Ended(LobbyExit.KICKED, "482915")
            testScheduler.advanceUntilIdle()

            assertEquals(
                listOf(mapOf(AnalyticsProperty.EXIT to "KICKED")),
                analytics.named(AnalyticsEvent.ROOM_EXITED).map { it.properties },
            )
            room.leave()
            assertEquals(LobbySessionState.Idle, room.state.value)
        }

    @Test
    fun `commands go to the session as they are`() =
        runTest(main) {
            val room = room()

            room.answer(2)
            room.start()
            room.kick("roda")
            room.transferHost("nina")
            room.backToLobby()
            room.react("bravo")
            room.updateSettings(LobbySettings(questionCount = 5))
            room.wake()

            assertEquals(
                listOf("answer 2", "start", "kick roda", "host nina", "back", "react bravo", "settings 5", "wake"),
                session.commands,
            )
        }

    @Test
    fun `a question reported says so a moment and a report refused says it could not be`() =
        runTest(main) {
            val room = room()

            room.report("q1", QuestionReportReason.WRONG_ANSWER)
            testScheduler.runCurrent()
            assertEquals(listOf("q1" to QuestionReportReason.WRONG_ANSWER), reports.sent)
            assertEquals(RoomNote.REPORTED, room.note.value)

            reports.refuseWith = KvizicException(GameError.QUESTION_NOT_FOUND)
            room.report("q2", QuestionReportReason.TYPO)
            testScheduler.runCurrent()
            assertEquals(RoomNote.REPORT_FAILED, room.note.value)
        }

    private fun room() = RoomViewModel(session, ReportQuestion(reports, NoSession), analytics)

    private class RecordingReports : ReportRepository {
        val sent = mutableListOf<Pair<String, QuestionReportReason>>()
        var refuseWith: KvizicException? = null

        override suspend fun report(
            questionId: String,
            reason: QuestionReportReason,
        ) {
            refuseWith?.let { throw it }
            sent += questionId to reason
        }
    }

    /** A session that is always there. */
    private object NoSession : SessionRepository {
        override suspend fun ensure(): String = YOU
    }
}
