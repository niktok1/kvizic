package io.ntole.kvizic.server.match

import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.db.MatchPlayers
import io.ntole.kvizic.server.db.MatchQuestions
import io.ntole.kvizic.server.db.Matches
import io.ntole.kvizic.server.db.PlayerTopicStats
import io.ntole.kvizic.server.db.Players
import io.ntole.kvizic.server.db.Profiles
import io.ntole.kvizic.server.db.QuestionStats
import io.ntole.kvizic.server.db.SeenQuestions
import io.ntole.kvizic.server.db.appTables
import io.ntole.kvizic.server.db.connectH2
import io.ntole.kvizic.server.db.h2Url
import io.ntole.kvizic.server.lobby.AnswerRecord
import io.ntole.kvizic.server.lobby.GameRecord
import io.ntole.kvizic.server.lobby.PlayerRecord
import io.ntole.kvizic.server.lobby.QuestionRecord
import io.ntole.kvizic.server.player.AccountDeletion
import io.ntole.kvizic.server.question.BankFixtures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A finished game written to the database, alone and beside others ending at once. */
class ResultWriterTest {
    private val database =
        connectH2(h2Url("kvizic-results-${UUID.randomUUID()}"), Connection.TRANSACTION_READ_COMMITTED)
    private val db = Db(database)
    private val writer =
        ResultWriter(db, CoroutineScope(SupervisorJob()), LoggerFactory.getLogger("test"), clock = { 5_000 })

    init {
        transaction(database) {
            SchemaUtils.create(*appTables)
            BankFixtures.topic("GEOGRAPHY", createdAt = 1)
            BankFixtures.topic("SPORT", createdAt = 2)
            listOf("ana", "boris", "ceca").forEach { BankFixtures.player(it) }
            BankFixtures.question("q1", listOf("GEOGRAPHY"))
            BankFixtures.question("q2", listOf("SPORT"))
        }
    }

    private fun game(
        id: String = UUID.randomUUID().toString(),
        kind: LobbyKind = LobbyKind.PRIVATE,
        players: List<PlayerRecord> =
            listOf(
                PlayerRecord("ana", score = 170, correct = 2, answered = 2, rank = 1, finished = true, won = true),
                PlayerRecord("boris", score = -12, correct = 0, answered = 1, rank = 2, finished = true, won = false),
            ),
        spectators: List<String> = listOf("ceca"),
        difficulty: Difficulty = Difficulty.MEDIUM,
    ) = GameRecord(
        gameId = id,
        kind = kind,
        settings = LobbySettingsDto(questionCount = 5, topics = listOf("SPORT", "GEOGRAPHY"), difficulty = difficulty),
        startedAt = 1_000,
        endedAt = 4_000,
        endedEarly = false,
        players = players,
        questions =
            listOf(
                QuestionRecord(
                    "q1",
                    "GEOGRAPHY",
                    mapOf("ana" to AnswerRecord(true, 2_000), "boris" to AnswerRecord(false, 900)),
                    silent = 1,
                ),
                QuestionRecord("q2", "SPORT", mapOf("ana" to AnswerRecord(true, 3_000))),
            ),
        spectators = spectators,
    )

    private fun write(record: GameRecord) = runBlocking { db.query { writer.write(record) } }

    private fun <T> read(block: () -> T): T = transaction(database) { block() }

    @Test
    fun `a game is written whole, every count in its place`() {
        write(game())

        read {
            val match = Matches.selectAll().single()
            assertEquals("SPORT,GEOGRAPHY", match[Matches.topics])
            assertEquals(2, match[Matches.participants])
            assertEquals(
                listOf("ana" to 1, "boris" to 2),
                MatchPlayers
                    .selectAll()
                    .map {
                        it[MatchPlayers.playerId] to it[MatchPlayers.standing]
                    }.sortedBy { it.first },
            )
            assertEquals(
                listOf("q1", "q2"),
                MatchQuestions
                    .selectAll()
                    .sortedBy {
                        it[MatchQuestions.slot]
                    }.map { it[MatchQuestions.questionId] },
            )

            val ana = Profiles.selectAll().where { Profiles.playerId eq "ana" }.single()
            assertEquals(1, ana[Profiles.gamesPlayed])
            assertEquals(1, ana[Profiles.gamesWon])
            assertEquals(2, ana[Profiles.answersGiven])
            assertEquals(2, ana[Profiles.answersCorrect])
            val ceca = Profiles.selectAll().where { Profiles.playerId eq "ceca" }.single()
            assertEquals(0, ceca[Profiles.gamesPlayed], "a spectator played nothing")

            val q1 = QuestionStats.selectAll().where { QuestionStats.questionId eq "q1" }.single()
            assertEquals(
                listOf(1, 2, 1),
                listOf(q1[QuestionStats.timesShown], q1[QuestionStats.timesAnswered], q1[QuestionStats.timesCorrect]),
            )
            assertEquals(2_000L, q1[QuestionStats.totalCorrectMs])
            assertEquals(1, q1[QuestionStats.timesUnanswered])

            assertEquals(6, SeenQuestions.selectAll().count(), "three who saw two questions")
            val topics =
                PlayerTopicStats.selectAll().associate {
                    (it[PlayerTopicStats.playerId] to it[PlayerTopicStats.topicId]) to
                        (it[PlayerTopicStats.answered] to it[PlayerTopicStats.correct])
                }
            assertEquals(1 to 1, topics["ana" to "GEOGRAPHY"])
            assertEquals(1 to 0, topics["boris" to "GEOGRAPHY"])
            assertNull(topics["boris" to "SPORT"], "no answer, no line")
        }
    }

