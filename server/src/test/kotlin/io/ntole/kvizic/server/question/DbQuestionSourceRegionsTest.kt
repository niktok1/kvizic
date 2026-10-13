package io.ntole.kvizic.server.question

import com.zaxxer.hikari.HikariDataSource
import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.region.ContentLanguages.BOSNIAN
import io.ntole.kvizic.core.region.ContentLanguages.CROATIAN
import io.ntole.kvizic.core.region.ContentLanguages.SERBIAN
import io.ntole.kvizic.core.region.Country
import io.ntole.kvizic.core.region.Country.BA
import io.ntole.kvizic.core.region.Country.HR
import io.ntole.kvizic.core.region.Country.ME
import io.ntole.kvizic.core.region.Country.MK
import io.ntole.kvizic.core.region.Country.RS
import io.ntole.kvizic.core.region.Country.SI
import io.ntole.kvizic.core.region.Country.UNKNOWN
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.db.Migrations
import io.ntole.kvizic.server.db.QuestionCountries
import io.ntole.kvizic.server.db.QuestionOptionTranslations
import io.ntole.kvizic.server.db.QuestionTranslations
import io.ntole.kvizic.server.db.Questions
import io.ntole.kvizic.server.db.SchemaTestEngine
import io.ntole.kvizic.server.db.serverPool
import io.ntole.kvizic.server.lobby.PickRequest
import io.ntole.kvizic.server.lobby.PickResult
import io.ntole.kvizic.server.lobby.QuestionText
import io.ntole.kvizic.server.lobby.textIn
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How the picker reads the regions: who may be asked a scoped question, the local share of a game, and the
 * translations a question comes with. On a migrated database, on H2 and, in the server-postgres CI job, on
 * PostgreSQL too, since the filter is SQL the live database runs.
 */
