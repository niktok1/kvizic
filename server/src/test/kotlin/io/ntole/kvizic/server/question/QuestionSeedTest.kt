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
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** A development server's questions, from a draft file named at boot. */
class QuestionSeedTest {
    @Test
    fun `a seed file's questions are in the bank, approved, when the server comes up`() {
        val file = File.createTempFile("kvizic-seed", ".json").apply { deleteOnExit() }
        val drafts =
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
                )
        file.writeText(
            ServerJson.encodeToString(ImportQuestionsRequest.serializer(), ImportQuestionsRequest("dev-seed", drafts)),
        )

        runTestServer("bank-seed", configure = { it.copy(questionSeedFile = file.path) }) { client, _ ->
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
