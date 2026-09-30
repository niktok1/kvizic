package io.ntole.kvizic.core.protocol

import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.lobby.Visibility
import io.ntole.kvizic.core.question.QuestionKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtocolJsonTest {
    private val question =
        QuestionView(index = 2, count = 10, text = "Која река?", topic = "GEOGRAPHY", optionCount = 4)
    private val member = MemberView(player = "p1", name = "Брзи Јеж", avatar = "hedgehog", seat = 3)
    private val settings = LobbySettingsDto(questionCount = 5, topics = listOf("SPORT"), visibility = Visibility.PUBLIC)
    private val reveal =
        RevealView(
            index = 2,
            count = 10,
            questionId = "q1",
            text = "Која река?",
            options = listOf("Дунав", "Сава", "Тиса", "Морава"),
            correct = 0,
            results = listOf(AnswerResultView("p1", option = 0, points = 88, timeMs = 1_200, order = 1)),
            standings = listOf(StandingView("p1", score = 88, rank = 1, correct = 1)),
            nextInMs = 5_000,
        )
    private val results =
        ResultsView(
            gameId = "g1",
            questionCount = 10,
            standings = listOf(FinalStandingView("p1", "Ана", "fox", score = 640, correct = 7, rank = 1)),
            personalBest = PersonalBestView(score = 640, previous = 500, isNew = true),
        )

    private val serverMessages: List<ServerMessage> =
        listOf(
            ServerMessage.Welcome(protocol = 1, you = "p1", pingEveryMs = 5_000),
            ServerMessage.Snapshot(
                v = 7,
                you = "p1",
                lobby = LobbyView("l1", "482915", LobbyKind.PUBLIC, settings, host = "p1", members = listOf(member)),
                phase = PhaseView.Answering("g1", listOf("p1"), question, listOf("a", "b"), 9_000, 15_000),
            ),
            ServerMessage.Ping(seq = 3, rttMs = 80),
            ServerMessage.Ack(id = 4),
            ServerMessage.Rejected(id = 5, code = RejectCode.ALREADY_ANSWERED),
            ServerMessage.MemberJoined(v = 1, member = member),
            ServerMessage.MemberLeft(v = 2, player = "p2", reason = LeaveReason.KICKED),
            ServerMessage.MemberUpdated(v = 3, member = member.copy(connected = false)),
            ServerMessage.HostChanged(v = 4, host = "p2"),
            ServerMessage.SettingsChanged(v = 5, settings = settings),
            ServerMessage.CountdownStarted(v = 6, remainingMs = 3_000),
            ServerMessage.GameStarted(v = 7, gameId = "g1", players = listOf("p1", "p2"), questionCount = 10),
            ServerMessage.QuestionShown(v = 8, question = question, readMs = 2_000),
            ServerMessage.AnswersOpened(
                v = 9,
                index = 2,
                options = listOf("a", "b", "c"),
                remainingMs = 15_000,
                durationMs = 15_000,
            ),
            ServerMessage.Picks(v = 10, index = 2, picks = listOf(PickView("p1", 1))),
            ServerMessage.Progress(v = 10, index = 2, answered = listOf("p1")),
            ServerMessage.Revealed(v = 11, reveal = reveal),
            ServerMessage.GameOver(v = 12, results = results),
            ServerMessage.GameAborted(v = 13, reason = AbortReason.NO_QUESTIONS),
            ServerMessage.Reacted(player = "p1", reaction = "bravo"),
            ServerMessage.Presence(online = 12, searching = 3),
            ServerMessage.Notice(kind = NoticeKind.SERVER_RESTARTING, remainingMs = 60_000),
            ServerMessage.Closing(reason = CloseReason.KICKED),
        )

    private val clientMessages: List<ClientMessage> =
        listOf(
            ClientMessage.Hello(ticket = "t", protocol = 1, platform = "android", build = 100),
            ClientMessage.Pong(seq = 3),
            ClientMessage.Resync,
            ClientMessage.Answer(id = 1, question = 2, option = 3),
            ClientMessage.React(reaction = "fire"),
            ClientMessage.UpdateSettings(id = 2, settings = settings),
            ClientMessage.Start(id = 3),
            ClientMessage.Kick(id = 4, player = "p2"),
            ClientMessage.TransferHost(id = 5, player = "p2"),
            ClientMessage.BackToLobby(id = 6),
            ClientMessage.Leave,
        )

    @Test
    fun everyServerMessageRoundTrips() {
        serverMessages.forEach { message ->
            val json = ProtocolJson.encodeToString(ServerMessage.serializer(), message)
            assertEquals(message, ProtocolJson.decodeFromString(ServerMessage.serializer(), json), json)
        }
    }

    @Test
    fun everyClientMessageRoundTrips() {
        clientMessages.forEach { message ->
            val json = ProtocolJson.encodeToString(ClientMessage.serializer(), message)
            assertEquals(message, ProtocolJson.decodeFromString(ClientMessage.serializer(), json), json)
        }
    }

    @Test
    fun aServerMessageTypeFromANewerServerDecodesAsUnknown() {
        val decoded = ProtocolJson.decodeFromString(ServerMessage.serializer(), """{"t":"confetti","v":9,"count":3}""")
        assertEquals(ServerMessage.Unknown, decoded)
    }

    @Test
    fun aClientMessageTypeFromANewerClientDecodesAsUnknown() {
        val decoded = ProtocolJson.decodeFromString(ClientMessage.serializer(), """{"t":"emote","id":1,"x":{"y":2}}""")
        assertEquals(ClientMessage.Unknown, decoded)
    }

    @Test
    fun aPhaseFromANewerServerDecodesAsUnknownInsideASnapshot() {
        val json =
            """{"t":"snapshot","v":1,"you":"p1",""" +
                """"lobby":{"id":"l","code":"123456","settings":{},"host":"p1","members":[]},""" +
                """"phase":{"t":"lightningRound","remainingMs":3}}"""
        val snapshot = ProtocolJson.decodeFromString(ServerMessage.serializer(), json) as ServerMessage.Snapshot
        assertEquals(PhaseView.Unknown, snapshot.phase)
        assertEquals(LobbySettingsDto(), snapshot.lobby.settings)
    }

    @Test
    fun anEnumValueFromANewerServerDecodesAsUnknown() {
        val left =
            ProtocolJson.decodeFromString(
                ServerMessage.serializer(),
                """{"t":"left","v":2,"player":"p","reason":"BANISHED"}""",
            )
        assertEquals(ServerMessage.MemberLeft(v = 2, player = "p", reason = LeaveReason.UNKNOWN), left)

        val view =
            ProtocolJson.decodeFromString(
                QuestionView.serializer(),
                """{"index":0,"count":1,"text":"x","topic":"t","optionCount":2,"kind":"ORDERING"}""",
            )
        assertEquals(QuestionKind.UNKNOWN, view.kind)
    }

    @Test
    fun aFieldFromANewerServerIsIgnored() {
        val ack = ProtocolJson.decodeFromString(ServerMessage.serializer(), """{"t":"ack","id":4,"latencyBudget":12}""")
        assertEquals(ServerMessage.Ack(4), ack)
    }

    @Test
    fun nothingBeforeTheRevealCarriesTheAnswerOrThePoints() {
        val beforeReveal: List<Any> =
            listOf(
                ServerMessage.QuestionShown(v = 1, question = question, readMs = 2_000),
                ServerMessage.AnswersOpened(
                    v = 2,
                    index = 2,
                    options = listOf("a", "b"),
                    remainingMs = 1,
                    durationMs = 1,
                ),
                ServerMessage.Picks(v = 3, index = 2, picks = listOf(PickView("p1", 1))),
                ServerMessage.Progress(v = 3, index = 2, answered = listOf("p1")),
            )
        val forbidden = setOf("correct", "questionId", "points", "results", "order", "timeMs")
        beforeReveal.forEach { message ->
            val keys = keysOf(ProtocolJson.encodeToJsonElement(ServerMessage.serializer(), message as ServerMessage))
            assertTrue(keys.none { it in forbidden }, "$message carries ${keys.intersect(forbidden)}")
        }
        listOf<PhaseView>(
            PhaseView.Reading("g", listOf("p1"), question, 1_000),
            PhaseView.Answering("g", listOf("p1"), question, listOf("a", "b"), 1, 1, picks = listOf(PickView("p1", 0))),
        ).forEach { phase ->
            val keys = keysOf(ProtocolJson.encodeToJsonElement(PhaseView.serializer(), phase))
            assertFalse(keys.any { it in forbidden - "picks" }, "$phase carries ${keys.intersect(forbidden)}")
        }
    }

    @Test
    fun theHelloHidesItsTicket() {
        assertFalse("secret" in ClientMessage.Hello(ticket = "secret", protocol = 1).toString())
    }

    @Test
    fun theDiscriminatorIsShortAndTheTypeNamesAreStable() {
        val json = ProtocolJson.encodeToString(ServerMessage.serializer(), ServerMessage.Ack(1))
        assertEquals("""{"t":"ack","id":1}""", json)
    }

    private fun keysOf(element: JsonElement): Set<String> =
        when (element) {
            is JsonObject -> element.keys + element.values.flatMap { keysOf(it) }
            is kotlinx.serialization.json.JsonArray -> element.flatMap { keysOf(it) }.toSet()
            else -> emptySet()
        }

    @Suppress("unused")
    private fun Json.objectOf(json: String): JsonObject = parseToJsonElement(json).jsonObject
}
