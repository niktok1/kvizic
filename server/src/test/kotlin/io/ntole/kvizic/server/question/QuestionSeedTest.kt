package io.ntole.kvizic.server.question

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.question.AdminQuestionPageDto
import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.ImportQuestionsRequest
import io.ntole.kvizic.core.question.QuestionDraftDto
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.server.FLOW_ADMIN_TOKEN
import io.ntole.kvizic.server.plugins.ServerJson
import io.ntole.kvizic.server.runTestServer
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A development server's questions, from a draft file named at boot, as JSON or gzipped in base64. */
class QuestionSeedTest {
    private val json =
        ServerJson.encodeToString(
            ImportQuestionsRequest.serializer(),
            ImportQuestionsRequest(
                "dev-seed",
                (1..3).map { n ->
                    QuestionDraftDto(
                        key = "seed-$n",
                        text = "Семе $n?",
                        options = listOf("Да $n", "Не $n"),
                        correct = 0,
                        topics = listOf("GEOGRAPHY"),
                        difficulty = Difficulty.EASY,
                    )
                } +
                    QuestionDraftDto(
                        key = "broken",
                        text = " ",
                        options = listOf("а", "б"),
                        correct = 0,
                        topics = listOf("GEOGRAPHY"),
                    ),
            ),
        )

    /** The JSON gzipped and in base64, as a Render secret file holds it, in lines of 76 as `base64` writes. */
    private val packed =
        Base64.getMimeEncoder().encodeToString(
            ByteArrayOutputStream()
                .also { bytes -> GZIPOutputStream(bytes).use { it.write(json.encodeToByteArray()) } }
                .toByteArray(),
        ) + "\n"

    @Test
    fun `a seed file's questions are in the bank, approved, when the server comes up`() =
        assertSeeded(json, "bank-seed")

    @Test
    fun `a seed file gzipped in base64 seeds the same`() {
        assertTrue(packed.lines().size > 2, "the test's base64 runs over lines")
        assertEquals(json, QuestionSeed.text(packed))
        assertSeeded(packed, "bank-seed-packed")
    }

    private fun assertSeeded(
        contents: String,
        database: String,
    ) {
        val file = File.createTempFile("kvizic-seed", ".json").apply { deleteOnExit() }
        file.writeText(contents)

        runTestServer(database, configure = { it.copy(questionSeedFile = file.path) }) { client, _ ->
            val bank =
                client
                    .get(KvizicApi.Paths.ADMIN_QUESTIONS) { header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN) }
                    .body<AdminQuestionPageDto>()
                    .questions
            assertEquals(
                setOf("seed-1", "seed-2", "seed-3"),
                bank.map { it.importKey }.toSet(),
                "the broken one left out",
            )
            assertEquals(setOf(QuestionStatus.APPROVED), bank.map { it.status }.toSet())
        }
    }
}