@RunWith(Parameterized::class)
internal class DbQuestionSourceRegionsTest(
    private val engine: SchemaTestEngine,
) {
    private lateinit var pool: HikariDataSource
    private lateinit var database: Database

    @BeforeTest
    fun open() {
        pool = engine.emptyDatabase("picker-regions").serverPool()
        Migrations.migrate(pool)
        database = Database.connect(pool)
        // The migrations write the topics, GEOGRAPHY, HISTORY and SPORT among them.
        bank {
            player("ana", soloBest = 640)
            player("boris")
        }
    }

    @AfterTest
    fun close() {
        TransactionManager.closeAndUnregister(database)
        pool.close()
    }

    private fun bank(block: BankFixtures.() -> Unit) = transaction(database) { BankFixtures.block() }

    private fun pick(
        count: Int,
        countries: Set<Country> = setOf(RS),
        topics: List<String> = emptyList(),
        players: Set<String> = setOf("ana", "boris"),
        languages: Set<String> = setOf(SERBIAN),
        difficulty: Difficulty = Difficulty.MEDIUM,
        solo: String? = null,
        localShare: Double = 0.2,
        seed: Int = 3,
    ): PickResult =
        runBlocking {
            DbQuestionSource(Db(database), Random(seed), localShare).pick(
                PickRequest(
                    count = count,
                    topics = topics,
                    players = players,
                    soloPlayer = solo,
                    difficulty = difficulty,
                    countries = countries,
                    languages = languages,
                ),
            )
        }

    private fun PickResult.ids() = questions.map { it.questionId }

    /** Questions named `<prefix>1` on, scoped to [countries] (none: common). */
    private fun BankFixtures.questions(
        prefix: String,
        count: Int,
        vararg countries: Country,
        topic: String = "SPORT",
        difficulty: Difficulty = Difficulty.MEDIUM,
    ) = (1..count).map { "$prefix$it" }.onEach { id ->
        question(id, listOf(topic), difficulty = difficulty)
        scope(id, *countries)
    }

    private fun scope(
        id: String,
        vararg countries: Country,
    ) {
        countries.forEach { country ->
            QuestionCountries.insert { row ->
                row[questionId] = id
                row[QuestionCountries.country] = country
            }
        }
    }

    /** A translation of [id] into [language], made from [revision], its answers by slot in [slots]' order. */
    private fun translate(
        id: String,
        language: String,
        revision: Int = 1,
        slots: List<Int> = listOf(0, 1, 2, 3),
        explanation: String? = null,
    ) {
        QuestionTranslations.insert { row ->
            row[questionId] = id
            row[QuestionTranslations.language] = language
            row[text] = "$language $id?"
            row[QuestionTranslations.explanation] = explanation
            row[sourceRevision] = revision
            row[updatedAt] = 0
        }
        slots.forEach { at ->
            QuestionOptionTranslations.insert { row ->
                row[questionId] = id
                row[QuestionOptionTranslations.language] = language
                row[slot] = at
                row[text] = "$language $id $at"
            }
        }
    }

    /** An edit of [id], which moves it to [revision] and leaves translations made from an earlier one stale. */
    private fun edit(
        id: String,
        revision: Int = 2,
    ) {
        Questions.update({ Questions.id eq id }) { row -> row[Questions.revision] = revision }
    }

    private fun sourceIn(
        id: String,
        language: String,
    ) {
        Questions.update({ Questions.id eq id }) { row -> row[Questions.language] = language }
    }

    @Test
    fun `a room of one country gets its own questions and the common ones, never another country's`() {
        bank {
            questions("c", 3)
            questions("rs", 3, RS)
            questions("hr", 3, HR)
            questions("ba", 3, BA)
        }
        assertEquals(
            setOf("c1", "c2", "c3", "rs1", "rs2", "rs3"),
            pick(20, localShare = 0.0).ids().toSet(),
            "a 1.0 client's room: the default country is RS",
        )
        assertEquals(setOf("c1", "c2", "c3", "hr1", "hr2", "hr3"), pick(20, setOf(HR)).ids().toSet())
    }

    @Test
    fun `a mixed room never gets a scoped question, and is shorter rather than get one`() {
        bank {
            questions("c", 4)
            questions("rs", 5, RS)
            questions("hr", 5, HR)
            questions("me", 5, ME)
        }
        listOf(setOf(RS, HR), setOf(RS, ME), setOf(HR, ME), setOf(RS, HR, ME)).forEach { room ->
            val picked = pick(10, room)
            assertEquals(setOf("c1", "c2", "c3", "c4"), picked.ids().toSet(), "$room")
            assertTrue(picked.shortened, "$room")
        }
    }

    @Test
    fun `a question scoped to several countries is asked of players all among them, and of no others`() {
        bank {
            questions("c", 1)
            questions("shared", 1, RS, ME, BA)
        }
        mapOf(
            setOf(RS) to true,
            setOf(ME) to true,
            setOf(RS, ME) to true,
            setOf(RS, ME, BA) to true,
            setOf(RS, HR) to false,
            setOf(HR) to false,
            setOf(SI) to false,
            setOf(MK, BA) to false,
            setOf(RS, ME, BA, SI) to false,
        ).forEach { (room, asked) ->
            assertEquals(asked, "shared1" in pick(5, room).ids(), "$room")
        }
    }

    @Test
    fun `a country the server does not know gets the common questions only`() {
        bank {
            questions("c", 2)
            questions("rs", 2, RS)
            questions("everywhere", 2, RS, HR, BA, SI, MK, ME)
        }
        listOf(setOf(UNKNOWN), setOf(RS, UNKNOWN), emptySet()).forEach { room ->
            assertEquals(setOf("c1", "c2"), pick(10, room).ids().toSet(), "$room")
        }
        assertEquals(6, pick(10, setOf(RS)).questions.size, "RS alone gets the six")
    }

    @Test
    fun `the top-up from other topics keeps to the rule, and takes the common ones first`() {
        bank {
            questions("geo", 2, topic = "GEOGRAPHY")
            questions("c", 3, topic = "HISTORY")
            questions("rs", 3, RS, topic = "HISTORY")
            questions("hr", 3, HR, topic = "HISTORY")
        }
        val five = pick(5, topics = listOf("GEOGRAPHY"))
        assertTrue(five.toppedUp)
        assertEquals(setOf("geo1", "geo2", "c1", "c2", "c3"), five.ids().toSet(), "the common ones first")

        val eight = pick(8, topics = listOf("GEOGRAPHY"))
        assertEquals(
            setOf("geo1", "geo2", "c1", "c2", "c3", "rs1", "rs2", "rs3"),
            eight.ids().toSet(),
            "then the local ones rather than a shorter game, never another country's",
        )
        assertFalse(eight.shortened)

        val mixed = pick(8, setOf(RS, HR), topics = listOf("GEOGRAPHY"))
        assertEquals(setOf("geo1", "geo2", "c1", "c2", "c3"), mixed.ids().toSet())
        assertTrue(mixed.shortened)
    }

    @Test
    fun `a room of one country gets its share of local questions, and the levels add up as ever`() {
        bank {
            DifficultyMix.LEVELS.forEach { level ->
                val name = level.name.lowercase()
                questions("c-$name-", 20, difficulty = level)
                questions("rs-$name-", 6, RS, difficulty = level)
            }
        }
        listOf(5, 10, 15, 20).forEach { count ->
            DifficultyMix.LEVELS.forEach { chosen ->
                // A share that does not come out whole falls at random, so a game's levels are one of these.
                val mixes = (1..200).map { DifficultyMix(Random(it)).quotas(chosen, count).filterValues { n -> n > 0 } }
                (1..12).forEach { seed ->
                    val picked = pick(count, difficulty = chosen, seed = seed).questions
                    val shown = "$count at $chosen, seed $seed"
                    assertEquals(count, picked.size, shown)
                    assertEquals((count * 0.2).roundToInt(), picked.count { it.questionId.startsWith("rs") }, shown)
                    assertContains(mixes, picked.groupingBy { it.difficulty }.eachCount(), shown)
                    val ramp = picked.sortedBy { DifficultyMix.LEVELS.indexOf(it.difficulty) }
                    assertEquals(ramp, picked, "easy to hard, $shown")
                }
            }
        }
    }

    @Test
    fun `a share the topics cannot fill takes what they hold, and the common ones fill the game`() {
        bank {
            questions("c", 12)
            questions("rs", 1, RS)
            questions("rs-history", 4, RS, topic = "HISTORY")
        }
        val picked = pick(10, topics = listOf("SPORT"))
        assertEquals(10, picked.questions.size)
        assertEquals(listOf("rs1"), picked.ids().filter { it.startsWith("rs") }, "the one local question in SPORT")
        assertFalse(picked.toppedUp)
    }

    @Test
    fun `no local share forces none, though local questions still come as they fall`() {
        bank {
            questions("c", 10)
            questions("rs", 5, RS).forEach { id -> seen("ana", id, at = 100) }
        }
        assertEquals(0, pick(10, localShare = 0.0).ids().count { it.startsWith("rs") }, "the unseen common ones first")
        assertEquals(2, pick(10).ids().count { it.startsWith("rs") }, "a share takes them, seen as they are")
        assertEquals(15, pick(15, localShare = 0.0).questions.size, "and fills a game with them")
    }

    @Test
    fun `a local question the share leaves comes only after the common ones`() {
        bank {
            questions("c", 8)
            questions("rs", 10, RS)
        }
        (1..10).forEach { seed ->
            assertEquals(2, pick(10, seed = seed).ids().count { it.startsWith("rs") }, "seed $seed")
        }
        assertEquals(18, pick(18).questions.size, "never a shorter game for the share")
    }

    @Test
    fun `a solo run is asked by its player's country`() {
        bank {
            questions("c", 2)
            questions("rs", 2, RS)
            questions("hr", 2, HR)
        }
        val solo = pick(10, setOf(HR), players = setOf("ana"), solo = "ana")
        assertEquals(setOf("c1", "c2", "hr1", "hr2"), solo.ids().toSet())
        assertEquals(640, solo.soloBestBefore)
    }

    @Test
    fun `a question comes with its translations, their answers in the source's slots`() {
        bank {
            question("q", listOf("SPORT"), correct = 2, explanation = "Зато.")
            translate("q", CROATIAN, explanation = "hr explanation")
            translate("q", BOSNIAN, slots = listOf(3, 1, 0, 2))
        }
        val q = pick(1).questions.single()
        assertEquals(SERBIAN, q.language)
        assertEquals(setOf(CROATIAN, BOSNIAN), q.texts.keys)
        assertEquals(
            QuestionText("hr q?", listOf("hr q 0", "hr q 1", "hr q 2", "hr q 3"), "hr explanation"),
            q.textIn(CROATIAN),
        )
        assertEquals(listOf("bs q 0", "bs q 1", "bs q 2", "bs q 3"), q.textIn(BOSNIAN).options, "by slot")
        assertEquals(2, q.correct)
        assertEquals(QuestionText("Питање q?", listOf("q-right", "q-a", "q-b", "q-c"), "Зато."), q.textIn(SERBIAN))
    }

    @Test
    fun `a stale translation is left out, and its reader reads the next text along the chain`() {
        bank {
            question("q", listOf("SPORT"))
            translate("q", CROATIAN, revision = 1)
            translate("q", BOSNIAN, revision = 2)
            edit("q", revision = 2)
            question("old", listOf("HISTORY"))
            translate("old", CROATIAN, revision = 1)
            translate("old", BOSNIAN, revision = 1)
            edit("old", revision = 3)
        }
        val byId = pick(2).questions.associateBy { it.questionId }
        val q = byId.getValue("q")
        assertEquals(setOf(BOSNIAN), q.texts.keys)
        assertEquals("bs q?", q.textIn(CROATIAN).text, "hr reads bs before the source")
        val old = byId.getValue("old")
        assertEquals(emptyMap(), old.texts)
        assertEquals("Питање old?", old.textIn(CROATIAN).text, "then the source")
    }

    @Test
    fun `a translation without an answer for each slot is left out`() {
        bank {
            question("q", listOf("SPORT"))
            translate("q", CROATIAN, slots = listOf(0, 1, 2))
            translate("q", BOSNIAN, slots = listOf(0, 1, 2, 5))
        }
        assertEquals(emptyMap(), pick(1).questions.single().texts)
    }

    @Test
    fun `among questions seen alike, one in every language the players read comes first, never in place of one`() {
        bank {
            questions("plain", 4)
            questions("translated", 3).forEach { id -> translate(id, CROATIAN) }
            question("stale", listOf("SPORT"))
            translate("stale", CROATIAN)
            edit("stale")
        }
        val languages = setOf(SERBIAN, CROATIAN)
        (1..10).forEach { seed ->
            assertEquals(
                setOf("translated1", "translated2", "translated3"),
                pick(3, languages = languages, seed = seed).ids().toSet(),
                "seed $seed",
            )
        }
        assertEquals(8, pick(8, languages = languages).questions.size, "a game is never shorter for it")
    }

    @Test
    fun `being unseen comes before being translated`() {
        bank {
            question("plain", listOf("SPORT"))
            question("translated", listOf("SPORT"))
            translate("translated", CROATIAN)
            seen("ana", "translated", at = 100)
        }
        assertEquals(listOf("plain"), pick(1, languages = setOf(SERBIAN, CROATIAN)).ids())
    }

    @Test
    fun `a question reads in its source's language without a translation into it`() {
        bank {
            question("serbian", listOf("SPORT"))
            question("croatian", listOf("SPORT"))
            sourceIn("croatian", CROATIAN)
            scope("croatian", HR)
            question("both", listOf("SPORT"))
            translate("both", CROATIAN)
        }
        assertEquals(
            setOf("croatian", "both"),
            pick(2, setOf(HR), languages = setOf(CROATIAN), localShare = 0.0).ids().toSet(),
        )
        assertEquals(listOf("both"), pick(1, setOf(HR), languages = setOf(CROATIAN, SERBIAN), localShare = 0.0).ids())
        assertEquals(CROATIAN, pick(3, setOf(HR)).questions.single { it.questionId == "croatian" }.language)
    }

    @Test
    fun `a ui tag the players read is the content language it reads`() {
        bank {
            question("plain", listOf("SPORT"))
            question("translated", listOf("SPORT"))
            translate("translated", BOSNIAN)
            seen("ana", "plain", at = 100)
            seen("ana", "translated", at = 100)
        }
        assertEquals(listOf("translated"), pick(1, languages = setOf("BS")).ids())
        assertEquals(listOf("translated"), pick(1, languages = setOf("sr-Latn", "bs")).ids(), "sr-Latn reads sr-Cyrl")
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun engines(): List<SchemaTestEngine> = SchemaTestEngine.all()
    }
}