    @Test
    fun `a second game adds to the first`() {
        write(game())
        write(game())

        read {
            val ana = Profiles.selectAll().where { Profiles.playerId eq "ana" }.single()
            assertEquals(2, ana[Profiles.gamesPlayed])
            assertEquals(4, ana[Profiles.answersCorrect])
            assertEquals(
                2,
                QuestionStats.selectAll().where { QuestionStats.questionId eq "q2" }.single()[QuestionStats.timesShown],
            )
            val seen =
                SeenQuestions
                    .selectAll()
                    .where {
                        SeenQuestions.playerId eq "ana"
                    }.map { it[SeenQuestions.timesSeen] }
            assertEquals(listOf(2, 2), seen)
        }
    }

    @Test
    fun `games ending at once lose no count`() {
        runBlocking(Dispatchers.IO) {
            (1..8).map { async { db.query { writer.write(game()) } } }.awaitAll()
        }
        read {
            assertEquals(8, Profiles.selectAll().where { Profiles.playerId eq "ana" }.single()[Profiles.gamesPlayed])
            assertEquals(
                8,
                QuestionStats.selectAll().where { QuestionStats.questionId eq "q1" }.single()[QuestionStats.timesShown],
            )
            assertEquals(
                8,
                SeenQuestions
                    .selectAll()
                    .where {
                        SeenQuestions.playerId eq "ceca"
                    }.map { it[SeenQuestions.timesSeen] }
                    .first(),
            )
        }
    }

    @Test
    fun `each level keeps its own solo best`() {
        fun solo(
            score: Int,
            level: Difficulty,
        ) = game(
            kind = LobbyKind.SOLO,
            players =
                listOf(
                    PlayerRecord("ana", score, correct = 2, answered = 2, rank = 1, finished = true, won = false),
                ),
            spectators = emptyList(),
            difficulty = level,
        )
        write(solo(600, Difficulty.MEDIUM))
        write(solo(900, Difficulty.EASY))
        write(solo(300, Difficulty.HARD))
        write(solo(200, Difficulty.HARD))
        read {
            val ana = Profiles.selectAll().where { Profiles.playerId eq "ana" }.single()
            assertEquals(
                listOf(900, 600, 300),
                listOf(ana[Profiles.soloBestEasyScore], ana[Profiles.soloBestScore], ana[Profiles.soloBestHardScore]),
            )
        }
    }

    @Test
    fun `a solo run keeps its best, and only a better one replaces it`() {
        fun solo(score: Int) =
            game(
                kind = LobbyKind.SOLO,
                players =
                    listOf(
                        PlayerRecord("ana", score, correct = 2, answered = 2, rank = 1, finished = true, won = false),
                    ),
                spectators = emptyList(),
            )
        write(solo(600))
        write(solo(400))
        read {
            val ana = Profiles.selectAll().where { Profiles.playerId eq "ana" }.single()
            assertEquals(600, ana[Profiles.soloBestScore])
            assertEquals(2, ana[Profiles.soloRuns])
            assertEquals(0, ana[Profiles.gamesWon])
        }
        write(solo(750))
        read {
            assertEquals(
                750,
                Profiles
                    .select(Profiles.soloBestScore)
                    .where {
                        Profiles.playerId eq "ana"
                    }.single()[Profiles.soloBestScore],
            )
        }
    }

    @Test
    fun `a player whose account went before the game was written is left out of it`() {
        read { AccountDeletion.delete("boris") }
        write(game())

        read {
            assertEquals(listOf("ana"), MatchPlayers.selectAll().map { it[MatchPlayers.playerId] })
            assertEquals(0, Players.selectAll().where { Players.id eq "boris" }.count())
            assertEquals(
                2,
                QuestionStats
                    .selectAll()
                    .where {
                        QuestionStats.questionId eq "q1"
                    }.single()[QuestionStats.timesAnswered],
                "the question's own count keeps his answer",
            )
        }
    }
}
