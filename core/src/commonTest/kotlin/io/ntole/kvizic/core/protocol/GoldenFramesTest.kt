package io.ntole.kvizic.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Frames exactly as protocol 1 sent them. A later build must still decode every one of them the same
 * way, so a rename of a type, a field or an enum value fails here rather than on installed clients.
 * Add a frame here when a message ships; never change one that has shipped.
 */
class GoldenFramesTest {
    private val v1ServerFrames =
        listOf(
            """{"t":"welcome","protocol":1,"you":"p1","pingEveryMs":5000}""",
            """{"t":"ping","seq":3,"rttMs":80}""",
            """{"t":"ack","id":4}""",
            """{"t":"rejected","id":5,"code":"ALREADY_ANSWERED"}""",
            """{"t":"joined","v":1,"member":{"player":"p1","name":"Ана","avatar":"fox","seat":0,"connected":true,"onResults":false,"playing":false}}""",
            """{"t":"left","v":2,"player":"p2","reason":"KICKED"}""",
            """{"t":"host","v":4,"host":"p2"}""",
            """{"t":"countdown","v":6,"remainingMs":3000}""",
            """{"t":"started","v":7,"gameId":"g1","players":["p1","p2"],"questionCount":10}""",
            """{"t":"question","v":8,"question":{"index":0,"count":10,"text":"?","topic":"SPORT","optionCount":4,"kind":"CHOICE"},"readMs":2000}""",
            """{"t":"answers","v":9,"index":0,"options":["a","b","c","d"],"remainingMs":15000,"durationMs":15000}""",
            """{"t":"picks","v":10,"index":0,"picks":[{"player":"p1","option":1}]}""",
            """{"t":"progress","v":10,"index":0,"answered":["p1"]}""",
            """{"t":"reaction","player":"p1","reaction":"bravo"}""",
            """{"t":"presence","online":12,"searching":3}""",
            """{"t":"notice","kind":"SERVER_RESTARTING","remainingMs":60000}""",
            """{"t":"closing","reason":"KICKED"}""",
        )

    private val v1ClientFrames =
        listOf(
            """{"t":"hello","ticket":"t","protocol":1,"platform":"android","build":100}""",
            """{"t":"pong","seq":3}""",
            """{"t":"resync"}""",
            """{"t":"answer","id":1,"question":2,"option":3}""",
            """{"t":"react","reaction":"fire"}""",
            """{"t":"start","id":3}""",
            """{"t":"kick","id":4,"player":"p2"}""",
            """{"t":"host","id":5,"player":"p2"}""",
            """{"t":"back","id":6}""",
            """{"t":"leave"}""",
        )

    @Test
    fun everyShippedServerFrameStillDecodesToAKnownMessage() {
        v1ServerFrames.forEach { frame ->
            val decoded = ProtocolJson.decodeFromString(ServerMessage.serializer(), frame)
            assert(decoded != ServerMessage.Unknown) { "$frame no longer decodes" }
        }
    }

    @Test
    fun everyShippedClientFrameStillDecodesToAKnownMessage() {
        v1ClientFrames.forEach { frame ->
            val decoded = ProtocolJson.decodeFromString(ClientMessage.serializer(), frame)
            assert(decoded != ClientMessage.Unknown) { "$frame no longer decodes" }
        }
    }

    @Test
    fun theShippedAnswerFrameMeansWhatItMeant() {
        val answer = ProtocolJson.decodeFromString(ClientMessage.serializer(), v1ClientFrames[3])
        assertIs<ClientMessage.Answer>(answer)
        assertEquals(ClientMessage.Answer(id = 1, question = 2, option = 3), answer)
    }
}
