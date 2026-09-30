package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.db.appTables
import io.ntole.kvizic.server.db.connectH2
import io.ntole.kvizic.server.db.h2Url
import io.ntole.kvizic.server.lobby.PickRequest
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How a game's questions are picked from the bank. */
class DbQuestionSourceTest {
    private val database = connectH2(h2Url("kvizic-picker-${UUID.randomUUID()}"), Connection.TRANSACTION_READ_COMMITTED)
    private val source = DbQuestionSource(Db(database), Random(3))

    init {
        transaction(database) {
            SchemaUtils.create(*appTables)
            BankFixtures.topic("GEOGRAPHY", createdAt = 1)
            BankFixtures.topic("HISTORY", createdAt = 2)
            BankFixtures.topic("SPORT", createdAt = 3)
            BankFixtures.player("ana", soloBest = 640)
            BankFixtures.player("boris")
        }
    }

    private fun bank(block: BankFixtures.() -> Unit) = transaction(database) { BankFixtures.block() }

    private fun pick(
        count: Int,
        topics: List<String> = emptyList(),
        players: Set<String> = setOf("ana", "boris"),
        solo: String? = null,
    ) = runBlocking { source.pick(PickRequest(count, topics, players, solo)) }

    @Test
    fun `only approved questions are asked, whole, with their answers in stored order`() {
        bank {
            question("q1", listOf("GEOGRAPHY"), correct = 2)
            question("q2", listOf("GEOGRAPHY"), status = QuestionStatus.DRAFT)
            question("q3", listOf("HISTORY"), status = QuestionStatus.RETIRED)
            question("q4", listOf("HISTORY"), status = QuestionStatus.SUSPENDED)
        }
        val picked = pick(10)
        assertEquals(listOf("q1"), picked.questions.map { it.questionId })
        val q1 = picked.questions.single()
        assertEquals(listOf("q1-right", "q1-a", "q1-b", "q1-c"), q1.options)
        assertEquals(2, q1.correct)
        assertEquals("GEOGRAPHY", q1.topicId)
        assertTrue(picked.shortened)
    }

    @Test
    fun `two and three answer questions come as they are`() {
        bank {
            question("tf", listOf("SPORT"), options = listOf("Тачно", "Нетачно"), correct = 1)
            question("three", listOf("SPORT"), options = listOf("a", "b", "c"))
        }
        val byId = pick(2).questions.associateBy { it.questionId }
        assertEquals(2, byId.getValue("tf").options.size)
        assertEquals(1, byId.getValue("tf").correct)
        assertEquals(3, byId.getValue("three").options.size)
    }

    @Test
    fun `the topics asked for come first, and other topics fill a game they cannot`() {
        bank {
            (1..3).forEach { question("g$it", listOf("GEOGRAPHY")) }
            (1..5).forEach { question("h$it", listOf("HISTORY")) }
        }
        val inTopic = pick(3, topics = listOf("GEOGRAPHY"))
        assertEquals(setOf("g1", "g2", "g3"), inTopic.questions.map { it.questionId }.toSet())
        assertFalse(inTopic.toppedUp)

        val toppedUp = pick(5, topics = listOf("GEOGRAPHY"))
        assertEquals(5, toppedUp.questions.size)
        assertTrue(toppedUp.questions.map { it.questionId }.containsAll(listOf("g1", "g2", "g3")))
        assertTrue(toppedUp.toppedUp)
        assertFalse(toppedUp.shortened)

        val tooMany = pick(20)
        assertEquals(8, tooMany.questions.size)
        assertTrue(tooMany.shortened)
    }

    @Test
    fun `the questions the lobby saw least come first, then those seen longest ago`() {
        bank {
            (1..6).forEach { question("q$it", listOf("SPORT")) }
            seen("ana", "q1", at = 100)
            seen("boris", "q1", at = 100)
            seen("ana", "q2", at = 50)
            seen("boris", "q3", at = 900)
        }
        val picked = pick(4).questions.map { it.questionId }.toSet()
        assertEquals(setOf("q4", "q5", "q6", "q2"), picked, "the unseen, then the one seen once longest ago")
    }

    @Test
    fun `a game goes from easy to hard`() {
        bank {
            question("hard", listOf("SPORT"), difficulty = Difficulty.HARD)
            question("easy", listOf("SPORT"), difficulty = Difficulty.EASY)
            question("medium", listOf("SPORT"), difficulty = Difficulty.MEDIUM)
        }
        assertEquals(listOf("easy", "medium", "hard"), pick(3).questions.map { it.questionId })
    }

    @Test
    fun `a solo run carries its player's best so far`() {
        bank { question("q1", listOf("SPORT")) }
        assertEquals(640, pick(1, players = setOf("ana"), solo = "ana").soloBestBefore)
        assertNull(pick(1, players = setOf("boris"), solo = "boris").soloBestBefore)
        assertNull(pick(1).soloBestBefore, "not a solo run")
    }

    @Test
    fun `a question stored wrong is left out rather than asked`() {
        bank {
            question("broken", listOf("SPORT"), options = listOf("only one"))
            question("fine", listOf("SPORT"))
        }
        assertEquals(listOf("fine"), pick(2).questions.map { it.questionId })
    }
}
