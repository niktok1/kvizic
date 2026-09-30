package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.db.Profiles
import io.ntole.kvizic.server.db.QuestionOptions
import io.ntole.kvizic.server.db.QuestionTopics
import io.ntole.kvizic.server.db.Questions
import io.ntole.kvizic.server.db.SeenQuestions
import io.ntole.kvizic.server.db.Topics
import io.ntole.kvizic.server.lobby.GameQuestion
import io.ntole.kvizic.server.lobby.PickRequest
import io.ntole.kvizic.server.lobby.PickResult
import io.ntole.kvizic.server.lobby.QuestionSource
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.random.Random

/**
 * Picks a game's questions from the bank: approved ones in the topics asked for, those the lobby's
 * players have seen least first (by how many of them saw each, then by when last), ties at random, then
 * asked from easy to hard. When the topics hold too few, the rest come from every other topic
 * ([PickResult.toppedUp]); when the whole bank does, the game is shorter ([PickResult.shortened]).
 *
 * Reads only, in one transaction, before the game starts, never while it runs.
 */
class DbQuestionSource(
    private val db: Db,
    private val random: Random = Random.Default,
) : QuestionSource {
    private data class Candidate(
        val id: String,
        val difficulty: Difficulty,
        val seenBy: Long,
        val lastSeen: Long?,
    )

    override suspend fun pick(request: PickRequest): PickResult =
        db.query {
            val inTopics = leastSeen(candidates(request.topics, request.players), request.count)
            var toppedUp = false
            val picked =
                if (inTopics.size < request.count && request.topics.isNotEmpty()) {
                    val taken = inTopics.map { it.id }.toSet()
                    val others = candidates(emptyList(), request.players).filter { it.id !in taken }
                    val more = leastSeen(others, request.count - inTopics.size)
                    toppedUp = more.isNotEmpty()
                    inTopics + more
                } else {
                    inTopics
                }
            val ramp = picked.sortedBy { rampOrder(it.difficulty) }
            val questions = load(ramp.map { it.id })
            PickResult(
                questions = questions,
                toppedUp = toppedUp,
                shortened = questions.size < request.count,
                soloBestBefore = request.soloPlayer?.let(::soloBest),
            )
        }

    private fun candidates(
        topics: List<String>,
        players: Set<String>,
    ): List<Candidate> {
        val seenBy = SeenQuestions.playerId.count()
        val lastSeen = SeenQuestions.lastSeenAt.max()
        val joined =
            Questions.join(
                SeenQuestions,
                JoinType.LEFT,
                onColumn = Questions.id,
                otherColumn = SeenQuestions.questionId,
                additionalConstraint = { SeenQuestions.playerId inList players.ifEmpty { setOf("") } },
            )
        val inTopics: Op<Boolean> =
            if (topics.isEmpty()) {
                Op.TRUE
            } else {
                exists(
                    QuestionTopics
                        .select(QuestionTopics.questionId)
                        .where {
                            (QuestionTopics.questionId eq Questions.id) and (QuestionTopics.topicId inList topics)
                        },
                )
            }
        return joined
            .select(Questions.id, Questions.difficulty, seenBy, lastSeen)
            .where { (Questions.status eq QuestionStatus.APPROVED) and inTopics }
            .groupBy(Questions.id, Questions.difficulty)
            .map { row -> Candidate(row[Questions.id], row[Questions.difficulty], row[seenBy], row[lastSeen]) }
    }

    /** The [count] seen least, shuffled first so that ties fall at random. */
    private fun leastSeen(
        candidates: List<Candidate>,
        count: Int,
    ): List<Candidate> =
        candidates
            .shuffled(random)
            .sortedWith(compareBy<Candidate> { it.seenBy }.thenBy { it.lastSeen ?: Long.MIN_VALUE })
            .take(count)

    /** The questions [ids], in that order, whole: a question stored wrong is left out rather than asked. */
    private fun load(ids: List<String>): List<GameQuestion> {
        if (ids.isEmpty()) return emptyList()
        val rows =
            Questions
                .select(
                    Questions.columns,
                ).where { Questions.id inList ids }
                .associateBy { it[Questions.id] }
        val options =
            QuestionOptions
                .select(QuestionOptions.questionId, QuestionOptions.slot, QuestionOptions.text)
                .where { QuestionOptions.questionId inList ids }
                .orderBy(QuestionOptions.slot)
                .groupBy({ it[QuestionOptions.questionId] }, { it[QuestionOptions.slot] to it[QuestionOptions.text] })
        val topics =
            QuestionTopics
                .join(Topics, JoinType.INNER, QuestionTopics.topicId, Topics.id)
                .select(QuestionTopics.questionId, QuestionTopics.topicId)
                .where { QuestionTopics.questionId inList ids }
                .orderBy(Topics.createdAt to SortOrder.ASC, Topics.id to SortOrder.ASC)
                .groupBy({ it[QuestionTopics.questionId] }, { it[QuestionTopics.topicId] })
        return ids.mapNotNull { id ->
            val row = rows[id] ?: return@mapNotNull null
            val slots = options[id].orEmpty()
            val correct = slots.indexOfFirst { (slot, _) -> slot == row[Questions.correctSlot] }
            val topic = topics[id]?.firstOrNull() ?: return@mapNotNull null
            runCatching {
                GameQuestion(
                    questionId = id,
                    text = row[Questions.text],
                    options = slots.map { it.second },
                    correct = correct,
                    topicId = topic,
                    kind = row[Questions.kind],
                    difficulty = row[Questions.difficulty],
                    explanation = row[Questions.explanation],
                )
            }.getOrNull()
        }
    }

    private fun soloBest(playerId: String): Int? =
        Profiles
            .select(Profiles.soloBestScore)
            .where { Profiles.playerId eq playerId }
            .firstOrNull()
            ?.get(Profiles.soloBestScore)

    private fun rampOrder(difficulty: Difficulty): Int =
        when (difficulty) {
            Difficulty.EASY -> 0
            Difficulty.MEDIUM, Difficulty.UNKNOWN -> 1
            Difficulty.HARD -> 2
        }
}
