package io.ntole.kvizic.server.match

import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.db.MatchPlayers
import io.ntole.kvizic.server.db.MatchQuestions
import io.ntole.kvizic.server.db.Matches
import io.ntole.kvizic.server.db.PlayerTopicStats
import io.ntole.kvizic.server.db.Players
import io.ntole.kvizic.server.db.Profiles
import io.ntole.kvizic.server.db.QuestionStats
import io.ntole.kvizic.server.db.Questions
import io.ntole.kvizic.server.db.SeenQuestions
import io.ntole.kvizic.server.lobby.GameRecord
import io.ntole.kvizic.server.lobby.ResultSink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.Logger

/**
 * Writes a finished game, in one transaction, off the lobby's loop: the match, each player's line of it,
 * their profile's totals, the questions' stats, who has now seen what, and each player's topics. Every
 * counter is an SQL increment and every row is taken in key order, so two games ending together never
 * lose a count nor deadlock; two inserting the same new row fail on its key, and the rerun counts it.
 *
 * The players' rows are locked first: a player whose account went meanwhile is left out, and one whose
 * deletion waits on the lock loses the rows written here with the rest of their account.
 */
class ResultWriter(
    private val db: Db,
    private val scope: CoroutineScope,
    private val log: Logger,
    private val clock: () -> Long = System::currentTimeMillis,
) : ResultSink {
    override fun submit(record: GameRecord) {
        scope.launch {
            try {
                db.query { write(record) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                log.warn("game ${record.gameId} could not be recorded: ${failed::class.simpleName}")
            }
        }
    }

    /** Writes [record]. Must run inside a transaction. */
    fun write(record: GameRecord) {
        val now = clock()
        val everyone = (record.players.map { it.playerId } + record.spectators).distinct().sorted()
        val alive =
            Players
                .select(Players.id)
                .where { Players.id inList everyone }
                .orderBy(Players.id)
                .forUpdate()
                .map { it[Players.id] }
                .toSet()
        val questionIds = record.questions.map { it.questionId }.distinct()
        val known =
            Questions
                .select(
                    Questions.id,
                ).where { Questions.id inList questionIds }
                .map { it[Questions.id] }
                .toSet()
        val questions = record.questions.filter { it.questionId in known }
        val players = record.players.filter { it.playerId in alive }.sortedBy { it.playerId }

        Matches.insert { row ->
            row[id] = record.gameId
            row[kind] = record.kind
            row[startedAt] = record.startedAt
            row[endedAt] = record.endedAt
            row[questionCount] = record.questions.size
            row[secondsPerQuestion] = record.settings.secondsPerQuestion
            row[topics] = record.settings.topics.joinToString(",")
            row[participants] = record.players.size
            row[endedEarly] = record.endedEarly
        }
        MatchPlayers.batchInsert(players, shouldReturnGeneratedValues = false) { player ->
            this[MatchPlayers.matchId] = record.gameId
            this[MatchPlayers.playerId] = player.playerId
            this[MatchPlayers.score] = player.score
            this[MatchPlayers.correct] = player.correct
            this[MatchPlayers.answered] = player.answered
            this[MatchPlayers.standing] = player.rank
            this[MatchPlayers.finished] = player.finished
        }
        MatchQuestions.batchInsert(
            record.questions.withIndex().filter {
                it.value.questionId in known
            },
            shouldReturnGeneratedValues = false,
        ) { (slot, question) ->
            this[MatchQuestions.matchId] = record.gameId
            this[MatchQuestions.slot] = slot
            this[MatchQuestions.questionId] = question.questionId
        }

        players.forEach { player ->
            Profiles.update({ Profiles.playerId eq player.playerId }) { row ->
                row[gamesPlayed] = gamesPlayed + 1
                if (player.won) row[gamesWon] = gamesWon + 1
                row[answersGiven] = answersGiven + player.answered
                row[answersCorrect] = answersCorrect + player.correct
                if (record.kind == LobbyKind.SOLO) row[soloRuns] = soloRuns + 1
            }
            if (record.kind == LobbyKind.SOLO && player.finished) {
                // A compare-and-set: only a better run at the same level replaces its best.
                val (best, bestAt) = Profiles.soloBest(record.settings.difficulty)
                Profiles.update({
                    (Profiles.playerId eq player.playerId) and (best.isNull() or (best less player.score))
                }) { row ->
                    row[best] = player.score
                    row[bestAt] = now
                }
            }
        }

        val byQuestion = questions.groupBy { it.questionId }
        ensureRows(QuestionStats, byQuestion.keys.sorted()) { id ->
            QuestionStats.insert { row -> row[questionId] = id }
        }
        byQuestion.keys.sorted().forEach { id ->
            val asked = byQuestion.getValue(id)
            val answers = asked.flatMap { it.answers.values }
            val right = answers.filter { it.correct }
            QuestionStats.update({ QuestionStats.questionId eq id }) { row ->
                row[timesShown] = timesShown + asked.size
                row[timesAnswered] = timesAnswered + answers.size
                row[timesCorrect] = timesCorrect + right.size
                row[totalCorrectMs] = totalCorrectMs + right.sumOf { it.timeMs }
                row[timesUnanswered] = timesUnanswered + asked.sumOf { it.silent }
                row[lastShownAt] = now
            }
        }

        val watchers = everyone.filter { it in alive }
        watchers.forEach { playerId -> markSeen(playerId, byQuestion.keys.sorted(), now) }

        players.forEach { player ->
            val byTopic =
                questions
                    .groupBy { it.topicId }
                    .mapValues { (_, asked) -> asked.mapNotNull { it.answers[player.playerId] } }
                    .filterValues { it.isNotEmpty() }
            if (byTopic.isEmpty()) return@forEach
            val topicIds = byTopic.keys.sorted()
            val existing =
                PlayerTopicStats
                    .select(PlayerTopicStats.topicId)
                    .where {
                        (PlayerTopicStats.playerId eq player.playerId) and
                            (PlayerTopicStats.topicId inList topicIds)
                    }.map { it[PlayerTopicStats.topicId] }
                    .toSet()
            topicIds.filterNot { it in existing }.forEach { topic ->
                PlayerTopicStats.insert { row ->
                    row[playerId] = player.playerId
                    row[topicId] = topic
                }
            }
            topicIds.forEach { topic ->
                val answers = byTopic.getValue(topic)
                PlayerTopicStats.update({
                    (PlayerTopicStats.playerId eq player.playerId) and
                        (PlayerTopicStats.topicId eq topic)
                }) { row ->
                    row[answered] = answered + answers.size
                    row[correct] = correct + answers.count { it.correct }
                }
            }
        }
    }

    private fun markSeen(
        playerId: String,
        questionIds: List<String>,
        now: Long,
    ) {
        if (questionIds.isEmpty()) return
        val existing =
            SeenQuestions
                .select(SeenQuestions.questionId)
                .where { (SeenQuestions.playerId eq playerId) and (SeenQuestions.questionId inList questionIds) }
                .map { it[SeenQuestions.questionId] }
                .toSet()
        val fresh = questionIds.filterNot { it in existing }
        SeenQuestions.batchInsert(fresh, shouldReturnGeneratedValues = false) { questionId ->
            this[SeenQuestions.playerId] = playerId
            this[SeenQuestions.questionId] = questionId
            this[SeenQuestions.lastSeenAt] = now
        }
        if (existing.isNotEmpty()) {
            SeenQuestions.update({
                (SeenQuestions.playerId eq playerId) and
                    (SeenQuestions.questionId inList existing.sorted())
            }) { row ->
                row[timesSeen] = timesSeen + 1
                row[lastSeenAt] = now
            }
        }
    }

    private fun ensureRows(
        table: QuestionStats,
        ids: List<String>,
        insert: (String) -> Unit,
    ) {
        if (ids.isEmpty()) return
        val existing =
            table
                .select(
                    table.questionId,
                ).where { table.questionId inList ids }
                .map { it[table.questionId] }
                .toSet()
        ids.filterNot { it in existing }.forEach(insert)
    }
}
