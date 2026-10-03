package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.question.ImportQuestionsRequest
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.plugins.ServerJson
import org.slf4j.Logger
import java.io.File
import java.util.Base64
import java.util.zip.GZIPInputStream

/**
 * The development server's questions, from the seed file `QUESTION_SEED_FILE` names: a draft file of the
 * private content repo, imported as approved at boot. Only ever on H2, which a boot of each discards
 * (`ServerConfig`), so it is read in whole each time. A file that cannot be read fails the boot, naming it.
 *
 * The file is the drafts' JSON, or that JSON gzipped and written in base64 ([text]): a Render secret file
 * holds at most 500 KiB, which 1.536 questions' JSON passes and their gzip in base64 keeps to 315. A bank that
 * outgrows one file is cut into several, which `QUESTION_SEED_FILE` names separated by commas ([paths]), each
 * imported in turn.
 */
object QuestionSeed {
    /** What a gzip file's first three bytes (1f 8b 08) read as in base64. */
    private const val GZIP_BASE64 = "H4sI"

    /** The seed's JSON from a file's [contents]: as they are, or unpacked when they are gzip in base64. */
    internal fun text(contents: String): String {
        val trimmed = contents.trim()
        if (!trimmed.startsWith(GZIP_BASE64)) return contents
        // The MIME decoder skips line breaks, which a pasted secret may gain.
        val packed = Base64.getMimeDecoder().decode(trimmed)
        return GZIPInputStream(packed.inputStream()).use { it.readBytes().decodeToString() }
    }

    /** The files [setting] names: one path, or several separated by commas, each trimmed. */
    internal fun paths(setting: String): List<String> = setting.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    suspend fun load(
        db: Db,
        setting: String,
        topics: Set<String>,
        log: Logger,
        now: Long = System.currentTimeMillis(),
    ) {
        paths(setting).forEach { loadFile(db, it, topics, log, now) }
    }

    private suspend fun loadFile(
        db: Db,
        path: String,
        topics: Set<String>,
        log: Logger,
        now: Long,
    ) {
        val file = File(path)
        require(file.isFile) { "QUESTION_SEED_FILE names no file: $path" }
        val seed =
            try {
                ServerJson.decodeFromString(ImportQuestionsRequest.serializer(), text(file.readText()))
            } catch (malformed: Exception) {
                throw IllegalArgumentException("QUESTION_SEED_FILE is no question file: ${malformed.message}")
            }
        val result = db.query { QuestionStore.import(seed.batch, seed.questions, QuestionStatus.APPROVED, topics, now) }
        log.info("seeded ${result.created.size} questions from ${file.name}, ${result.duplicates.size} known")
        result.refused.forEach { log.warn("seed question ${it.key} refused: ${it.reason}") }
    }
}
