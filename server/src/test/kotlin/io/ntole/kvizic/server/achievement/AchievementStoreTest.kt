package io.ntole.kvizic.server.achievement

import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.region.ContentLanguages
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.db.PlayerTopicStats
import io.ntole.kvizic.server.db.Profiles
import io.ntole.kvizic.server.db.appTables
import io.ntole.kvizic.server.db.connectH2
import io.ntole.kvizic.server.db.h2Url
import io.ntole.kvizic.server.lobby.AnswerRecord
import io.ntole.kvizic.server.lobby.GameRecord
import io.ntole.kvizic.server.lobby.PlayerRecord
import io.ntole.kvizic.server.lobby.QuestionRecord
import io.ntole.kvizic.server.match.ResultWriter
import io.ntole.kvizic.server.question.BankFixtures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AchievementStoreTest {
    private val database =
        connectH2(h2Url("kvizic-achievements-${UUID.randomUUID()}"), Connection.TRANSACTION_READ_COMMITTED)
    private val db = Db(database)
    private val writer =
        ResultWriter(db, CoroutineScope(SupervisorJob()), LoggerFactory.getLogger("test"), clock = { 5_000 })

    init {
        transaction(database) {
            SchemaUtils.create(*appTables)
            BankFixtures.topic("LANGUAGE")
            listOf("ana", "boris").forEach { BankFixtures.player(it) }
            (1..10).forEach { BankFixtures.question("q$it", listOf("LANGUAGE")) }
        }
    }

    private fun <T> read(block: () -> T): T = runBlocking { db.query { block() } }

    @Test
    fun `every achievement has its words in every language, naming its goal`() {
        val languages = ContentLanguages.ALL + "en"
        Achievements.ALL.forEach { achievement ->
            assertEquals(languages.toSet(), achievement.title.keys, achievement.titleId)
            assertEquals(languages.toSet(), achievement.how.keys, achievement.titleId)
            if (achievement.goal > 1) {
                assertTrue(achievement.how.values.all { "${achievement.goal}" in it }, achievement.titleId)
            }
        }
    }

    @Test
    fun `a game earns the titles its totals reach, and a title kept stays when the totals fall short`() {
        val questions =
            (1..10).map { slot ->
                QuestionRecord(
                    "q$slot",
                    "LANGUAGE",
                    mapOf("ana" to AnswerRecord(true, 2_000), "boris" to AnswerRecord(slot <= 3, 1_000)),
                )
            }
        read {
            writer.write(
                GameRecord(
                    gameId = "g1",
                    kind = LobbyKind.PRIVATE,
                    settings = LobbySettingsDto(questionCount = 10),
                    startedAt = 1_000,
                    endedAt = 4_000,
                    endedEarly = false,
                    players =
                        listOf(
                            PlayerRecord(
                                "ana",
                                900,
                                correct = 10,
                                answered = 10,
                                rank = 1,
                                finished = true,
                                won = true,
                            ),
                            PlayerRecord(
                                "boris",
                                100,
                                correct = 3,
                                answered = 10,
                                rank = 2,
                                finished = true,
                                won = false,
                            ),
                        ),
                    questions = questions,
                    spectators = emptyList(),
                ),
            )
        }

        fun counts(id: String) =
            read {
                Profiles
                    .select(Profiles.perfectGames, Profiles.firstRights)
                    .where { Profiles.playerId eq id }
                    .single()
                    .let { it[Profiles.perfectGames] to it[Profiles.firstRights] }
            }
        assertEquals(1 to 7, counts("ana"), "a perfect game, and first right where boris was wrong")
        assertEquals(0 to 3, counts("boris"))
        assertEquals(setOf("title.lucky", "title.brain"), read { AchievementStore.earned("ana") })
        assertEquals(emptySet(), read { AchievementStore.earned("boris") })

        read {
            Profiles.update({ Profiles.playerId eq "ana" }) { row ->
                row[gamesWon] = 0
                row[perfectGames] = 0
            }
            PlayerTopicStats.deleteAll()
        }
        val shown = read { AchievementStore.of("ana") }.associateBy { it.id }
        assertEquals(Achievements.ALL.map { it.titleId }, shown.keys.toList())
        assertTrue(shown.getValue("title.lucky").earned && shown.getValue("title.brain").earned, "kept")
        assertEquals(7 to 25, shown.getValue("title.finger").let { it.progress to it.goal })
        assertEquals(false, shown.getValue("title.star").earned)
    }
}
