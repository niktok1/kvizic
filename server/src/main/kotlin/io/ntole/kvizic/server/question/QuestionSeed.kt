package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.question.ImportQuestionsRequest
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.plugins.ServerJson
import org.slf4j.Logger
import java.io.File

/**
 * The development server's questions, from the seed file `QUESTION_SEED_FILE` names: a draft file of the
 * private content repo, imported as approved at boot. Only ever on H2, which a boot of each discards
 * (`ServerConfig`), so it is read in whole each time. A file that cannot be read fails the boot, naming it.
 */
object QuestionSeed {
    suspend fun load(
        db: Db,
        path: String,
        topics: Set<String>,
        log: Logger,
        now: Long = System.currentTimeMillis(),
    ) {
        val file = File(path)
        require(file.isFile) { "QUESTION_SEED_FILE names no file: $path" }
        val seed =
            try {
                ServerJson.decodeFromString(ImportQuestionsRequest.serializer(), file.readText())
            } catch (malformed: Exception) {
                throw IllegalArgumentException("QUESTION_SEED_FILE is no question file: ${malformed.message}")
            }
        val result = db.query { QuestionStore.import(seed.batch, seed.questions, QuestionStatus.APPROVED, topics, now) }
        log.info("seeded ${result.created.size} questions from ${file.name}, ${result.duplicates.size} known")
        result.refused.forEach { log.warn("seed question ${it.key} refused: ${it.reason}") }
    }
}
