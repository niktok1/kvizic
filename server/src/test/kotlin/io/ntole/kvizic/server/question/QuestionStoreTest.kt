package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.QuestionDraftDto
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.db.Questions
import io.ntole.kvizic.server.db.appTables
import io.ntole.kvizic.server.db.connectH2
import io.ntole.kvizic.server.db.h2Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** The bank's writes at READ COMMITTED, raced. */
class QuestionStoreTest {
    private val database = connectH2(h2Url("kvizic-bank-${UUID.randomUUID()}"), Connection.TRANSACTION_READ_COMMITTED)
    private val db = Db(database)

    init {
        transaction(database) {
            SchemaUtils.create(*appTables)
            BankFixtures.topic("GEOGRAPHY")
        }
    }

    private fun draft(key: String) =
        QuestionDraftDto(
            key = key,
            text = "Питање $key?",
            options = listOf("а $key", "б $key"),
            correct = 0,
            topics = listOf("GEOGRAPHY"),
            difficulty = Difficulty.EASY,
        )

    @Test
    fun `imports of one draft racing store it once`() {
        val results =
            runBlocking(Dispatchers.IO) {
                (1..8)
                    .map {
                        async {
                            db.query {
                                QuestionStore.import(
                                    "b",
                                    listOf(draft("same")),
                                    QuestionStatus.DRAFT,
                                    setOf("GEOGRAPHY"),
                                    0,
                                )
                            }
                        }
                    }.awaitAll()
            }
        assertEquals(1, results.sumOf { it.created.size })
        assertEquals(7, results.sumOf { it.duplicates.size })
        assertEquals(1, transaction(database) { Questions.selectAll().count() })
    }

    @Test
    fun `of two moderators deciding one draft at once, one wins`() {
        val id =
            transaction(database) {
                QuestionStore.import("b", listOf(draft("a")), QuestionStatus.DRAFT, setOf("GEOGRAPHY"), 0)
                Questions.selectAll().single()[Questions.id]
            }
        val outcomes =
            runBlocking(Dispatchers.IO) {
                (1..6)
                    .map { n ->
                        async {
                            runCatching {
                                db.query {
                                    if (n % 2 == 0) {
                                        QuestionStore.move(
                                            id,
                                            setOf(QuestionStatus.DRAFT),
                                            QuestionStatus.APPROVED,
                                            null,
                                            1,
                                        )
                                    } else {
                                        QuestionStore.move(
                                            id,
                                            setOf(QuestionStatus.DRAFT),
                                            QuestionStatus.REJECTED,
                                            "не",
                                            1,
                                        )
                                    }
                                }
                            }
                        }
                    }.awaitAll()
            }
        assertEquals(1, outcomes.count { it.isSuccess })
    }
}
