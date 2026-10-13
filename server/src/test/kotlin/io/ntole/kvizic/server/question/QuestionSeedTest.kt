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
import io.ntole.kvizic.core.question.QuestionTextDto
import io.ntole.kvizic.core.region.Country
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
    private val packed = packed(json)

    private fun packed(json: String) =
        Base64.getMimeEncoder().encodeToString(
            ByteArrayOutputStream()
                .also { bytes -> GZIPOutputStream(bytes).use { it.write(json.encodeToByteArray()) } }
                .toByteArray(),
        ) + "\n"

    @Test
    fun `a packed seed's countries and translations are in the bank with its questions`() {
        val local =
            QuestionDraftDto(
                key = "seed-ba",
                text = "Семе?",
                options = listOf("Да", "Не"),
                correct = 1,
                topics = listOf("GEOGRAPHY"),
                difficulty = Difficulty.HARD,
                explanation = "Зато.",
                countries = listOf(Country.BA),
                translations =
                    mapOf(
                        "hr" to QuestionTextDto("Sjeme?", listOf("Da", "Ne"), "Zato hr."),
                        "bs" to QuestionTextDto("Sjeme bs?", listOf("Da bs", "Ne bs"), "Zato bs."),
                    ),
            )
        val common = local.copy(key = "seed-common", countries = emptyList(), translations = emptyMap())
        val seed =
            ServerJson.encodeToString(
                ImportQuestionsRequest.serializer(),
                ImportQuestionsRequest("dev-seed", listOf(local, common)),
            )
        val file =
            File.createTempFile("kvizic-seed", ".gz.b64").apply {
                deleteOnExit()
                writeText(packed(seed))
            }
        runTestServer("bank-seed-regions", configure = { it.copy(questionSeedFile = file.path) }) { client, _ ->
            val bank =
                client
                    .get(
                        KvizicApi.Paths.ADMIN_QUESTION_EXPORTS,
                    ) { header(KvizicApi.Headers.ADMIN_TOKEN, FLOW_ADMIN_TOKEN) }
                    .body<ImportQuestionsRequest>()
                    .questions
            assertEquals(setOf(local, common), bank.toSet(), "approved, so exported, as written")
        }
    }

    @Test
    fun `a seed file's questions are in the bank, approved, when the server comes up`() =
        assertSeeded(json, "bank-seed")

    @Test
    fun `a seed file gzipped in base64 seeds the same`() {
        assertTrue(packed.lines().size > 2, "the test's base64 runs over lines")
        assertEquals(json, QuestionSeed.text(packed))
        assertSeeded(packed, "bank-seed-packed")
    }

    /** A bank past one secret file's 500 KiB is cut into parts, named separated by commas. */
    @Test
    fun `a seed in several files seeds them all`() {
        val request = ServerJson.decodeFromString(ImportQuestionsRequest.serializer(), json)
        val parts =
            request.questions.chunked(2).map { part ->
                ServerJson.encodeToString(ImportQuestionsRequest.serializer(), request.copy(questions = part))
            }
        assertEquals(listOf("a", "b"), QuestionSeed.paths(" a , b ,"))
        assertSeeded(parts, "bank-seed-parts")
    }

    private fun assertSeeded(
        contents: String,
        database: String,
    ) = assertSeeded(listOf(contents), database)

    private fun assertSeeded(
        contents: List<String>,
        database: String,
    ) {
        val files =
            contents.map { text ->
                File.createTempFile("kvizic-seed", ".json").apply {
                    deleteOnExit()
                    writeText(text)
                }
            }

        runTestServer(
            database,
            configure = { it.copy(questionSeedFile = files.joinToString(",") { it.path }) },
        ) { client, _ ->
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
